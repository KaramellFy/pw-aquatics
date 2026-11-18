# Build stage: compile and package the Spring Boot application.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace
COPY gradlew settings.gradle.kts build.gradle.kts ./
COPY gradle gradle
RUN chmod +x gradlew
# Download dependencies in a separate layer so code changes don't refetch them.
RUN ./gradlew dependencies --no-daemon -q > /dev/null
COPY src src
RUN ./gradlew bootJar --no-daemon -x test \
    && java -Djarmode=tools -jar build/libs/app.jar extract --destination extracted

# Runtime stage: JRE only, non-root user. The user has a fixed id (10001), so uploaded photos keep
# a known owner across image updates and restores (infra/backup/restore.sh).
FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 app && useradd --system --uid 10001 --gid app --home /app app \
    && mkdir -p /app/media && chown app:app /app/media
WORKDIR /app
COPY --from=build --chown=app:app /workspace/extracted/lib ./lib
COPY --from=build --chown=app:app /workspace/extracted/app.jar ./app.jar
USER app
EXPOSE 8080
ENV MEDIA_LOCAL_PATH=/app/media
# --enable-native-access: the WebP encoder for product photos uses a bundled native library.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "--enable-native-access=ALL-UNNAMED", "-jar", "app.jar"]
