FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY config-server/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
