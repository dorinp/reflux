package reflux

import cats.effect.{ContextShift, IO, Timer}
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, any, postRequestedFor, urlPathMatching}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.http4s._
import org.http4s.blaze.client.BlazeClientBuilder
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.Instant

class GenericSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {
  private val stubs = new FakeInflux()
  val influx = stubs.client

  test("basic strict query") {
    stubs.stub("city,temperature", "london,11", "paris,14")
    val rows = influx.asVector[CsvRow]("select * from weather").unsafeRunSync()
    rows.map(_.getString("city")) shouldBe Vector("london", "paris")
  }

  test("manual instance") {
    stubs.stub("city,temperature", "london,11", "paris,14")
    case class Weather(city: String, temperature: Int)
    implicit val r = new Read[Weather] {override def read(row: CsvRow): Weather = Weather(row.getString("city"), row.getString("temperature").toInt)}

    val rows = influx.asVector[Weather]("select * from weather").unsafeRunSync()
    rows shouldBe Vector(Weather("london", 11), Weather("paris", 14))
  }

  test("auto derived instance") {
    stubs.stub("temps,tags,time,city,temperature", "temps,,1571002272000,london,11", "temps,,1571002272000,paris,14")
    case class Weather(city: String, temperature: Int)
    import _root_.reflux.generic.auto._
    val rows = influx.asVector[Weather]("select * from weather").unsafeRunSync()
    rows shouldBe Vector(Weather("london", 11), Weather("paris", 14))
  }

  test("Measurement instance") {
    stubs.stub("temps,tags,time,city,temperature", "temps,,1571002272000,london,11", "temps,,1571002272000,paris,14")
    influx.stream[Measurement]("select * from weather").compile.toVector.unsafeRunSync().length shouldBe 2
  }

  test("writing") {
    influx.write("temperature", Measurement(Seq("temperature" -> "11"), Seq("city" -> "london"), time = Some(Instant.EPOCH))).unsafeRunSync()
    stubs.verifyDataPosted("temperature,city=london temperature=11 0\n")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    stubs.reset()
  }
}

class FakeInflux {
  import scala.concurrent.ExecutionContext.Implicits.global

  private val wiremock = new WireMockRule(WireMockConfiguration.options().dynamicPort())
  wiremock.start()

  private implicit val shift: ContextShift[IO] = IO.contextShift(global)
  private implicit val timer: Timer[IO] = IO.timer(global)

  private val http = BlazeClientBuilder[IO](global).resource.allocated.unsafeRunSync()._1
  val client = new InfluxClient[IO](http, Uri.unsafeFromString(s"http://localhost:${wiremock.port()}"))

  def reset() = {
    wiremock.resetAll()
    wiremock.stubFor(any(urlPathMatching("/write")).willReturn(aResponse().withStatus(204)))
  }

  def verifyDataPosted(expected: String): Unit = wiremock.verify(postRequestedFor(urlPathMatching("/write*")).withRequestBody(WireMock.containing(expected)))

  def stub(header: String, data: String*) = {
    wiremock.stubFor(any(urlPathMatching("/query")).willReturn(
      aResponse().withStatus(200).withBody((header +: data).mkString("\n"))
    ))

  }
}