# Multi-stage build para Prisma Backend API (Spring Boot 3.3.4 / Java 21)

# Etapa 1: Compilacion con Maven preinstalado y OpenJDK 21
FROM maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# Copiar pom y pre-descargar dependencias para aprovechar cache de capas Docker
COPY pom.xml ./
RUN mvn dependency:go-offline -B || true

# Copiar codigo fuente y compilar JAR de produccion
COPY src src/
RUN mvn clean package -DskipTests

# Etapa 2: Imagen final de ejecucion ligera (JRE 21)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el binario compilado
COPY --from=builder /build/target/prisma-backend-0.0.1-SNAPSHOT.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
