# syntax=docker/dockerfile:1
#
# Self-contained image build (no local Java/Node needed). Used by Render and as an
# alternative to `npm run java:docker` (Jib). Both produce a prod-profile image.

# ---- Build stage: compiles the Angular client and the Spring Boot jar ----
FROM eclipse-temurin:21-jdk-noble AS build
WORKDIR /workspace
COPY . .
# Windows checkouts can give mvnw CRLF line endings, which break /bin/sh.
RUN sed -i 's/\r$//' mvnw && chmod +x mvnw \
    && ./mvnw -ntp -B -Pprod -DskipTests verify \
    && cp target/*.jar /workspace/app.jar

# ---- Runtime stage ----
FROM eclipse-temurin:21-jre-noble
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
USER 1000:1000
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_OPTS="-XX:MaxRAMPercentage=75"
EXPOSE 8080
# Render injects PORT; locally it falls back to 8080.
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar --server.port=${PORT:-8080}"]
