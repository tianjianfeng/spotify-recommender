import cats.effect._
import com.comcast.ip4s.{Host, Port}
import org.apache.spark.sql.DataFrame
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._

object Main extends IOApp {
  override def run(args: List[String]): IO[ExitCode] = {
    val spark = SparkSessionBuilder.createSparkSession()
    sys.addShutdownHook {
      spark.stop()
    }
    DataLoader.loadAndScaleData(spark).flatMap { df =>
      val recService = new RecommendationService(df)
      val routes = Routes(recService)(spark)

      EmberServerBuilder.default[IO]
        .withHost(Host.fromString("0.0.0.0").get)
        .withPort(Port.fromInt(8080).get)
        .withHttpApp(routes.orNotFound)
        .build
        .use(_ => IO.never)
        .as(ExitCode.Success)
    }
  }
}