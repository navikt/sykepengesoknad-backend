FROM gcr.io/distroless/java17@sha256:7551f82ea4e09eb67f06f71becdb76016ff47ba5b370f65660ac271fedb6d2f0

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
