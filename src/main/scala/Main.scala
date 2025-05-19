import cats.effect._
import com.comcast.ip4s.{Host, Port}
import org.apache.spark.ml.feature.{StandardScaler, StandardScalerModel, VectorAssembler}
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.sql.expressions.UserDefinedFunction
import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.http4s.HttpRoutes
import org.http4s.circe.CirceEntityCodec.circeEntityEncoder
import org.http4s.dsl.impl.QueryParamDecoderMatcher
import org.http4s.dsl.io._
import org.http4s.ember.server.EmberServerBuilder
import org.http4s.implicits._
import org.http4s.circe._
import io.circe.generic.auto._

object Main extends IOApp {

  case class Recommendation(track_name: String, artist_name: String, genres: String, similarity: Double)

  implicit val spark: SparkSession = SparkSession.builder()
    .appName("Spotify Track Similarity")
    .master("local[*]")
    .getOrCreate()

  val dfRaw: DataFrame = spark.read
    .option("header", "true")
    .option("inferSchema", "true")
    .csv("src/main/resources/spotifydataset.csv")
    .na.drop()

  val featureCols = Array("danceability", "energy", "valence", "tempo", "acousticness", "instrumentalness", "liveness")

  def assembleAndScaleFeatures(df: DataFrame): DataFrame = {
    val assembler: VectorAssembler = new VectorAssembler()
      .setInputCols(featureCols)
      .setOutputCol("features")

    val dfFeatures: DataFrame = assembler.transform(df)

    val scaler = new StandardScaler()
      .setInputCol("features")
      .setOutputCol("scaledFeatures")
      .setWithStd(true)
      .setWithMean(true)

    val scalerModel: StandardScalerModel = scaler.fit(dfFeatures)
    scalerModel.transform(dfFeatures)
  }

  val dfScaled: DataFrame = assembleAndScaleFeatures(dfRaw).cache()
  dfScaled.count() // Forces evaluation and caching
//  When Not to Cache?
//  •	If the dataset is huge and doesn’t fit in memory.
//  •	If it’s only used once.
//  •	If your cluster has memory pressure.
//
//  In such cases, use .persist(StorageLevel.DISK_ONLY) or avoid caching altogether.

  def getRecommendations(
                          queryTrackName: String,
                          mode: String // "all", "artist", or "genre"
                        )(implicit spark: SparkSession): Seq[Recommendation] = {
    import spark.implicits._

    // Assemble + scale

    // Get query track row
    val queryRow = dfScaled
      .filter($"track_name" === queryTrackName)
      .select("artist_name", "genres", "scaledFeatures")
      .head

    val queryArtist = queryRow.getAs[String]("artist_name")
    val queryGenre  = queryRow.getAs[String]("genres")
    val queryVec    = queryRow.getAs[Vector]("scaledFeatures")

    // Precompute query norm once
    val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)

    val cosineUDF: UserDefinedFunction = F.udf((vec: Vector) => {
      val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
      val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
      if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    })

    // Apply filtering based on mode
    val filteredDF = mode match {
      case "artist" => dfScaled.filter($"artist_name" === queryArtist && $"track_name" =!= queryTrackName)
      case "genre"  => dfScaled.filter($"genres" === queryGenre && $"track_name" =!= queryTrackName)
      case _        => dfScaled.filter($"track_name" =!= queryTrackName)
    }

    // Return top 10 similar tracks
    filteredDF
      .withColumn("similarity", cosineUDF($"scaledFeatures"))
      .orderBy(F.desc("similarity"))
      .select("track_name", "artist_name", "genres", "similarity")
      .as[Recommendation]
      .take(10)
      .toSeq
  }

  val routes = HttpRoutes.of[IO] {
    case GET -> Root / "recommend" :? TrackParam(track) +& ModeParam(mode) =>
      val recs = getRecommendations(track, mode)
      Ok(recs)
  }

  object TrackParam extends QueryParamDecoderMatcher[String]("track")
  object ModeParam extends QueryParamDecoderMatcher[String]("mode")

  override def run(args: List[String]): IO[ExitCode] = {
    EmberServerBuilder.default[IO]
      .withHost(Host.fromString("0.0.0.0").get)
      .withPort(Port.fromInt(8080).get)
      .withHttpApp(routes.orNotFound)
      .build
      .use(_ => IO.never)
      .as(ExitCode.Success)
  }

//  val queryTrack = "One Last Time"
//  val mode = "genre" // "all", "artist", or "genre"
//
//  val topRecommendations = getRecommendations(dfRaw, queryTrack, mode, featureCols)(spark)
//  topRecommendations.show(truncate = false)

}
