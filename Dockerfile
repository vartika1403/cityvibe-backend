# ---- Stage 1: build the jar ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Cache dependencies first: copy only the POM, resolve, then copy sources.
COPY pom.xml .
RUN mvn -q dependency:go-offline

COPY src ./src
RUN mvn -q clean package -DskipTests

# ---- Stage 2: minimal runtime image ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Run as an unprivileged user rather than root.
RUN groupadd --system app && useradd --system --gid app app

# Copy the repackaged Spring Boot jar from the build stage.
COPY --from=build /app/target/cityvibe-backend-*.jar app.jar
RUN chown app:app app.jar
USER app

# Default to the production profile; override with -e SPRING_PROFILES_ACTIVE=dev if needed.
ENV SPRING_PROFILES_ACTIVE=prod

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
