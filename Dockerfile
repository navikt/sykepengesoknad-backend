FROM gcr.io/distroless/java17-debian11@sha256:891d3c8081acb3b900eeaff045ed3c6fe3f4375a05b909710028ee0b7841e2e9

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
