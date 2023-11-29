FROM gcr.io/distroless/java17-debian11@sha256:b50c5d2f702a755dfaa18463edfd76c6cd0d8cb9a0e3073d99b8392b189aab24

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
