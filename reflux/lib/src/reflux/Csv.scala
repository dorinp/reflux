package reflux

import java.time.Instant
import cats.Eq
import fs2.{Pipe, text}

import scala.collection.immutable.ArraySeq
import scala.collection.mutable.ArrayBuffer

class CsvHeader(headerLine: String) {
  private val cols: Array[String] = Csv.split(headerLine)
  private val indices: Map[String, Int] = cols.zipWithIndex.toMap

  def getString(data: Array[String], column: String): String = data(indices(column))

  def columns = cols.drop(3)
  def columnCount: Int = indices.size
  override def toString: String = headerLine
}

case class CsvRow(header: CsvHeader, data: Array[String], cursor: Int) {
  def columns: Array[String] = header.columns
  def tuples: Array[(String, String)] = columns.zip(data.drop(cursor))
  def time: Instant = Instant.ofEpochMilli(getString("time").toLong)

  def getTag(name: String): Option[String] = tags.find(_._1 == name).map(_._2)

  def get[A](field: String)(implicit read: Read[A]): A = read.read(this)

  def getOption(field: String): Option[String] = { val a = header.getString(data, field); if(a.isEmpty) None else Some(a) }
  def getString(field: String): String = header.getString(data, field)
  def getString(index: Int): String = data(index)
  def getAtCursor: String = data(cursor)

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
  def instance[A](f: CsvRow => A): Read[A] = (row: CsvRow) => f(row)
  implicit val rowRead: Read[CsvRow] = instance(identity)
  implicit val measurementRead: Read[Measurement] = instance(_.toMeasurement)
  implicit val stringRead: Read[String] = Read.instance(_.getAtCursor)
  implicit val intRead: Read[Int] = Read.instance(_.getAtCursor.toInt)
  implicit val longRead: Read[Long] = Read.instance(_.getAtCursor.toLong)
  implicit val boolRead: Read[Boolean] = Read.instance(x => !(x.getAtCursor == "0"))
  implicit val doubleRead: Read[Double] = Read.instance(_.getAtCursor.toDouble)
  implicit val bigDRead: Read[BigDecimal] = Read.instance(r => BigDecimal(r.getAtCursor))
  implicit val timeRead: Read[TimeColumn] = Read.instance(r => TimeColumn(r.time))
  implicit val instantRead: Read[Instant] = Read.instance(r => Instant.ofEpochMilli(r.getString("time").toLong))
  implicit def optionRead[A](implicit reader: Read[A]): Read[Option[A]] = Read.instance(row => if (row.getAtCursor.isEmpty) None else Some(reader.read(row)))
}

trait Write[A] {
  def write(a: A): String
}

object Write {
  def instance[A](f: A => String) = new Write[A] {
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
    in.through(text.utf8Decode)
      .through(text.lines)
      .filter(!_.isEmpty)
      .scan(null: CsvRow) {
        case (acc, line) => if (acc == null) CsvRow(new CsvHeader(line), null, dataIndex) else CsvRow(acc.header, Csv.split(line), dataIndex)
      }
    }.drop(2)

  // could be done with a regex, but this is 3x faster
  def split(s: String) = {
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
