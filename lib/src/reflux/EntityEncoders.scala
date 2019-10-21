package reflux

import org.http4s.{Entity, EntityEncoder, Headers}
import scala.language.higherKinds

object EntityEncoders {
  def jsonArrayEncoder[F[_], A](prefix: String = "[", suffix: String = "]", delimiter: String = ",")(implicit encoder: EntityEncoder[F, A]): EntityEncoder[F, fs2.Stream[F, A]] = {
    val `,` = fs2.Stream(delimiter).through(fs2.text.utf8Encode)
    val `[` = fs2.Stream(prefix).through(fs2.text.utf8Encode)
    val `]` = fs2.Stream(suffix).through(fs2.text.utf8Encode)

    val se = EntityEncoder.streamEncoder[F,A]
    new EntityEncoder[F, fs2.Stream[F, A]] {
      override def toEntity(stream: fs2.Stream[F, A]): Entity[F] = Entity(`[` ++ stream.map(encoder.toEntity(_).body).intersperse(`,`).flatten ++ `]`)
      override def headers: Headers = se.headers
    }
  }
}
