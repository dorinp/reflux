import mill._, scalalib._, publish._
import $ivy.`de.tototec::de.tobiasroeser.mill.publishM2:0.1.0`
import de.tobiasroeser.mill.publishM2._

// mill mill.scalalib.GenIdea/idea
object reflux extends Cross[RefluxModule]("2.12.10", "2.13.1")

class RefluxModule(val crossScalaVersion: String) extends CrossScalaModule {

  object lib extends CommonModule with PublishM2Module {
    def pomSettings = PomSettings(
      description = "Streaming InfluxDB client",
      organization = "com.github.dorinp.reflux",
      url = "https://github.com/dorinp/reflux",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("dorinp", "reflux"),
      developers = Seq.empty
    )

    def artifactName = "reflux"

    def publishVersion = "0.0.6"
    val http4sVersion = if (crossScalaVersion.startsWith("2.12")) "0.20.15" else "0.21.0-M6"

    def ivyDeps = Agg(
      ivy"org.http4s::http4s-blaze-client:$http4sVersion",
      ivy"org.http4s::http4s-client:$http4sVersion",
    )

    object test extends Tests with ScalaTest

  }

  object generic extends CommonModule with PublishM2Module {
    def moduleDeps = Seq(lib)

    def artifactName = "reflux-generic"

    def publishVersion = lib.publishVersion

    def pomSettings: T[PomSettings] = lib.pomSettings

    def ivyDeps = Agg(
      ivy"com.chuusai::shapeless:2.3.3",
    )

    object test extends Tests with ScalaTest

  }

  trait CommonModule extends ScalaModule {
    def scalaVersion = crossScalaVersion

    override def scalacOptions = Seq("-feature", "-deprecation")
  }

  trait ScalaTest extends mill.scalalib.TestModule {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.1.0",
      ivy"com.github.tomakehurst:wiremock:2.18.0",
      ivy"org.slf4j:slf4j-simple:1.7.25",
    )

    def testFrameworks = Seq("org.scalatest.tools.Framework")
  }

}