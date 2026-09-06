FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :auth-service:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=build /workspace/services/auth-service/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8087
ENTRYPOINT ["java", "-jar", "app.jar"]
