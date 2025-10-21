FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Copy the built JAR file
COPY shortly-click-service/build/libs/*.jar app.jar

# Expose application port
EXPOSE 8083

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
