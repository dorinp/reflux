package reflux

import cats.effect.Async
import org.http4s.Uri
import org.http4s.Uri.{Authority, Path}
import org.http4s.client.Client

object Reflux {
  /** THe url parameter can contain InfluxDB credentials and the database name as following:
   * htpp[s]://user:password@host[:port]/[database]
   */
  def client[F[_] : Async](http: Client[F], serverUrl: Uri): InfluxClient[F] = {
    serverUrl match {
      case u@Uri(_, Some(Authority(Some(userInfo), _, _)), path, _, _) =>
        new InfluxClient[F](http, u.withPath(Path.empty).copy(authority = u.authority.map(_.copy(userInfo = None))))
          .use(path.segments.headOption.map(_.encoded))
          .withCredentials(userInfo.username, userInfo.password.getOrElse(""))

      case u@Uri(_, _, path, _, _) if !path.isEmpty =>
        new InfluxClient[F](http, u.withPath(Path.empty)).use(path.segments.headOption.map(_.encoded))

      case u =>
        new InfluxClient[F](http, u)

    }
  }

  def client[F[_] : Async](http: Client[F], serverUrl: String): InfluxClient[F] = client(http, Uri.unsafeFromString(serverUrl))
}
