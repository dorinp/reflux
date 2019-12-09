import reflux.Csv
import org.scalatest.{FunSuite, Matchers}

class CsvSpec extends FunSuite with Matchers {
  test("split row into fields") {
    Csv.split("") shouldBe Array("")
    Csv.split("a,b") shouldBe Array("a", "b")
    Csv.split("""asd,"b,c",d""") shouldBe Array("asd", "b,c", "d")
  }

}
