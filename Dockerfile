FROM gcr.io/distroless/java21-debian13@sha256:27d6932e85923aa9baf382f3daed5a587fe764c4c5397a0fa085a3f1b8f637ec

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
