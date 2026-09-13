FROM gradle:9-jdk21 AS deps
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon dependencies -q

FROM deps AS test
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon check

FROM deps AS build
COPY src ./src
RUN --mount=type=cache,target=/home/gradle/.gradle gradle --no-daemon bootJar -x test && mv build/libs/*.jar app.jar

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app
RUN useradd -m -u 10001 appuser
USER appuser

COPY --from=build --chown=appuser:appuser /app/app.jar /app/app.jar

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-XX:+UseZGC", "-jar", "/app/app.jar"]
