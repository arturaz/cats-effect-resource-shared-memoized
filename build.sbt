import com.typesafe.tools.mima.core._

// https://typelevel.org/sbt-typelevel/faq.html#what-is-a-base-version-anyway
ThisBuild / tlBaseVersion := "0.1" // your current series x.y

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

val MUnit = Def.setting(
  Seq(
    // MUnit supports only scala-native 0.5 from version 1.0.1 and upwards.
    "org.scalameta" %%% "munit" % "1.0.0",
    "org.typelevel" %%% "munit-cats-effect" % "2.0.0"
  )
)

lazy val core = crossProject(
  JVMPlatform,
  JSPlatform,
  NativePlatform
)
  .crossType(CrossType.Pure)
  .in(file("core"))
  .settings(
    name := "cats-effect-resource-shared-memoized",
    libraryDependencies ++= Seq(
      // Not the latest version, as we are targeting scala-native 0.4 for now and later versions of cats-core target
      // 0.5. We will update once cats-effect has a build for scala-native 0.5.
      "org.typelevel" %%% "cats-core" % "2.11.0",
      "org.typelevel" %%% "cats-effect" % "3.5.7"
    ) ++ MUnit.value.map(_ % Test),
    // Do not fail on no previous artifacts, as we don't have scala-native published yet.
    mimaPreviousArtifacts := {
      if (crossProjectPlatform.value == NativePlatform) Set.empty else mimaPreviousArtifacts.value
    },
    mimaBinaryIssueFilters ++= Seq(
      // This class is private, but somehow MIMA still checks it :/
      ProblemFilters.exclude[Problem]("cats.effect.resource_shared_memoized.ResourceSharedMemoized#Allocated"),
      ProblemFilters.exclude[Problem]("cats.effect.resource_shared_memoized.ResourceSharedMemoized#Allocated.*")
    )
  )

lazy val docs = project
  .in(file("site"))
  .dependsOn(core.jvm)
  .enablePlugins(TypelevelSitePlugin)
  .settings(
    libraryDependencies ++= MUnit.value
  )
