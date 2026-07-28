# Build stage: compile the Spring Boot jar with the Gradle wrapper.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the wrapper first so dependency resolution is cached separately from source.
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x ./gradlew && ./gradlew dependencies --no-daemon || true

COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

# Runtime stage: JRE only, no Gradle or source in the shipped image.
FROM eclipse-temurin:21-jre
WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

# Railway injects PORT; application.yml reads it via ${PORT:8080}.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
