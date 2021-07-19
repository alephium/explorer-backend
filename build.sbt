import Dependencies._

Global / cancelable := true // Allow cancellation of forked task without killing SBT

lazy val root = (project in file("."))
  .settings(
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
    scalastyleConfig in Test := baseDirectory.value / "scalastyle-test-config.xml",
    wartremoverErrors in (Compile, compile) := Warts.allBut(wartsCompileExcludes: _*),
    wartremoverErrors in (Test, compile) := Warts.allBut(wartsTestExcludes: _*),
    fork := true,
    mainClass in assembly := Some("org.alephium.explorer.Main"),
    assemblyJarName in assembly := s"explorer-backend-${version.value}.jar",
    test in assembly := {},
    libraryDependencies ++= Seq(
      alephiumUtil,
      alephiumProtocol,
      alephiumApi,
      alephiumCrypto,
      alephiumJson,
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
      h2
    ),
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
    buildInfoUsePackageAsPath := true
  )
  .enablePlugins(JavaAppPackaging, sbtdocker.DockerPlugin, BuildInfoPlugin)

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
