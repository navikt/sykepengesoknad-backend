FROM gcr.io/distroless/java17-debian11@sha256:c737fc29fc2556d3377d6a719a9842a500777fce35a7f1299acd569c73f65247

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
