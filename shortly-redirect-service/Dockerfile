FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY shortly-redirectLookupResult-service/build/libs/*.jar app.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]
