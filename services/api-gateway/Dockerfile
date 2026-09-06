FROM eclipse-temurin:21-jdk-jammy AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew :api-gateway:bootJar -x test --no-daemon

FROM eclipse-temurin:21-jre-jammy
RUN groupadd --system spring && useradd --system --gid spring spring
WORKDIR /app
COPY --from=build /workspace/services/api-gateway/build/libs/*.jar app.jar
USER spring:spring
EXPOSE 8086
ENTRYPOINT ["java", "-jar", "app.jar"]
