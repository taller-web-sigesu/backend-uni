# Imagen de ejecución del backend (CI y despliegues). El entorno de desarrollo usa Dockerfile.dev.
FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml ./
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package \
    && cp target/backend-uni-*.jar /build/app.jar

FROM eclipse-temurin:21-jre
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && useradd --system --create-home --shell /usr/sbin/nologin app \
    && mkdir -p /data/evidencias && chown app:app /data/evidencias
WORKDIR /app
COPY --from=build --chown=app:app /build/app.jar /app/app.jar
USER app
# Evidencias locales (APP_STORAGE_PROVIDER=local); monte un volumen aquí para conservarlas.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -Duser.timezone=UTC" \
    APP_STORAGE_LOCAL_DIR=/data/evidencias
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=60s --retries=12 \
    CMD curl --fail --silent --output /dev/null http://127.0.0.1:8080/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
