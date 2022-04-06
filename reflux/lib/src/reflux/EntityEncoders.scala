package reflux

import fs2.text
import org.http4s.{Entity, EntityEncoder, Headers}

object EntityEncoders {
  def jsonArrayEncoder[F[_], A](prefix: String = "[", suffix: String = "]", delimiter: String = ",")(implicit encoder: EntityEncoder[F, A]): EntityEncoder[F, fs2.Stream[F, A]] = {
    val `,` = fs2.Stream(delimiter).through(text.utf8.encode)
    val `[` = fs2.Stream(prefix).through(text.utf8.encode)
    val `]` = fs2.Stream(suffix).through(text.utf8.encode)

    val se = EntityEncoder.streamEncoder[F,A]
    new EntityEncoder[F, fs2.Stream[F, A]] {
      override def toEntity(stream: fs2.Stream[F, A]): Entity[F] = Entity(`[` ++ stream.map(encoder.toEntity(_).body).intersperse(`,`).flatten ++ `]`)
      override def headers: Headers = se.headers
    }
  }
}
