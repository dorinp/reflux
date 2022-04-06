package reflux

import cats.effect.{Async, IO}
import cats.syntax.functor._
import org.http4s.Uri
import org.http4s.blaze.client.BlazeClientBuilder


object Reflux {
  def client[F[_] : Async](serverUrl: String): F[InfluxClient[F]] = client(Uri.unsafeFromString(serverUrl))

  def client[F[_] : Async](serverUrl: Uri): F[InfluxClient[F]] = for {
    http  <- BlazeClientBuilder[F].resource.allocated.map(_._1)
  } yield new InfluxClient[F](http, serverUrl)

  def clientIO(serverUrl: String): IO[InfluxClient[IO]] = clientIO(Uri.unsafeFromString(serverUrl))

  def clientIO(serverUrl: Uri): IO[InfluxClient[IO]] = client[IO](serverUrl)

}
