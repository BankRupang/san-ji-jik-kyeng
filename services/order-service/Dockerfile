FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY services/order-service/build/libs/*.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
