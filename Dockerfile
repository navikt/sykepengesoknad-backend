FROM gcr.io/distroless/java25-debian13@sha256:2c2450426c449aa19e6fb12a151172187588f6f6fc6d7183b3af559d208a5e8b

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2 -XX:+UseCompactObjectHeaders"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
