import org.apache.spark.sql.{DataFrame, SparkSession, functions => F}
import org.apache.spark.ml.linalg.Vector
import org.apache.spark.sql.expressions.UserDefinedFunction

case class Recommendation(track_name: String, artist_name: String, genres: String, similarity: Double)

class RecommendationService(dfScaled: DataFrame) {
  def getRecommendations(queryTrackName: String, mode: String)(implicit spark: SparkSession): Seq[Recommendation] = {
    import spark.implicits._

    val queryRow = dfScaled
      .filter($"track_name" === queryTrackName)
      .select("artist_name", "genres", "scaledFeatures")
      .head

    val queryArtist = queryRow.getAs[String]("artist_name")
    val queryGenre  = queryRow.getAs[String]("genres")
    val queryVec    = queryRow.getAs[Vector]("scaledFeatures")
    val normB = math.sqrt(queryVec.toArray.map(x => x * x).sum)

    val cosineUDF: UserDefinedFunction = F.udf((vec: Vector) => {
      val dot = vec.toArray.zip(queryVec.toArray).map { case (a, b) => a * b }.sum
      val normA = math.sqrt(vec.toArray.map(x => x * x).sum)
      if (normA == 0.0 || normB == 0.0) 0.0 else dot / (normA * normB)
    })

    val filteredDF = mode match {
      case "artist" => dfScaled.filter($"artist_name" === queryArtist && $"track_name" =!= queryTrackName)
      case "genre"  => dfScaled.filter($"genres" === queryGenre && $"track_name" =!= queryTrackName)
      case _        => dfScaled.filter($"track_name" =!= queryTrackName)
    }

    filteredDF
      .withColumn("similarity", cosineUDF($"scaledFeatures"))
      .orderBy(F.desc("similarity"))
      .select("track_name", "artist_name", "genres", "similarity")
      .as[Recommendation]
      .take(10)
      .toSeq
  }
}
