import Dependencies._

Global / cancelable := true // Allow cancellation of forked task without killing SBT

def mainProject(id: String): Project = {
  Project(id, file(id))
    .settings(commonSettings: _*)
    .settings(
      name := s"explorer-backend-$id",
      Compile / scalastyleConfig := root.base / "scalastyle-config.xml",
      Test / scalastyleConfig := root.base / "scalastyle-test-config.xml"
    )
    .enablePlugins(JavaAppPackaging, sbtdocker.DockerPlugin, ScalaUnidocPlugin)
}

/**
  * Finds the jar file for an external library.
  *
  * @param classPath The classpath to search
  * @param moduleId  Target external library to find within the classpath
  * @return          The jar file of external library or [[sys.error]] if not found.
  */
def findDependencyJar(classPath: Classpath, moduleId: ModuleID): File = {
  val jarFileOption =
    classPath.find { file =>
      file.get(moduleID.key).exists { module =>
        module.organization == moduleId.organization && module.name.startsWith(moduleId.name)
      }
    }

  jarFileOption match {
    case Some(jarFile) =>
      jarFile.data

    case None =>
      sys.error(
        s"Dependency not found: ${moduleId.organization}:${moduleId.name}:${moduleId.revision}")
  }
}

/** Scala-docs API Mapping for scala-library */
def scalaDocsAPIMapping(classPath: Classpath, scalaVersion: String): (sbt.File, sbt.URL) = {
  val scalaLibJar =
    findDependencyJar(
      classPath = classPath,
      moduleId  = "org.scala-lang" % "scala-library" % scalaVersion
    )

  val scalaDocsURL = url(s"http://www.scala-lang.org/api/$scalaVersion/")

  scalaLibJar -> scalaDocsURL
}

/** Scala-docs API Mapping for slick */
def slickScalaDocAPIMapping(classPath: Classpath, scalaVersion: String): (sbt.File, sbt.URL) = {
  val slickJar =
    findDependencyJar(
      classPath = classPath,
      moduleId  = slick
    )

  //fetch only the major and minor
  val scalaMajorMinor = scalaVersion.split("\\.").take(2).mkString(".")
  val slickDocsURL =
    url(
      s"https://www.javadoc.io/doc/com.typesafe.slick/slick_$scalaMajorMinor/${slick.revision}/index.html"
    )

  slickJar -> slickDocsURL
}

/** Java-docs API Mapping for Caffeine */
def caffeineJavaDocAPIMapping(classPath: Classpath): (sbt.File, sbt.URL) = {
  val slickJar =
    findDependencyJar(
      classPath = classPath,
      moduleId  = caffeine
    )

  //fetch only the major and minor
  val docsURL =
    url(
      s"https://www.javadoc.io/doc/com.github.ben-manes.caffeine/caffeine/${caffeine.revision}/com.github.benmanes.caffeine/"
    )

  slickJar -> docsURL
}

val inliningOptions =
  Seq(
    "-opt-warnings",
    "-opt:l:inline",
    "-opt-inline-from:org.alephium.explorer.**",
    //Uncomment to debug inlining
    /*
    "-Yopt-log-inline",
    "_"
   */
  )

val commonSettings = Seq(
  name := "explorer-backend",
  organization := "org.alephium",
  scalaVersion := "2.13.8",
  scalacOptions ++= Seq(
    "-deprecation",
    "-encoding",
    "utf-8",
    "-explaintypes",
    "-feature",
    "-unchecked",
    "-Xsource:3.1",
    "-Xfatal-warnings",
    "-Xlint:adapted-args",
    "-Xlint:constant",
    "-Xlint:delayedinit-select",
    "-Xlint:doc-detached",
    "-Xlint:inaccessible",
    "-Xlint:infer-any",
    "-Xlint:missing-interpolator",
    "-Xlint:nullary-unit",
    "-Xlint:option-implicit",
    "-Xlint:package-object-classes",
    "-Xlint:poly-implicit-overload",
    "-Xlint:private-shadow",
    "-Xlint:stars-align",
    "-Xlint:type-parameter-shadow",
    "-Xlint:nonlocal-return",
    "-Ywarn-dead-code",
    "-Ywarn-extra-implicit",
    "-Ywarn-numeric-widen",
    "-Ywarn-unused:implicits",
    "-Ywarn-unused:imports",
    "-Ywarn-unused:locals",
    "-Ywarn-unused:params",
    "-Ywarn-unused:patvars",
    "-Ywarn-unused:privates",
    "-Ywarn-value-discard"
  ) ++ inliningOptions,
  Test / envVars += "ALEPHIUM_ENV" -> "test",
  Compile / compile / wartremoverErrors := Warts.allBut(wartsCompileExcludes: _*),
  Test / compile / wartremoverErrors := Warts.allBut(wartsTestExcludes: _*),
  fork := true,
  apiMappings ++= {
    val scalaDocsMap =
      scalaDocsAPIMapping(
        classPath    = (Compile / fullClasspath).value,
        scalaVersion = scalaVersion.value
      )

    Map(scalaDocsMap)
  }
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(app, tools, benchmark)

lazy val app = mainProject("app")
  .enablePlugins(BuildInfoPlugin)
  .settings(libraryDependencies ++= Seq(
    alephiumUtil,
    alephiumProtocol,
    alephiumApi,
    alephiumCrypto,
    alephiumJson,
    rxJava,
    alephiumHttp,
    alephiumConf,
    tapirCore,
    tapirServer,
    tapirVertx,
    vertxRxJava,
    tapirOpenapi,
    tapirOpenapiModel,
    tapirSwaggerUi,
    tapirClient,
    sttpBackend,
    upickle,
    caffeine,
    ficus,
    scalaLogging,
    logback,
    akkaTest,
    scalatest,
    scalaMock,
    scalatestplus,
    scalacheck,
    slick,
    slickHikaricp,
    postgresql,
    prometheusSimpleClient,
    prometheusSimpleClientHotspot,
    tapirPrometheusMetrics,
    micrometerCore,
    micrometerPrometheus,
  ))
  .settings(
    assembly / mainClass := Some("org.alephium.explorer.Main"),
    assembly / assemblyJarName := s"explorer-backend-${version.value}.jar",
    assembly / test := {},
    docker / dockerfile := {
      val appSource = stage.value
      val appTarget = "/app"

      new Dockerfile {
        from("adoptopenjdk/openjdk11:jre")
        expose(9090)
        workDir(appTarget)
        entryPoint(s"$appTarget/bin/${executableScriptName.value}")
        copy(appSource, appTarget)
      }
    },
    docker / imageNames := {
      val baseImageName = "alephium/dev-explorer-backend"
      val versionTag    = version.value.replace('+', '_')
      Seq(
        ImageName(baseImageName + ":" + versionTag),
        ImageName(baseImageName + ":latest"),
      )
    },
    buildInfoKeys := Seq[BuildInfoKey](
      name,
      scalaVersion,
      sbtVersion,
      BuildInfoKey("commitId"       -> git.gitHeadCommit.value.getOrElse("missing-git-commit")),
      BuildInfoKey("branch"         -> git.gitCurrentBranch.value),
      BuildInfoKey("releaseVersion" -> version.value)
    ),
    buildInfoPackage := "org.alephium.explorer",
    buildInfoUsePackageAsPath := true,
    assembly / assemblyMergeStrategy := {
      case PathList("META-INF", "io.netty.versions.properties", xs @ _*) =>
        MergeStrategy.first
      case PathList("META-INF", "maven", "org.webjars", "swagger-ui", xs @ _*) =>
        MergeStrategy.first
      case x if x.endsWith("module-info.class") =>
        MergeStrategy.discard
      case other => (assembly / assemblyMergeStrategy).value(other)
    },
    apiMappings ++= {
      val classPath = (Compile / fullClasspath).value

      val slickAPIMapping =
        slickScalaDocAPIMapping(
          classPath    = classPath,
          scalaVersion = scalaVersion.value
        )

      val caffeineAPIMapping =
        caffeineJavaDocAPIMapping(classPath)

      Map(slickAPIMapping, caffeineAPIMapping)
    }
  )

lazy val tools = mainProject("tools")
  .dependsOn(app)
  .settings(libraryDependencies ++= Seq(alephiumProtocol, alephiumApi, alephiumCrypto, logback))

lazy val benchmark = mainProject("benchmark")
  .enablePlugins(JmhPlugin)
  .dependsOn(app % "test->test;compile->compile")
  .settings(
    libraryDependencies ++= Seq(
      scalaLogging,
      logback,
      scalatest,
      scalatestplus,
      scalacheck,
      slick,
      slickHikaricp,
      postgresql
    ),
    apiMappings ++= {
      val slickAPIMapping =
        slickScalaDocAPIMapping(
          classPath    = (Compile / fullClasspath).value,
          scalaVersion = scalaVersion.value
        )

      Map(slickAPIMapping)
    },
    Jmh / sourceDirectory := (Test / sourceDirectory).value,
    Jmh / classDirectory := (Test / classDirectory).value,
    Jmh / dependencyClasspath := (Test / dependencyClasspath).value,
    // rewire tasks, so that 'jmh:run' automatically invokes 'jmh:compile' (otherwise a clean 'jmh:run' would fail)
    Jmh / compile := (Jmh / compile).dependsOn(Test / compile).value,
    Jmh / run := (Jmh / run).dependsOn(Test / Keys.compile).evaluated,
  )

val wartsCompileExcludes = Seq(
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.StringPlusAny,
  Wart.Null, // for upickle macroRW
  Wart.Equals, // for upickle macroRW
  Wart.ToString, // for upickle macroRW
  Wart.Var, // for upickle macroRW
  Wart.Throw, // for upickle macroRW
  Wart.Nothing
)

val wartsTestExcludes = wartsCompileExcludes ++ Seq(
  Wart.PublicInference,
  Wart.OptionPartial,
  Wart.Overloading,
  Wart.NonUnitStatements,
  Wart.IterableOps,
  Wart.Throw,
  Wart.Equals,
  Wart.MutableDataStructures,
  Wart.Recursion,
  Wart.GlobalExecutionContext
)
