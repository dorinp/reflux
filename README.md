reflux
===
Streaming InfluxDB client for scala
### Features
- Constant memory usage on queries returning large amounts of data
- Small, only depends on http4s,fs2,cats-effect
- optional auto derivation on Read instances (convert from Influx format to case classes or even tuples) 
  
### Quick start 
Add jitpack as a resolver in sbt: 
```scala
resolvers += "jitpack" at "https://jitpack.io"
```
Add reflux as a dependency 
```scala
libraryDependencies += "com.github.dorinp.reflux" %% "reflux-generic" % "0.0.14"
```
```scala
val influx = reflux.clientIO("http://localhost:8086").unsafeRunSync().use("mydatabase").withCredentials("user", "password")
println(influx.asVector[String]("select * from weather").unsafeRunSync())
```  
You can map the results to an arbitrary class by implementing an instance of `Read`
```scala
case class Weather(city: String, temperature: Int)
implicit val r = new Read[Weather] {override def read(row: CsvRow): Weather = Weather(row.getString("city"), row.getString("temperature").toInt)}

val data = influx.asVector[Weather]("select * from weather").unsafeRunSync()
```
reflux comes with `Read` instances for the common scalar types like String, Int. Long, Instant, etc.
#### Writing data
```scala
influx.write("weather", Measurement(values = Seq("temp" -> "10", "rainfall" -> "20"), tags = Seq("city" -> "London")))
```  
You can implement an instance of `ToMeasurement` to allow writing of arbitrary data.
write accepts any `Iterable`, as well as fs2 `Stream`s.
This makes it trivial to copy data from one server to another:
```scala
val data = source.stream[Measurement]("select * from weather")
destination.write("dbcopy", data)
```
#### Automatic `Read` derivation
```scala
libraryDependencies += "org.reflux"  %% "reflux-generic" % "0.0.14"
```
```scala
import reflux.generic.auto._
case class Weather(city: String, temperature: Int)
influx.stream[Measurement]("select * from weather")
//or
influx.asVector[Measurement]("select * from temps")
```  
Tuples can also be used as a result type
```scala
influx.stream[(String, Int)]("select * from weather")
```
Values are matched by the order they are mentioned in the query. 
Due to how time is represented in Influx, you need to use the special type `TimeColumn`, it can be in any position
```scala
reflux.stream[(Int, Int, TimeColumn)]("select temp, rainfall from weather)
```  

#### Http4s EntityEncoder for streaming Json arrays
```scala
import reflux.generic.auto._
implicit val encoder = jsonArrayEncoder[IO, MyClass]()
case class Weather(city: String, temperature: Int)

case GET -> Root / "weather"  =>
  val stream = influx.stream[Weather]("select * from weather")
  Ok(stream)
```
The prefix, suffix, and delimiter are configurable   
