FROM gcr.io/distroless/java21-debian12@sha256:3b8f431c1192a24465b0efaa04155d4be2a808279a19aae2a57b5a355f3192df

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
