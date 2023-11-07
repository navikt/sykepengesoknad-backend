FROM gcr.io/distroless/java17-debian11@sha256:12c7afb1875a0c01f2c0138698e619a1d39a8319fd40e020a6d8349cf5aae043

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
