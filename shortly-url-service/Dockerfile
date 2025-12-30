FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY shortly-url-service/build/libs/shortly-url-service.jar app.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "app.jar"]
