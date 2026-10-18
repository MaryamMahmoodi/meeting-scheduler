FROM gradle:9.1.0-jdk25 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon clean bootJar
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /workspace/build/libs/meeting-scheduler-1.0.0.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
