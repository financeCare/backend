# Stage 1: Build jar
FROM eclipse-temurin:21-jdk AS build

WORKDIR /app

COPY gradlew .
COPY gradle ./gradle
COPY build.gradle .
COPY settings.gradle .
COPY src ./src

RUN chmod +x gradlew
RUN ./gradlew clean build -x test

# Stage 2: Run jar
FROM eclipse-temurin:21-jdk-alpine

WORKDIR /app

# COPY --from=build /app/build/libs/*.jar app.jar
# ใช้คำสั่งที่เจาะจงเพื่อไม่ให้ก๊อปปี้ plain jar
COPY --from=build /app/build/libs/*[!plain].jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]