import mill._
import scalalib._
import publish._

object reflux extends ScalaModule {

  def scalaVersion = "3.3.6"
  val http4sVersion = "0.23.30"

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
    override def publishVersion = "0.3.7"

    override def ivyDeps = Agg(
      ivy"org.http4s::http4s-client:$http4sVersion",
    )

    object test extends ScalaTest {
      def moduleDeps = Seq(lib)
    }
  }

  trait CommonModule extends ScalaModule {
    def scalaVersion = reflux.scalaVersion
    override def scalacOptions = Seq("-feature", "-deprecation", "-Wunused:privates", "-Wunused:locals", "-Wunused:params")
  }

  trait ScalaTest extends ScalaTests {
    override def ivyDeps = Agg(
      ivy"org.scalatest::scalatest:3.2.19",
      ivy"junit:junit:4.13.2",
      ivy"com.github.tomakehurst:wiremock-jre8:3.0.1",
      ivy"org.slf4j:slf4j-simple:1.7.36",
      ivy"org.http4s::http4s-ember-client:$http4sVersion",
    )

    override def testFramework = "org.scalatest.tools.Framework"
  }
}