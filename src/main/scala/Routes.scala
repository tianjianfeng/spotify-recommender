import cats.effect.IO
import org.http4s.HttpRoutes
import org.http4s.dsl.io._
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import io.circe.generic.auto._
import org.apache.spark.sql.SparkSession

object Routes {
  object TrackParam extends QueryParamDecoderMatcher[String]("track")
  object ModeParam extends QueryParamDecoderMatcher[String]("mode")

  def apply(recService: RecommendationService)(implicit spark: SparkSession): HttpRoutes[IO] = HttpRoutes.of[IO] {
    case GET -> Root / "recommend" :? TrackParam(track) +& ModeParam(mode) =>
      IO.blocking(recService.getRecommendations(track, mode)).flatMap(Ok(_))
  }
}