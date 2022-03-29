FROM gcr.io/distroless/java17@sha256:63548c70f5bb6f33c15d1420475f0a39119fd7b5112097433708bad2771e5ccc

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
