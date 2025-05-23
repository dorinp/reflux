package reflux

import cats.effect.{Async, Sync}
import cats.{Applicative, Functor}
import fs2.*
import org.http4s.*
import org.http4s.Method.*
import org.http4s.client.Client
import org.http4s.client.dsl.Http4sClientDsl
import org.http4s.headers.{Accept, Authorization}

class InfluxClient[F[_]: Async](http: Client[F], val serverUrl: Uri, db: Option[String] = None) extends Http4sClientDsl[F] with Tools[F] {
  private[reflux] val queryUri: Uri = (serverUrl / "query").withOptionQueryParam("db", db).withQueryParam("epoch", "ms")
  private[reflux] val writeUri: Uri = (serverUrl / "write").withOptionQueryParam("db", db).withQueryParam("precision", "ms")

  def write(retentionPolicy: String, measurement: String, values: Measurement*): F[Unit] =
    write(measurement, fs2.Stream(values*), Some(retentionPolicy))

  def write(measurement: String, values: Measurement*): F[Unit] =
    write(measurement, fs2.Stream(values*))

  def write[A](values: Iterable[A])(implicit mapper: ToMeasurement[A]): F[Unit] = write(values, None)

  def write[A](values: Iterable[A], retentionPolicy: Option[String])(implicit mapper: ToMeasurement[A]): F[Unit] =
    write(fs2.Stream.fromIterator(values.iterator, 1024), retentionPolicy)

  def write[A](values: fs2.Stream[F, A])(implicit mapper: ToMeasurement[A]): F[Unit] = write(values, None)

  def write[A](values: fs2.Stream[F, A], retentionPolicy: Option[String])(implicit mapper: ToMeasurement[A]): F[Unit] =
    write(mapper.measurementName, values.map(mapper.write), retentionPolicy)

  def write(measurement: String, values: fs2.Stream[F, Measurement]): F[Unit] =
    write(measurement, values, None)

  def write(measurement: String, values: fs2.Stream[F, Measurement], retentionPolicy: Option[String]): F[Unit] = {
    def nameValue(t: (String, String))        = t._1 + "=" + t._2
    def nameValues(vs: Seq[(String, String)]) = vs.map(nameValue)

    def toStr(m: Measurement) =
      s"${(measurement +: nameValues(m.tags)).mkString(",")} ${nameValues(m.values).mkString(",")} ${m.time.map(_.toEpochMilli).getOrElse("")}\n"

    http.run(Request[F](POST, writeUri.withOptionQueryParam("rp", retentionPolicy), body = values.map(toStr).through(text.utf8.encode))).use { r =>
      if(r.status.isSuccess) Applicative[F].unit else handleError(r).as(()).compile.lastOrError
    }
  }

  def stream[A](query: String)(implicit reader: Read[A]): Stream[F, A] = stream[A](query, 3)

  protected def stream[A](query: String, csvDataIndex: Int = 3)(implicit reader: Read[A]): Stream[F, A] = streamRaw(query, csvDataIndex).map(reader.read)

  def streamRaw(query: String, csvDataIndex: Int = 3): Stream[F, CsvRow] = {
    http.stream(POST(UrlForm("q" -> query), queryUri.withQueryParam("chunked", "true"), Accept(MediaType.text.csv))).flatMap { r =>
      if(r.status.isSuccess) r.body.through(Csv.rows(csvDataIndex)) else handleError(r)
    }
  }

  private def handleError(r: Response[F]) = r.body.through(text.utf8.decode).take(4096).flatMap(s => Stream.raiseError(InfluxException(r.status, s)))

  def asVector[A : Read](query: String): F[Vector[A]] = stream[A](query).compile.toVector

  def exec(query: String): F[Vector[String]] = stream[String](query, 2).compile.toVector

  def use(database: String): InfluxClient[F] = use(Some(database))

  def use(maybeDatabase: Option[String]) = new InfluxClient(http, serverUrl, maybeDatabase)

  def withCredentials(username: String, password: String) = new InfluxClient(Authenticator(username, password)(http), serverUrl, db)

  override implicit val sync: Functor[F] = Functor[F]
}

case class InfluxException(status: Status, message: String) extends Throwable(s"$status: $message")

trait Tools[F[_]] { self: InfluxClient[F] =>
  import cats.syntax.functor.*
  implicit val sync: Functor[F]

  def createDatabase(name: String): F[Unit] = exec(s"""CREATE DATABASE "$name"""").map(_ => ())
  def dropDatabase(name: String): F[Unit]             = exec(s"""DROP DATABASE "$name"""").map(_ => ())
  def listDatabases:  F[Vector[String]]               = exec("SHOW DATABASES").map(_.filter(_ != "_internal"))
  def listUsers:      F[Vector[String]]               = exec("SHOW USERS")

  def databaseExists(name: String): F[Boolean]        = listDatabases.map(_.contains(name))
}

object Authenticator {
  def apply[F[_]: Sync](username: String, password: String)(client: Client[F]): Client[F] =
    Client { req =>
      client.run(req.withHeaders(req.headers.put(Authorization(BasicCredentials(username, password)))))
    }
}
