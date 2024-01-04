FROM gcr.io/distroless/java17-debian11@sha256:bc99bb6dfb842c0d07410cb341e1a993b4b75198f6e883315d8dc42588844a16

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
