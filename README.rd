

brew install openjdk@11
// Set an environment variable to override Spark’s internal attempt to look up the system user via Hadoop:
export SPARK_USER="$(whoami)"


sbt "runMain Main"


curl "http://localhost:8080/recommend?track=One%20Last%20Time&mode=artist"

### Docker
sbt clean
sbt assembly
docker build -t spotify-recommender .
docker run -p 8080:8080 spotify-recommender
# If your app uses environment variables, add -e flags:
docker run -p 8080:8080 -e SPARK_APP_NAME=MyApp spotify-recommender
