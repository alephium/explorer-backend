import Dependencies._

Global / cancelable := true // Allow cancellation of forked task without killing SBT

def mainProject(id: String): Project = {
  Project(id, file(id))
    .settings(commonSettings: _*)
    .settings(
      name := s"explorer-backend-$id",
      scalastyleConfig in Compile := root.base / "scalastyle-config.xml",
      scalastyleConfig in Test := root.base / "scalastyle-test-config.xml"
    )
    .enablePlugins(JavaAppPackaging, sbtdocker.DockerPlugin, BuildInfoPlugin)
}

val commonSettings = Seq(
  name := "explorer-backend",
  organization := "org.alephium",
  scalaVersion := "2.13.3",
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
  ),
  Test / envVars += "ALEPHIUM_ENV" -> "test",
  wartremoverErrors in (Compile, compile) := Warts.allBut(wartsCompileExcludes: _*),
  wartremoverErrors in (Test, compile) := Warts.allBut(wartsTestExcludes: _*),
  fork := true
)

lazy val root = (project in file("."))
  .settings(commonSettings: _*)
  .aggregate(app, tools)

lazy val app = mainProject("app")
  .settings(
    libraryDependencies ++= Seq(
      alephiumUtil,
      alephiumProtocol,
      alephiumApi,
      alephiumCrypto,
      alephiumJson,
      alephiumHttp,
      tapirCore,
      tapirAkka,
      tapirOpenapi,
      tapirOpenapiModel,
      tapirSwaggerUi,
      tapirClient,
      sttpAkkaBackend,
      akkaHttpJson,
      upickle,
      akkaHttpCors,
      scalaLogging,
      logback,
      akkaTest,
      akkaHttptest,
      akkaStream,
      akkaStreamTest,
      scalatest,
      scalatestplus,
      scalacheck,
      slick,
      slickHikaricp,
      postgresql,
      h2,
      prometheusSimpleClient,
      prometheusSimpleClientHotspot,
      tapirPrometheusMetrics,
      micrometerCore,
      micrometerPrometheus,
    ))
  .settings(
    mainClass in assembly := Some("org.alephium.explorer.Main"),
    assemblyJarName in assembly := s"explorer-backend-${version.value}.jar",
    test in assembly := {},
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
      val baseImageName = "alephium/explorer-backend"
      val versionTag    = version.value.replace('+', '_')
      Seq(
        ImageName(baseImageName + ":" + versionTag),
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
    assemblyMergeStrategy in assembly := {
      case PathList("META-INF", "io.netty.versions.properties", xs @ _*) =>
        MergeStrategy.first
      case "module-info.class" =>
        MergeStrategy.discard
      case other => (assemblyMergeStrategy in assembly).value(other)
    }
  )

lazy val tools = mainProject("tools")
  .dependsOn(app)
  .settings(libraryDependencies ++= Seq(alephiumProtocol, alephiumApi, alephiumCrypto, logback))

val wartsCompileExcludes = Seq(
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.StringPlusAny,
  Wart.Null, // for upickle macroRW
  Wart.Equals, // for upickle macroRW
  Wart.ToString, // for upickle macroRW
  Wart.Var, // for upickle macroRW
  Wart.Nothing
)

val wartsTestExcludes = wartsCompileExcludes ++ Seq(
  Wart.PublicInference,
  Wart.OptionPartial,
  Wart.Overloading,
  Wart.NonUnitStatements,
  Wart.TraversableOps,
  Wart.Throw,
  Wart.Equals
)
