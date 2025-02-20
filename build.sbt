// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "1.0" // your current series x.y

ThisBuild / organization := "io.github.arturaz"
ThisBuild / organizationName := "Artūras Šlajus"
ThisBuild / startYear := Some(2025)
ThisBuild / licenses := Seq(License.Apache2)
ThisBuild / developers := List(
  // your GitHub handle and name
  tlGitHubDev("arturaz", "Artūras Šlajus")
)

ThisBuild / sonatypeCredentialHost := xerial.sbt.Sonatype.sonatype01

// publish website from this branch
ThisBuild / tlSitePublishBranch := Some("main")

// Disable the checks, I don't want to deal with them right now.
ThisBuild / tlCiHeaderCheck := false

val Scala213 = "2.13.16"
ThisBuild / crossScalaVersions := Seq(Scala213, "3.3.5")
ThisBuild / scalaVersion := Scala213 // the default Scala

lazy val root = tlCrossRootProject.aggregate(core)

lazy val core = crossProject(
  JVMPlatform,
  JSPlatform
  // Seems we can't build native for now, as cats-core is only available for scala-native 0.5, but cats-effect is only
  // available for scala-native 0.4 for now.
//  NativePlatform
)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "cats-effect-resource-shared-memoized",
    libraryDependencies ++= Seq(
      "org.typelevel" %%% "cats-core" % "2.13.0",
      "org.typelevel" %%% "cats-effect" % "3.5.7",
      "org.scalameta" %%% "munit" % "1.1.0" % Test,
      "org.typelevel" %%% "munit-cats-effect" % "2.0.0" % Test
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(core.jvm)
  .enablePlugins(TypelevelSitePlugin)
