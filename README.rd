

brew install openjdk@11
// Set an environment variable to override Spark’s internal attempt to look up the system user via Hadoop:
export SPARK_USER="$(whoami)"


sbt "runMain Main"


curl "http://localhost:8080/recommend?track=One%20Last%20Time&mode=artist"