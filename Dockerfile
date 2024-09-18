FROM gcr.io/distroless/java21-debian12@sha256:cf7fce959603124dcc412bc2d245d84a4e0d86c4c02c6eae2cf13e973c246f24

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
