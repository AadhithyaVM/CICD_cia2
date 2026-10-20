# Runtime-only image. The JAR is produced by `mvn package` in the CI pipeline.
FROM eclipse-temurin:17-jre-alpine

# Run as a non-root user
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY target/app.jar /app/app.jar

ARG BUILD_VERSION=local
ENV APP_BUILD_VERSION=${BUILD_VERSION}

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
