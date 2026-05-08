# Use Maven image to build the application
FROM eclipse-temurin:21-jdk-alpine AS build

# Install Maven
RUN apk add --no-cache maven

# Set working directory
WORKDIR /app

# Copy pom.xml first to leverage Docker layer caching
COPY copayagent/pom.xml .

# Copy source code
COPY copayagent/src ./src

# Build the application
RUN mvn clean package -DskipTests

# Use OpenJDK runtime image
FROM eclipse-temurin:21-jre-alpine

# Set working directory
WORKDIR /app

# Copy the built jar from the build stage
COPY --from=build /app/target/copayagent-0.0.1-SNAPSHOT.jar app.jar

# Expose port (default Spring Boot port)
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]
