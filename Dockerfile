FROM gcr.io/distroless/java21-debian12@sha256:fe0560fbf87031402f6c725917eabbf9217b5e9fc95fdff9a004dba3587a0513

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
