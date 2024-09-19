import cats.effect.unsafe.implicits.global
import org.scalatest.BeforeAndAfterEach
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import reflux.{Measurement, Read}

import java.time.Instant

class DerivationSpec extends AnyFunSuite with Matchers with BeforeAndAfterEach {
  private val stubs = new FakeInflux()
  val influx = stubs.client

  test("query tuples") {
    stubs.stub("temps,tags,time,city,temperature", "temps,,1571002272000,london,11", "temps,,1571002272000,paris,14")

    val rows = influx.asVector[(String, Int)]("select * from weather").unsafeRunSync()
    rows `shouldBe` Vector(("london", 11), ("paris", 14))
  }

  test("Measurement instance") {
    stubs.stub("temps,tags,time,city,temperature", "temps,,1571002272000,london,11", "temps,,1571002272000,paris,14")
    influx.stream[Measurement]("select * from weather").compile.toVector.unsafeRunSync().length shouldBe 2
  }

  test("writing") {
    influx.write("temperature", Measurement(Seq("temperature" -> "11"), Seq("city" -> "london"), time = Some(Instant.EPOCH))).unsafeRunSync()
    stubs.verifyDataPosted("temperature,city=london temperature=11 0\n")
  }

  test("writing without tags") {
    influx.write("temperature", Measurement(Seq("temperature" -> "11"), tags = Nil, time = Some(Instant.EPOCH))).unsafeRunSync()
    stubs.verifyDataPosted("temperature temperature=11 0\n")
  }

  override protected def beforeEach(): Unit = {
    super.beforeEach()
    stubs.reset()
  }
}
