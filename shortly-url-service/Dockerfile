FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY shortly-url-service/build/libs/*.jar app.jar

# Expose application port
EXPOSE 8081

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
