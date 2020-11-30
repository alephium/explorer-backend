import Dependencies._
import sbtrelease.ReleaseStateTransformations._

Global / cancelable := true // Allow cancellation of forked task without killing SBT

val releaseSettings =
  releaseProcess := Seq[ReleaseStep](
    checkSnapshotDependencies,
    inquireVersions,
    releaseStepCommandAndRemaining("clean"),
    releaseStepCommandAndRemaining("test"),
    setReleaseVersion,
    commitReleaseVersion,
    tagRelease,
    setNextVersion,
    commitNextVersion,
    pushChanges
  )

lazy val root = (project in file("."))
  .settings(releaseSettings: _*)
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
    libraryDependencies ++= Seq(
      alephiumUtil,
      alephiumProtocol,
      alephiumApi,
      alephiumCrypto,
      tapirCore,
      tapirCirce,
      tapirAkka,
      tapirOpenapi,
      tapiOpenapiCirce,
      tapirSwaggerUi,
      circeCore,
      circeGeneric,
      akkaHttpCirce,
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
    )
  )
  .enablePlugins(JavaAppPackaging)

val wartsCompileExcludes = Seq(
  Wart.Any,
  Wart.ImplicitParameter,
  Wart.StringPlusAny,
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
