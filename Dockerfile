# Stage 1: build the jar (tests run in CI, not here)
FROM eclipse-temurin:25-jdk AS build

WORKDIR /app

# Dependencies first: this layer is only rebuilt when the Gradle files change
COPY gradlew .
COPY gradle ./gradle
COPY build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon -q > /dev/null 2>&1 || true

COPY src ./src
RUN ./gradlew bootJar -x test --no-daemon \
    && java -Djarmode=tools -jar build/libs/*.jar extract --layers --destination build/extracted

# Stage 2: run it on a JRE (not a JDK), as an unprivileged user, with the Spring Boot layers split so a code change
# only replaces the small "application" layer
FROM eclipse-temurin:25-jre-alpine

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build --chown=app:app /app/build/extracted/dependencies/ ./
COPY --from=build --chown=app:app /app/build/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /app/build/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /app/build/extracted/application/ ./

USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "capstone-0.0.1-SNAPSHOT.jar"]
