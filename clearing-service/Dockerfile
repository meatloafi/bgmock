# Stage 1: Build
FROM maven:3.9.3-eclipse-temurin-17 AS build
WORKDIR /app

# 1️⃣ Copy only pom.xml first, download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2️⃣ Copy the rest of the source code
COPY src ./src

# 3️⃣ Build project
RUN mvn clean install -DskipTests

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT ["java","-jar","app.jar"]
