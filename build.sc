import mill._
import scalalib._
import publish._

// mill mill.scalalib.GenIdea/idea
object reflux extends Cross[RefluxModule]("2.13.6")

class RefluxModule(val crossScalaVersion: String) extends CrossScalaModule {

  object lib extends CommonModule with PublishModule {
    def pomSettings = PomSettings(
      description = "Streaming InfluxDB client",
      organization = "com.github.dorinp.reflux",
      url = "https://github.com/dorinp/reflux",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("dorinp", "reflux"),
      developers = Seq.empty
    )

    def artifactName = "reflux"

    def publishVersion = "0.0.17"
    val http4sVersion = "0.22.6"

    def ivyDeps = Agg(
      ivy"org.http4s::http4s-blaze-client:$http4sVersion",
      ivy"org.http4s::http4s-client:$http4sVersion",
    )

    object test extends Tests with ScalaTests

  }

  object generic extends CommonModule with PublishModule {
    def moduleDeps = Seq(lib)
    def artifactName = "reflux-generic"
    def publishVersion = lib.publishVersion
    def pomSettings: T[PomSettings] = lib.pomSettings
    def ivyDeps = Agg(ivy"com.chuusai::shapeless:2.3.3")
    object test extends Tests with ScalaTests
  }

  trait CommonModule extends ScalaModule {
    def scalaVersion = crossScalaVersion
    override def scalacOptions = Seq("-feature", "-deprecation")
  }

  trait ScalaTests extends mill.scalalib.TestModule.ScalaTest {
    def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.9",
      ivy"junit:junit:4.13.2",
      ivy"com.github.tomakehurst:wiremock-jre8:2.28.1",
      ivy"org.slf4j:slf4j-simple:1.7.25",
    )
  }

}