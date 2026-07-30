# syntax=docker/dockerfile:1

# ---------------------------------------------------------------- build
# Imagem oficial do Gradle: o repositorio nao versiona gradle-wrapper.jar,
# entao depender de ./gradlew quebraria o build. Ver README, secao Infraestrutura.
FROM gradle:8.14.2-jdk21-alpine AS build
WORKDIR /build

# Camada de dependencias: so invalida quando os arquivos de build mudam.
COPY settings.gradle.kts build.gradle.kts gradle.properties ./
RUN gradle dependencies --no-daemon || true

COPY src ./src
RUN gradle bootJar --no-daemon -x test

# ---------------------------------------------------------------- runtime
FROM eclipse-temurin:21-jre-alpine AS runtime

RUN addgroup -S app && adduser -S app -G app
WORKDIR /app

COPY --from=build --chown=app:app /build/build/libs/*.jar app.jar

USER app
EXPOSE 8080

# Ajusta o heap ao container — sem isso a JVM assume a memoria do host,
# o que num t3.small de 2 GB dividido com PostgreSQL causaria OOM.
ENV JAVA_OPTS="-XX:MaxRAMPercentage=70 -XX:+UseSerialGC"

HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar app.jar"]
