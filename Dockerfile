FROM gcr.io/distroless/java21-debian12@sha256:4ef80b38c61881bdd4d682df9989a9816f4926f8fb41eaaf55d54a6affe6a6c2

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=70.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
