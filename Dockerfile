# Use Maven with OpenJDK as the base image for both build and runtime
FROM maven:3.9.6-eclipse-temurin-21 AS build

# Set the working directory in the container
WORKDIR /app

# Copy the entire project (including pom.xml and source code)
COPY . .

# Build the Java application inside the container
RUN mvn clean package

# Use the same Maven image for runtime
FROM maven:3.9.6-eclipse-temurin-21

# Set the working directory
WORKDIR /app

# Copy the built JAR from the previous stage
COPY --from=build /app/target/kafka-integration-1.0-SNAPSHOT.jar /app/kafka-integration.jar

# Command to run the application
CMD ["java", "-jar", "/app/kafka-integration.jar"]
