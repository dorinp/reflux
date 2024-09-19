package reflux

import cats.Eq
import fs2.{Pipe, text}

import java.time.Instant
import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer
import scala.compiletime.{erasedValue, error, summonInline}
import scala.deriving.*

class CsvHeader(headerLine: String) {
  private val cols: Array[String] = Csv.split(headerLine)
  private val indices: Map[String, Int] = cols.zipWithIndex.toMap

  def getString(data: Array[String], column: String): String = data(indices(column))

  def columns: Array[String] = cols.drop(3)
  def columnCount: Int = indices.size
  override def toString: String = headerLine
}

final case class CsvRow(header: CsvHeader, data: Array[String], cursor: Int) {
  def columns: Array[String] = header.columns
  def tuples: Array[(String, String)] = columns.zip(data.drop(cursor))
  def time: Instant = Instant.ofEpochMilli(getString("time").toLong)

  def getTag(name: String): Option[String] = tags.find(_._1 == name).map(_._2)

  def get[A](field: String)(implicit read: Read[A]): A = read.read(this)

  def getOption(field: String): Option[String] = { val a = header.getString(data, field); if(a.isEmpty) None else Some(a) }
  def getString(field: String): String = header.getString(data, field)
  inline def getString(index: Int): String = data(index)
  inline def getAtCursor: String = getString(cursor)

  def tags: Array[(String, String)] = getString("tags").split(",").flatMap(a => {
    val arr = a.split("="); if (arr.length > 1) Some(arr(0) -> arr(1)) else None
  })

  def toMeasurement: Measurement = Measurement(ArraySeq.unsafeWrapArray(header.columns).zip(data.drop(3)), ArraySeq.unsafeWrapArray(tags), time = Some(time))
  override def toString: String = data.mkString("[",",", "]")
}



case class Measurement(values: Seq[(String, String)], tags: Seq[(String, String)] = Seq.empty, time: Option[Instant] = None)

object Measurement {
  def create[A, B](values: Seq[(String, A)], tags: Seq[(String, B)], time: Option[Instant])(implicit writeA: Write[A], writeB: Write[B]): Measurement =
    new Measurement(values.map{ case (n,v) => (n, writeA.write(v))}, tags.map{ case (n,v) => (n, writeB.write(v))}, time)

  def create(time: Option[Instant], columns: Column*): Measurement = {
    val (fields, tags) = columns.partition { case _: Field => true; case _ => false }
    new Measurement(fields.map(f => f.name -> f.value), tags.map(t => t.name -> t.value), time)
  }
}

sealed trait Column {
  def name: String
  def value: String
}

case class Tag(name: String, value: String) extends Column
case class Field(name: String, value: String) extends Column

object Field {
  def apply[A](name: String, value: A)(implicit write: Write[A]): Field = Field(name, write.write(value))
}

object Tag {
  def apply[A](name: String, value: A)(implicit write: Write[A]): Tag = Tag(name, write.write(value))
}

trait ToMeasurement[A] {
  def measurementName: String
  def write(a: A): Measurement
}

object ToMeasurement {
  def instance[A](name: String, f: A => Measurement): ToMeasurement[A] = new ToMeasurement[A] {
    override def measurementName: String = name
    override def write(a: A): Measurement = f(a)
  }
}

object TimeColumn {
  implicit val eq: Eq[TimeColumn] = Eq.fromUniversalEquals
}
case class TimeColumn(time: Instant) extends AnyVal

trait Read[A] {
  def read(row: CsvRow): A
}

object Read {
  def apply[A](implicit r: Read[A]): Read[A] = r
  inline def instance[A](f: CsvRow => A): Read[A] = (row: CsvRow) => f(row)
  implicit val rowRead: Read[CsvRow] = instance(identity)
  implicit val measurementRead: Read[Measurement] = instance(_.toMeasurement)
  implicit val stringRead: Read[String] = Read.instance(_.getAtCursor)
  implicit val intRead: Read[Int] = Read.instance(_.getAtCursor.toInt)
  implicit val longRead: Read[Long] = Read.instance(_.getAtCursor.toLong)
  implicit val boolRead: Read[Boolean] = Read.instance(x => !(x.getAtCursor == "0"))
  implicit val doubleRead: Read[Double] = Read.instance(_.getAtCursor.toDouble)
  implicit val floatRead: Read[Float] = Read.instance(_.getAtCursor.toFloat)
  implicit val bigDRead: Read[BigDecimal] = Read.instance(r => BigDecimal(r.getAtCursor))
  implicit val bigIRead: Read[BigInt] = Read.instance(r => BigInt(r.getAtCursor))
  implicit val timeRead: Read[TimeColumn] = Read.instance(r => TimeColumn(r.time))
  implicit val instantRead: Read[Instant] = Read.instance(r => Instant.ofEpochMilli(r.getString("time").toLong))

  implicit def optionRead[A](implicit reader: Read[A]): Read[Option[A]] = Read.instance(row => if (row.getAtCursor.isEmpty) None else Some(reader.read(row)))

  implicit def tuple2Read[A: Read, B: Read]: Read[(A, B)] = instance { row =>
    (summon[Read[A]].read(row), summon[Read[B]].read(row.copy(cursor = row.cursor + 1)))
  }

  implicit def tuple3Read[A: Read, B: Read, C: Read]: Read[(A, B, C)] = instance { row =>
    (summon[Read[A]].read(row),
      summon[Read[B]].read(row.copy(cursor = row.cursor + 1)),
      summon[Read[C]].read(row.copy(cursor = row.cursor + 2)),
    )
  }

  implicit def tuple4Read[A: Read, B: Read, C: Read, D: Read]: Read[(A, B, C, D)] = instance { row =>
    (summon[Read[A]].read(row),
      summon[Read[B]].read(row.copy(cursor = row.cursor + 1)),
      summon[Read[C]].read(row.copy(cursor = row.cursor + 2)),
      summon[Read[D]].read(row.copy(cursor = row.cursor + 3)),
    )
  }

  implicit def tuple5Read[A: Read, B: Read, C: Read, D: Read, E: Read]: Read[(A, B, C, D, E)] = instance { row =>
    (summon[Read[A]].read(row),
      summon[Read[B]].read(row.copy(cursor = row.cursor + 1)),
      summon[Read[C]].read(row.copy(cursor = row.cursor + 2)),
      summon[Read[D]].read(row.copy(cursor = row.cursor + 3)),
      summon[Read[E]].read(row.copy(cursor = row.cursor + 4)),
    )
  }

  implicit def tuple6Read[A: Read, B: Read, C: Read, D: Read, E: Read, F: Read]: Read[(A, B, C, D, E, F)] = instance { row =>
    (summon[Read[A]].read(row),
      summon[Read[B]].read(row.copy(cursor = row.cursor + 1)),
      summon[Read[C]].read(row.copy(cursor = row.cursor + 2)),
      summon[Read[D]].read(row.copy(cursor = row.cursor + 3)),
      summon[Read[E]].read(row.copy(cursor = row.cursor + 4)),
      summon[Read[F]].read(row.copy(cursor = row.cursor + 5)),
    )
  }

  inline def derived[A](implicit m: Mirror.Of[A]): Read[A] = {
    lazy val elemInstances = summonInstances[A, m.MirroredElemTypes]
    inline m match
      case s: Mirror.SumOf[A] => error("only Product types are supported")
      case p: Mirror.ProductOf[A] => readProduct(p, elemInstances)
  }

  def readProduct[A](m: Mirror.ProductOf[A], elems: => List[Read[?]]): Read[A] =
    new Read[A] {
      override def read(row: CsvRow): A = {
        val p = elems.zipWithIndex.map((a, i) => a.read(row.copy(cursor = row.cursor + i))).toArray
        val t = Tuple.fromArray(p)
        m.fromProduct(t)
      }
    }

  inline def summonInstances[T, Elems <: Tuple]: List[Read[?]] =
    inline erasedValue[Elems] match
      case _: (elem *: elems) => deriveOrSummon[T, elem] :: summonInstances[T, elems]
      case _: EmptyTuple => Nil

  inline def deriveOrSummon[T, Elem]: Read[Elem] =
    inline erasedValue[Elem] match
      case _: T => deriveRec[T, Elem]
      case _ => summonInline[Read[Elem]]

  inline def deriveRec[T, Elem]: Read[Elem] =
    inline erasedValue[T] match
      case _: Elem => error("infinite recursive derivation")
      case _ => Read.derived[Elem](using summonInline[Mirror.Of[Elem]])


}

trait Write[A] {
  def write(a: A): String
}

object Write {
  private def instance[A](f: A => String) = new Write[A] {
    override def write(a: A): String = f(a)
  }

  implicit val writeStr    : Write[String] = instance(identity)
  implicit val writeBigD   : Write[BigDecimal] = instance(_.toString)
  implicit val writeInt    : Write[Int] = instance(_.toString)
  implicit val writeLong   : Write[Long] = instance(_.toString)
  implicit val writeDouble : Write[Double] = instance(_.toString)
}

object Csv {
  def rows[F[_], O](dataIndex: Int): Pipe[F, Byte, CsvRow] = in => {
    in.through(text.utf8.decode)
      .through(text.lines)
      .filter(_.nonEmpty)
      .scan(null: CsvRow) {
        case (acc, line) => if (acc == null) CsvRow(new CsvHeader(line), null, dataIndex) else CsvRow(acc.header, Csv.split(line), dataIndex)
      }
    }.drop(2)

  // could be done with a regex, but this is 3x faster
  def split(s: String): Array[String] = {
    val words: ArrayBuffer[String] = ArrayBuffer[String]()
    var inQuote = false
    var start = 0
    var i = 0
    var x = 0
    while (i < s.length) {
      if (s.charAt(i) == ',' && !inQuote) {
        words += s.substring(start, i - x)
        x = 0
        start = i + 1
      } else if (s.charAt(i) == '"') {
        if (!inQuote) {start = start + 1; x = 1; }
        inQuote = !inQuote
      }
      i += 1
    }
    words += s.substring(start, i - x)
    words.toArray[String]
  }
}
