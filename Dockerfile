FROM gcr.io/distroless/java21-debian13@sha256:6cc0aa2d30ed370e4e2ff75091bd547e15f9cf9508796ae21dff331b7c406cd9

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
