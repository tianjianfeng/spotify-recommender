FROM openjdk:11-jre-slim

WORKDIR /app

COPY target/scala-2.13/spotify-recommender-0.1.0.jar /app/app.jar
# resources/spotifydataset.csv is not packaged via sbt assembly, we have to copy it manually
COPY src/main/resources/spotifydataset.csv /app/spotifydataset.csv

# set the system enviorment variable for the CSV file path to override the default in reference.conf
ENTRYPOINT ["java", "-Dcsv.path=/app/spotifydataset.csv", "-jar", "/app/app.jar"]