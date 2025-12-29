FROM gcr.io/distroless/java21-debian12@sha256:ed87b011df38601c55503cb24a0d136fed216aeb3bcd57925719488d93d236f4

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
