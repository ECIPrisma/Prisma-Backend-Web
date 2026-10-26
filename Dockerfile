# Multi-stage build para Prisma Backend API (Spring Boot 3.3.4 / Java 21)

# Etapa 1: Compilacion con Maven y OpenJDK 21
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /build

# Copiar configuracion de dependencias y wrappers
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn/
RUN chmod +x mvnw && ./mvnw dependency:go-offline -B || true

# Copiar codigo fuente y compilar
COPY src src/
COPY services services/
RUN ./mvnw clean package -DskipTests

# Etapa 2: Imagen final de ejecucion ligera (JRE)
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copiar el binario compilado
COPY --from=builder /build/target/prisma-backend-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
