FROM europe-north1-docker.pkg.dev/cgr-nav/pull-through/nav.no/jre:openjdk-25

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2 -XX:+UseCompactObjectHeaders"

COPY build/libs/app.jar /app/
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]
