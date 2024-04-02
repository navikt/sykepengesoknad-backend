FROM gcr.io/distroless/java21-debian12@sha256:245a5c2bbdbd5c9f859079f885cd03054340f554c6fcf67f14fef894a926979b

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
