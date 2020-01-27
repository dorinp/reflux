package reflux

import java.time.Instant

import org.scalatest.matchers.should.Matchers

class MeasurementSpec extends org.scalatest.FunSuite with Matchers {

  test("create measurement") {
    val t = Some(Instant.ofEpochMilli(0))
    Measurement
      .create(t, Field("bigdecimal", BigDecimal(0)), Tag("int", 1), Field("string", "string")) shouldBe Measurement(
        values = Seq(
          "bigdecimal" -> "0",
          "string" -> "string"
        ),
        tags = Seq("int" -> "1"),
        time = t
      )
  }
}