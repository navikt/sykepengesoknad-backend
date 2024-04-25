FROM gcr.io/distroless/java21-debian12@sha256:b03ca845543908e297358117a3897451621b73bf22ded1596acccaae5a848dba

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
