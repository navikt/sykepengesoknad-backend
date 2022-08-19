FROM gcr.io/distroless/java17@sha256:4e790b86d291fbe26fbd05bbd4dfac4f0daeaf40167085f14933447b87dd6ad2

COPY build/libs/app.jar /app/
WORKDIR /app
CMD ["app.jar"]
