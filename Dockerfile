# ============================================================
#  FixMate backend — multi-stage Docker build
#  Stage 1 builds the Spring Boot jar with Maven; stage 2 runs it on a JRE.
# ============================================================

# --- Build stage ---
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app

# Copy the Maven wrapper and pom first, to cache dependencies
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -B dependency:go-offline

# Copy the source and build the jar
COPY src ./src
RUN ./mvnw -q -B clean package -DskipTests

# --- Run stage ---
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar

# Use the Docker profile (reads secrets from environment variables)
ENV SPRING_PROFILES_ACTIVE=docker
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
