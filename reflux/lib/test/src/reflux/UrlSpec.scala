package reflux

import cats.effect.IO
import org.http4s.implicits.uri
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class UrlSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {
  test("simple") {
    Reflux.client[IO](null, "https://localhost:8086/database").serverUrl.renderString shouldBe "https://localhost:8086"
  }

  test("credentials") {
    Reflux.client[IO](null, "https://user:pass@localhost:8086/database").queryUri shouldBe uri"https://localhost:8086/query?db=database&epoch=ms"
  }

  test("handle non-root url") {
    Reflux.client[IO](null, "https://localhost:8086/some/path/database").queryUri.renderString shouldBe "https://localhost:8086/some/path/query?db=database&epoch=ms"
  }

  test("handle non-root url ands credentials") {
    Reflux.client[IO](null, "https://user:pass@localhost:8086/some/path/database").queryUri.renderString shouldBe "https://localhost:8086/some/path/query?db=database&epoch=ms"
  }

}
