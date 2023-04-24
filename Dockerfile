FROM gcr.io/distroless/java17@sha256:5f91857d1e8d8883299bdd0a19c09a532e776c843d9877076cfa1e802a75b282

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
