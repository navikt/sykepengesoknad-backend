FROM gcr.io/distroless/java17-debian11@sha256:672df6324b5e36527b201135c37c3ed14579b2eb9485a4f4e9ab526d466f671c

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
