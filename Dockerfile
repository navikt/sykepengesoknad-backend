FROM gcr.io/distroless/java21-debian13@sha256:46918c99fec3a4fb69c5e6d0679883935997f63ad602165369795039875384b0

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
