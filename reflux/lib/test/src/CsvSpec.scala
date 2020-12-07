import reflux.Csv
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class CsvSpec extends AnyFunSuite with Matchers {
  test("split row into fields") {
    Csv.split("") shouldBe Array("")
    Csv.split("a,b") shouldBe Array("a", "b")
    Csv.split("""asd,"b,c",d""") shouldBe Array("asd", "b,c", "d")
    Csv.split(""""a,b"""") shouldBe Array("a,b")
  }
}
