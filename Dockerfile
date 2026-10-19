FROM gradle:9.1.0-jdk25 AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
RUN ./gradlew --no-daemon --version

# docs/openapi.yaml is packaged into the jar (see processResources in build.gradle).
COPY docs docs
COPY src src
# Tests are skipped here: they start their own PostgreSQL through Testcontainers, which needs Docker.
RUN ./gradlew --no-daemon bootJar -x test \
 && cp build/libs/*.jar /workspace/app.jar

FROM eclipse-temurin:25-jre-alpine
RUN addgroup -S app && adduser -S -G app app
USER app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --start-period=40s --retries=5 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
