import cats.effect._
import com.comcast.ip4s.{Host, Port}
import org.apache.spark.sql.DataFrame
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    for {
      spark <- SparkSessionBuilder.createSparkSession()
      _ <- IO(sys.addShutdownHook {
        spark.stop()
      }) // Ensure the shutdown hook is executed as a side effect
      df <- DataLoader.loadAndScaleData(spark)
      recService = new RecommendationService(df)
      routes = Routes(recService)(spark)
      _ <- EmberServerBuilder.default[IO]
        .withHost(Host.fromString("0.0.0.0").get)
        .withPort(Port.fromInt(8080).get)
        .withHttpApp(routes.orNotFound)
        .build
        .use(_ => IO.never)
    } yield ExitCode.Success
  }
}