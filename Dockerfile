FROM gcr.io/distroless/java21-debian13@sha256:53ce8e6ab58ff683ec8de330610605a8d0a2d12135d3a1b79fe53290eb1999f2

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
