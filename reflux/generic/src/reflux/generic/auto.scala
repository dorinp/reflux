package reflux.generic

import shapeless.{::, Generic, HList, HNil}
import reflux.{CsvRow, Read, TimeColumn}

object auto {
  implicit val hnilRead: Read[HNil] = Read.instance(_ => HNil)

  implicit def timeColRead[T <: HList](implicit hRead: Read[TimeColumn], tRead: Read[T]): Read[TimeColumn :: T] =
    Read.instance { row => TimeColumn(row.time) :: tRead.read(row) }

  implicit def hListRead[H, T <: HList](implicit hRead: Read[H], tRead: Read[T]): Read[H :: T] =
    Read.instance { row => hRead.read(row) :: tRead.read(row.copy(cursor = row.cursor + 1)) }

  implicit def genericRead[A, R](implicit gen: Generic.Aux[A, R], env: Read[R]): Read[A] =
    Read.instance(row => gen.from(env.read(row)))
}

object ReadInstances {
  implicit val stringReader = new Read[String] {
    override def read(row: CsvRow): String = row.getString(0)
  }
}