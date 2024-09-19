import cats.effect.IO
import cats.effect.unsafe.implicits.global
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.client.WireMock.{aResponse, any, postRequestedFor, urlPathMatching}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import com.github.tomakehurst.wiremock.junit.WireMockRule
import org.http4s.Uri
import org.http4s.ember.client.EmberClientBuilder
import reflux.InfluxClient

class FakeInflux {

  private val wiremock = new WireMockRule(WireMockConfiguration.options().dynamicPort())
  wiremock.start()

  private val http = EmberClientBuilder.default[IO].build.allocated.unsafeRunSync()._1
  val client       = new InfluxClient[IO](http, Uri.unsafeFromString(s"http://localhost:${wiremock.port()}"))

  def reset() = {
    wiremock.resetAll()
    wiremock.stubFor(any(urlPathMatching("/write")).willReturn(aResponse().withStatus(204)))
  }

  def verifyDataPosted(expected: String): Unit =
    wiremock.verify(postRequestedFor(urlPathMatching("/write*")).withRequestBody(WireMock.containing(expected)))

  def stub(header: String, data: String*) = {
    wiremock.stubFor(
      any(urlPathMatching("/query")).willReturn(
        aResponse().withStatus(200).withBody((header +: data).mkString("\n"))
      ))

  }
}
