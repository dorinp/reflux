import mill._
import scalalib._
import publish._

// mill mill.scalalib.GenIdea/idea
object reflux extends ScalaModule {

  override def scalaVersion = "2.13.8"

  object lib extends CommonModule with PublishModule {
    def pomSettings = PomSettings(
      description = "Streaming InfluxDB client",
      organization = "com.github.dorinp.reflux",
      url = "https://github.com/dorinp/reflux",
      licenses = Seq(License.MIT),
      versionControl = VersionControl.github("dorinp", "reflux"),
      developers = Seq.empty
    )

    override def artifactName = "reflux"

    def publishVersion = "0.0.15"
    val http4sVersion = "0.23.11"

    override def ivyDeps = Agg(
      ivy"org.http4s::http4s-blaze-client:$http4sVersion",
      ivy"org.http4s::http4s-client:$http4sVersion",
    )

    object test extends Tests with ScalaTest

  }

  object generic extends CommonModule with PublishModule {
    override def moduleDeps = Seq(lib)
    override def artifactName = "reflux-generic"
    def publishVersion = lib.publishVersion
    def pomSettings: T[PomSettings] = lib.pomSettings
    override def ivyDeps = Agg(ivy"com.chuusai::shapeless:2.3.3")
    object test extends Tests with ScalaTest
  }

  trait CommonModule extends ScalaModule {
    def scalaVersion = reflux.scalaVersion
    override def scalacOptions = Seq("-feature", "-deprecation")
  }

  trait ScalaTest extends mill.scalalib.TestModule {
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.11",
      ivy"junit:junit:4.13.2",
      ivy"com.github.tomakehurst:wiremock-jre8:2.27.2",
      ivy"org.slf4j:slf4j-simple:1.7.36",
    )

    override def testFramework = "org.scalatest.tools.Framework"
  }
}