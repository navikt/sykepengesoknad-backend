FROM gcr.io/distroless/java17@sha256:8f47f004831ff3c6f5199e8c088116af1c883768e837e5f7755c5a4e1a3a22b8

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
