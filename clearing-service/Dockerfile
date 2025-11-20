# Build stage
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy parent POM
COPY pom.xml .

# Copy all modules
COPY common/ ./common/
COPY bank-service/ ./bank-service/
COPY clearing-service/ ./clearing-service/

# Build the entire project from root, then build clearing-service specifically
RUN mvn clean install -DskipTests -pl clearing-service -am
RUN ls -lh /app/clearing-service/target/*.jar

# Runtime stage
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/clearing-service/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]