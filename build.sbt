import Dependencies._

Global / cancelable := true // Allow cancellation of forked task without killing SBT

lazy val root = (project in file("."))
  .settings(
    organization := "org.alephium",
    version := "0.1.0-SNAPSHOT",
    scalaVersion := "2.13.2",
    scalacOptions ++= Seq(
      "-deprecation",
      "-encoding",
      "utf-8",
      "-explaintypes",
      "-feature",
      "-unchecked",
      "-Xfatal-warnings",
      "-Xlint:adapted-args",
      "-Xlint:constant",
      "-Xlint:delayedinit-select",
      "-Xlint:doc-detached",
      "-Xlint:inaccessible",
      "-Xlint:infer-any",
      "-Xlint:missing-interpolator",
      "-Xlint:nullary-override",
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
    libraryDependencies ++= Seq(
      alephiumUtil % "test" classifier "tests",
      alephiumRpc,
      tapirCore,
      tapirCirce,
      tapirAkka,
      tapirOpenapi,
      tapiOpenapiCirce,
      circeCore,
      circeGeneric,
      akkaHttpCirce,
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
    )
  )

val wartsCompileExcludes = Seq(
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.StringPlusAny,
  Wart.Nothing
)
val wartsTestExcludes = wartsCompileExcludes ++ Seq(
  Wart.PublicInference,
  Wart.OptionPartial,
  Wart.NonUnitStatements,
  Wart.TraversableOps,
  Wart.Equals
)
