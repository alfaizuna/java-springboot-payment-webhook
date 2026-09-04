# ==============================================================================
# Multi-stage Dockerfile for Spring Boot Application
# Base image: Eclipse Temurin Java 21 (Alpine Linux)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build & Package
# ------------------------------------------------------------------------------
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /build

# 1. Salin pom.xml dan unduh dependensi untuk memanfaatkan layer caching Docker
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 2. Salin seluruh source code aplikasi
COPY src ./src

# 3. Build executable JAR tanpa menjalankan unit test saat build image
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Minimal Runtime Environment
# ------------------------------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app

# Pengerasan Keamanan (Security Hardening):
# Jalankan container menggunakan user non-root khusus (appuser)
RUN addgroup -S appgroup && adduser -S appuser -G appgroup

# Salin file JAR hasil build dari Stage 1
COPY --from=builder /build/target/*.jar app.jar
RUN chown -R appuser:appgroup /app

# Gunakan user non-root
USER appuser:appgroup

# Port default Spring Boot
EXPOSE 8080

# Konfigurasi flags JVM untuk container:
# - G1GC untuk efisiensi memory
# - MaxRAMPercentage agar JVM menyesuaikan batas memory cgroup container
# - Dev urandom untuk non-blocking random generation (kriptografi JWT/BCrypt)
ENV JAVA_OPTS="-XX:+UseG1GC -XX:MaxRAMPercentage=75.0 -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
