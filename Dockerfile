# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
# Wrapper and build scripts first so dependency resolution caches across code edits
COPY gradlew ./
COPY gradle ./gradle
COPY build.gradle.kts settings.gradle.kts ./
RUN ./gradlew dependencies --no-daemon -q || true
COPY src ./src
RUN ./gradlew bootJar --no-daemon -x test

FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Lambda Web Adapter: a Lambda extension that proxies the Runtime API to a plain
# HTTP server, so the same image runs unchanged on Lambda and on ECS/local — the
# extension is simply inert outside Lambda. Non-HTTP triggers (SQS) arrive as a
# POST to AWS_LWA_PASS_THROUGH_PATH.
COPY --from=public.ecr.aws/awsguru/aws-lambda-adapter:0.9.1 /lambda-adapter /opt/extensions/lambda-adapter
ENV PORT=8080 \
    AWS_LWA_READINESS_CHECK_PATH=/healthz \
    AWS_LWA_PASS_THROUGH_PATH=/events \
    AWS_LWA_INVOKE_MODE=buffered

COPY --from=build /app/build/libs/*.jar app.jar

# Container-aware heap sizing; the platform owns the actual memory limit.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC -XX:TieredStopAtLevel=1"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
