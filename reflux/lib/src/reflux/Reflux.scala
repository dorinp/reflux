package reflux

import cats.effect.{ConcurrentEffect, ContextShift, IO, Timer}
import cats.syntax.functor._
import org.http4s.Uri
import org.http4s.client.blaze.BlazeClientBuilder

import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.higherKinds

object Reflux {
  def client[F[_] : ConcurrentEffect](serverUrl: String): F[InfluxClient[F]] = client(Uri.unsafeFromString(serverUrl))

  def client[F[_] : ConcurrentEffect](serverUrl: Uri): F[InfluxClient[F]] = for {
    http  <- BlazeClientBuilder[F](global).resource.allocated.map(_._1)
  } yield new InfluxClient[F](http, serverUrl)

  def clientIO(serverUrl: String): IO[InfluxClient[IO]] = clientIO(Uri.unsafeFromString(serverUrl))

  def clientIO(serverUrl: Uri): IO[InfluxClient[IO]] = {
    implicit val shift: ContextShift[IO] = IO.contextShift(global)
    implicit val timer: Timer[IO] = IO.timer(global)
    client[IO](serverUrl)
  }
}
