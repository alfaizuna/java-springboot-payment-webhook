# Baseapp — Production-Ready Spring Boot REST API with JWT RS256 & Redis

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-7.x-green.svg)](https://spring.io/projects/spring-security)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Flyway](https://img.shields.io/badge/Flyway-Migration-blueviolet.svg)](https://flywaydb.org/)
[![Swagger](https://img.shields.io/badge/OpenAPI-3.0%20%2F%20Swagger-85EA2D.svg)](https://swagger.io/)
[![Docker](https://img.shields.io/badge/Docker-Multi--stage-2496ED.svg)](https://www.docker.com/)

A enterprise-grade, production-ready **Spring Boot REST API starter kit** implementing **Asymmetric JWT (RS256)** authentication, **Redis-backed Token Revocation (Blacklist)**, **Refresh Token Rotation**, **Flyway Database Migrations**, **Input Validation**, **Role-Based Access Control (RBAC)**, **Swagger / OpenAPI 3**, **Comprehensive Automated Tests**, and **Docker Multi-Stage Deployment**.

---

## 🌟 Fitur Utama (Key Features)

- 🔑 **Asymmetric JWT (RS256 RSA 2048-bit)**:
  - Token ditandatangani menggunakan **RSA Private Key** (hanya disimpan di auth server).
  - Verifikasi token menggunakan **RSA Public Key** (dapat di-expose via `/api/v1/auth/public-key` untuk resource server / microservices).
- ⚡ **Redis Token Blacklist (O(1))**:
  - Logout seketika mem-blacklist Access Token ke Redis dengan TTL dinamis sesuai sisa umur token.
  - Token yang di-logout langsung ditolak seketika (HTTP 403) meskipun belum expired secara waktu.
- 🔄 **Refresh Token Rotation & Revocation**:
  - Refresh token berumur panjang disimpan di PostgreSQL.
  - Setiap rotasi token menghasilkan refresh token baru dan me-revoke token lama untuk mencegah replay attacks.
- 🗄️ **Database Migration dengan Flyway**:
  - Migrasi DDL skema terstruktur (`V1__init_users_table.sql`, `V2__create_refresh_tokens_table.sql`).
  - Hibernate disetel ke `ddl-auto: validate` untuk menjamin keamanan skema di lingkungan produksi.
- 🛡️ **Role-Based Access Control (RBAC)**:
  - Dukungan otorisasi bertingkat (`ROLE_USER` dan `ROLE_ADMIN`) dengan Spring Security method security (`@PreAuthorize`).
- 👤 **User Management Endpoints**:
  - Melihat & update profil (`/api/v1/users/me`), ganti password dengan validasi password lama, serta paginasi dan penghapusan user untuk Admin.
- 🧩 **Input Validation & Global Exception Handler**:
  - Validasi ketat request body DTO menggunakan `jakarta.validation` (`@NotBlank`, `@Email`, `@Size`).
  - Handler terpusat (`@RestControllerAdvice`) dengan format error response standar JSON.
- 📚 **Dokumentasi Interaktif (OpenAPI 3 & Swagger UI)**:
  - Antarmuka visual Swagger UI di `/swagger-ui.html` dengan integrasi otorisasi Bearer JWT.
- 🧪 **Rangkaian Test Otomatis (26 Test Cases)**:
  - Unit testing terisolasi (JUnit 5 & Mockito) untuk `JwtService`, `TokenBlacklistService`, dan `RefreshTokenService`.
  - Integration testing end-to-end (`@SpringBootTest` & `MockMvc`) menguji alur auth lengkap.
- 🐳 **Containerisasi & Orkestrasi Siap Produksi**:
  - Multi-stage build `Dockerfile` berbasis Eclipse Temurin 21 Alpine dengan user non-root (`appuser`).
  - `docker-compose.yml` siap pakai untuk menjalankan PostgreSQL, Redis, dan Baseapp secara terpadu.

---

## 🧰 Tech Stack

| Komponen | Teknologi / Library | Versi | Keterangan |
|---|---|---|---|
| **Language** | Java | 21 (LTS) | Modern Java Features |
| **Framework** | Spring Boot | 4.1.1 | Core Framework |
| **Security** | Spring Security | 7.x | Filter-based Authentication & RBAC |
| **JWT Library** | JJWT (io.jsonwebtoken) | 0.12.5 | RS256 RSA Signature & Verification |
| **Database** | PostgreSQL | 16 | Primary Relational Database |
| **Migration** | Flyway | 10.x | Version-controlled Database Migration |
| **Caching / KV** | Redis | 7.x | Token Blacklist Store (TTL Expiry) |
| **ORM** | Spring Data JPA / Hibernate | 7.4.5 | Persistence Layer (`validate` mode) |
| **Documentation**| Springdoc OpenAPI Starter UI | 2.8.5 | Swagger UI & OpenAPI 3 Specification |
| **Validation** | Jakarta Bean Validation | — | DTO Input Validation |
| **Testing** | JUnit 5, Mockito, MockMvc | — | Automated Unit & Integration Tests |
| **Container** | Docker & Docker Compose | — | Multi-stage build & non-root user |

---

## 🏗️ Arsitektur Sistem

```
┌─────────────────────────────────────────────────────────────────────────┐
│                              Client / SPA                               │
└────────────────────────┬───────────────────────▲────────────────────────┘
                         │ HTTPS                 │
                         ▼                       │ Response
┌────────────────────────────────────────────────┴────────────────────────┐
│                        Spring Boot REST API                             │
│                                                                         │
│  ┌───────────────────────────────────────────────────────────────────┐  │
│  │                     JwtAuthenticationFilter                       │  │
│  │   1. Verifikasi Signature RS256 via RSA Public Key                │  │
│  │   2. Pengecekan Token Blacklist ke Redis (O(1))                   │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │                 Controller Layer (REST & OpenAPI)                 │  │
│  │  - AuthenticationController  - UserController  - DemoController   │  │
│  └───────────────────────────────┬───────────────────────────────────┘  │
│                                  │                                      │
│  ┌───────────────────────────────▼───────────────────────────────────┐  │
│  │                          Service Layer                            │  │
│  │  - JwtService (Sign RS256 w/ Private Key)                         │  │
│  │  - TokenBlacklistService (Redis TTL Storage)                      │  │
│  │  - RefreshTokenService (UUID Rotation & Revocation)               │  │
│  │  - UserService & AuthenticationService                            │  │
│  └───────────────┬───────────────────────────────┬───────────────────┘  │
└──────────────────┼───────────────────────────────┼──────────────────────┘
                   │                               │
                   ▼                               ▼
    ┌─────────────────────────────┐  ┌──────────────────────────────┐
    │       PostgreSQL 16         │  │           Redis 7            │
    │  - Tabel `_user`            │  │  - Key: `blacklist:token:*` │
    │  - Tabel `refresh_tokens`   │  │    (TTL otomatis habis saat  │
    │  - Tabel `flyway_schema_...`│  │     token expired)           │
    └─────────────────────────────┘  └──────────────────────────────┘
```

---

## 📁 Struktur Project

```
baseapp/
├── src/
│   ├── main/
│   │   ├── java/com/alfaizunawebid/baseapp/
│   │   │   ├── config/              # Security, JWT RSA, Redis, & OpenAPI Config
│   │   │   ├── controller/          # REST Endpoints (Auth, User, Demo)
│   │   │   ├── dto/                 # Request & Response DTOs dengan validasi
│   │   │   ├── exception/           # Global Exception Handler (@RestControllerAdvice)
│   │   │   ├── model/               # JPA Entities (_user, refresh_tokens, Role)
│   │   │   ├── repository/          # Spring Data JPA Repositories
│   │   │   └── service/             # Business Logic (Auth, User, JWT, Redis, Refresh)
│   │   └── resources/
│   │       ├── application.yaml     # Konfigurasi dasar & default profile
│   │       ├── application-dev.yaml # Konfigurasi lokal development (Postgres & Redis)
│   │       ├── application-prod.yaml# Konfigurasi production-ready (Environment variables)
│   │       ├── certs/               # RSA 2048-bit PEM Keys (private & public)
│   │       └── db/migration/        # SQL Migration Flyway (V1, V2)
│   └── test/
│       └── java/com/alfaizunawebid/baseapp/
│           ├── controller/          # Integration Test (AuthenticationIntegrationTest)
│           └── service/             # Unit Tests (JwtService, TokenBlacklist, RefreshToken)
├── .dockerignore
├── .env.example                     # Template konfigurasi environment variables
├── docker-compose.yml               # Multi-container orchestration (App, DB, Redis)
├── Dockerfile                       # Multi-stage Docker build (Alpine & non-root)
├── MANUAL_TESTING.md                # Panduan lengkap pengujian manual via cURL
├── postman_collection.json          # Postman Collection v2.1.0 dengan auto-token save
└── pom.xml                          # Maven build dependencies
```

---

## 📡 Ringkasan API Endpoints

### 1. Authentication (`/api/v1/auth`) — Public
| Method | Endpoint | Deskripsi | Status |
|---|---|---|:---:|
| `GET` | `/api/v1/auth/public-key` | Mengambil RSA Public Key (PEM X.509) untuk client | `200 OK` |
| `POST` | `/api/v1/auth/register` | Mendaftarkan user baru & mengembalikan token pair | `200 OK` |
| `POST` | `/api/v1/auth/login` | Login user & mengembalikan Access + Refresh Token | `200 OK` |
| `POST` | `/api/v1/auth/refresh-token` | Rotasi Access Token menggunakan Refresh Token | `200 OK` |
| `POST` | `/api/v1/auth/logout` | Blacklist Access Token ke Redis & Revoke Refresh Token | `200 OK` |

### 2. User Management (`/api/v1/users`) — Protected (Bearer Token)
| Method | Endpoint | Role | Deskripsi |
|---|---|:---:|---|
| `GET` | `/api/v1/users/me` | `USER`, `ADMIN` | Mengambil profil user yang sedang login |
| `PUT` | `/api/v1/users/me` | `USER`, `ADMIN` | Memperbarui nama profil pengguna |
| `PATCH` | `/api/v1/users/me/password` | `USER`, `ADMIN` | Mengubah password akun dengan validasi old password |
| `GET` | `/api/v1/users` | `ADMIN` | Mengambil daftar seluruh user terpaginasi (`?page=0&size=10`) |
| `DELETE` | `/api/v1/users/{id}` | `ADMIN` | Menghapus user berdasarkan ID |

### 3. Demo / Role Check (`/api/v1/demo`) — Protected
| Method | Endpoint | Role | Deskripsi |
|---|---|:---:|---|
| `GET` | `/api/v1/demo/hello` | `USER`, `ADMIN` | Akses authenticated user umum |
| `GET` | `/api/v1/demo/admin` | `ADMIN` | Akses khusus administrator (Role check) |

### 4. API Documentation
| Method | Endpoint | Deskripsi |
|---|---|---|
| `GET` | `/swagger-ui.html` | Swagger UI Interactive Portal (Redirect ke `/swagger-ui/index.html`) |
| `GET` | `/v3/api-docs` | OpenAPI 3.0 JSON Specification |

---

## ⚙️ Variabel Lingkungan (Environment Variables)

Salin template berkas [.env.example](file:///.env.example) menjadi `.env`:

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=baseapp_db
DB_USERNAME=postgres
DB_PASSWORD=your_password_here

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# App Profile & Port
PORT=8080
SPRING_PROFILES_ACTIVE=prod

# JWT (RSA 2048-bit)
RSA_PRIVATE_KEY_LOCATION=classpath:certs/private_key.pem
RSA_PUBLIC_KEY_LOCATION=classpath:certs/public_key.pem
JWT_EXPIRATION=86400000          # 24 jam (ms)
REFRESH_TOKEN_EXPIRATION=604800000 # 7 hari (ms)
```

---

## 🚀 Cara Menjalankan Aplikasi

### Opsi 1: Menggunakan Docker Compose (Direkomendasikan)
Metode tercepat yang secara otomatis menyiapkan PostgreSQL, Redis, dan menjalankan aplikasi:
```bash
# 1. Pastikan Docker Desktop aktif
cp .env.example .env

# 2. Build dan jalankan seluruh container di background
docker compose up -d --build

# 3. Pantau log aplikasi
docker compose logs -f app
```
Aplikasi dapat diakses di: **http://localhost:8080**

### Opsi 2: Menjalankan Secara Lokal (Local Development)
1. **Pastikan PostgreSQL dan Redis lokal aktif**:
   ```bash
   brew services start postgresql@16
   brew services start redis
   ```
2. **Jalankan aplikasi dengan Maven Wrapper**:
   ```bash
   ./mvnw spring-boot:run
   ```

### Opsi 3: Build & Jalankan Executable Production JAR
```bash
# 1. Build JAR produksi (skip tests untuk kecepatan packaging)
./mvnw clean package -DskipTests

# 2. Jalankan dengan profile production
export SPRING_PROFILES_ACTIVE=prod
export DB_PASSWORD=your_postgres_password
java -jar target/baseapp-0.0.1-SNAPSHOT.jar
```

---

## 🧪 Menjalankan Automated Test Suite

Aplikasi dilengkapi dengan **26 skenario pengujian otomatis**:
```bash
# Jalankan seluruh unit test dan integration test
./mvnw test
```

Rincian cakupan test:
- `JwtServiceTest`: 7 skenario (RS256 sign, claims extraction, validity check, expired token, mismatched signature detection).
- `TokenBlacklistServiceTest`: 5 skenario (Redis TTL caching, negative duration guard, O(1) blacklist check).
- `RefreshTokenServiceTest`: 7 skenario (UUID generation, active validation, revoked rejection, expiration deletion).
- `AuthenticationIntegrationTest`: 6 skenario alur lengkap (*Register ➔ Protected ➔ Login ➔ Refresh ➔ Logout ➔ Blacklist Rejection*).

---

## 📮 Panduan Pengujian Manual & Postman

1. **Pengujian Manual via cURL**: Panduan langkah demi langkah beserta contoh request dan response lengkap tersedia di [MANUAL_TESTING.md](file:///MANUAL_TESTING.md).
2. **Postman Collection**: Import berkas [postman_collection.json](file:///postman_collection.json) ke Postman. Koleksi ini dilengkapi fitur **Auto-Save Token** pada request *Register*, *Login*, dan *Refresh Token* sehingga Anda tidak perlu menyalin token secara manual saat pengujian!
3. **Swagger UI**: Buka browser di [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) lalu klik tombol hijau **Authorize 🔓** untuk memasukkan token.

---

## 📜 Lisensi
Proyek ini dilisensikan di bawah lisensi MIT. Bebas digunakan sebagai boilerplate dasar untuk aplikasi komersial maupun pribadi.
