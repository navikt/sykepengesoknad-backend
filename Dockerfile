FROM gcr.io/distroless/java21-debian13@sha256:26a517c7f7d69a98adab4d1e71d5a3a9f1079c85ac9c4193ce6b6bd3d73496f3

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
