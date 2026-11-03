FROM gcr.io/distroless/java21-debian13@sha256:1bc4eca3c3b351743be673d7109951b509d6f47a57648e5e2efe784a1b051c4a

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
