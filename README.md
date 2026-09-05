# 🛡️ Secure Payment Webhook Handler & Transaction State Machine

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-7.x-green.svg)](https://spring.io/projects/spring-security)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration%20Tests-black.svg)](https://testcontainers.com/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-blueviolet.svg)](https://flywaydb.org/)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

An enterprise-grade, production-ready payment webhook service built with **Spring Boot 4 (Java 21)**. It solves real-world e-commerce and fintech security challenges: **payload spoofing**, **timing attacks**, **network-retry duplicate processing**, and **amount tampering fraud**.

> 🇮🇩 *Untuk dokumentasi lengkap dalam Bahasa Indonesia dan studi kasus narasi, silakan baca [README.id.md](README.id.md).*

---

## 🎯 The Real-World Problem & Threat Model

In modern payment architectures (e.g., Midtrans, Xendit, Stripe), payment confirmations are delivered asynchronously via HTTP webhooks. Because webhooks are exposed to the public internet, they are prime targets for malicious exploits and network anomalies:

```
                              THREAT LANDSCAPE
┌────────────────────────┐
│ Malicious Actor        ├──────────► [ 1. Payload Spoofing / Forgery ]
│ (Attacker)             ├──────────► [ 2. Timing Attack on Signatures ]
└────────────────────────┘

┌────────────────────────┐
│ Payment Provider       ├──────────► [ 3. At-Least-Once Delivery / Duplicate Retries ]
│ (Network / Gateway)    ├──────────► [ 4. Out-of-Order Webhook Delivery ]
└────────────────────────┘
```

| Threat / Risk | Impact Without Protection | Engineered Solution |
|---|---|---|
| **1. Payload Spoofing** | Attacker sends forged `status: PAID` to unlock items without paying. | **HMAC-SHA256 Signature Verification** using shared secret key. |
| **2. Timing Attacks** | Attacker measures nano-second string comparison response times to guess valid signatures. | **Constant-Time Comparison** (`MessageDigest.isEqual`). |
| **3. Duplicate Processing** | Gateway retries cause double fulfillment (e.g., wallet credited twice). | **Atomic Idempotency Engine** powered by Redis (`SETNX` + TTL). |
| **4. Amount Tampering** | Attacker pays Rp1.000 for a Rp1.000.000 order. | **Strict Amount Integrity Guard** against order database records. |
| **5. Regressive States** | Delayed `EXPIRED` webhook overwrites an already `PAID` order. | **Rigid Finite State Machine (FSM)**. |
| **6. Dispute & Compliance** | Inability to audit gateway events during customer disputes. | **Raw Payload Audit Trail** in PostgreSQL (`payment_transaction_logs`). |

---

## 🏗️ Architecture & Processing Flow

```
Payment Gateway (Midtrans / Stripe)
       │
       │  POST /api/v1/webhooks/payment
       │  Headers: X-Signature: <hmac_hex>
       ▼
┌─────────────────────────────────────────────────────────────┐
│ 1. Spring Security Gateway Filter                           │
│    - Whitelists webhook endpoint from Bearer JWT filter     │
└──────────────────────────────┬──────────────────────────────┘
                               │
                               ▼
┌─────────────────────────────────────────────────────────────┐
│ 2. HMAC-SHA256 Cryptographic Verification                   │
│    - Calculates HMAC on raw request body                    │
│    - Constant-time verification against timing attacks      │
└──────────────┬───────────────────────────────┬──────────────┘
               │ (Invalid)                     │ (Valid)
               ▼                               ▼
       [ 401 Unauthorized ]    ┌──────────────────────────────────────────────────┐
                               │ 3. Redis Atomic Idempotency Check (SETNX)        │
                               └───────────────┬──────────────────┬───────────────┘
                                               │ (Duplicate)      │ (First-time)
                                               ▼                  ▼
                                     [ 200 OK (Skip DB) ] ┌────────────────────────┐
                                                          │ 4. Order State Machine │
                                                          │    - Amount check      │
                                                          │    - PENDING ➔ PAID    │
                                                          │    - Persist Audit Log │
                                                          └───────────┬────────────┘
                                                                      │
                                                                      ▼
                                                            [ 200 OK Processed ]
```

---

## 🚀 Key Technical Highlights

1. **Memory-Efficient Request Handling:**
   Avoids `ServletInputStream` exhaustion traps by accepting the raw payload as a `String` inside the controller, computing HMAC on raw bytes without double-buffering filter overhead, and cleanly parsing via Jackson `ObjectMapper`.
2. **Timing Attack Resistant:**
   Uses `MessageDigest.isEqual(...)` instead of standard `String.equals(...)`, eliminating cryptographic timing side-channel vulnerabilities.
3. **Atomic Distributed Idempotency:**
   Leverages Redis `setIfAbsent(...)` (`SET key value NX EX ttl`) for atomic, lock-free duplicate request prevention across distributed microservice instances.
4. **Finite State Machine & Fraud Guard:**
   Ensures final transaction states (`PAID`) cannot be regressed by delayed out-of-order webhooks. Automatically flags orders as `FAILED` if payment amounts do not strictly match order totals.
5. **Production Parity with Testcontainers:**
   Comprehensive end-to-end integration tests spin up real **PostgreSQL 16** and **Redis 7** containers in Docker—guaranteeing 100% production parity without relying on in-memory mocks.

---

## 📊 Endpoints Specification

| Method | Endpoint | Access | Purpose |
|---|---|---|---|
| `POST` | `/api/v1/webhooks/payment` | Public (HMAC protected) | Receives payment callbacks from gateway |
| `POST` | `/api/v1/orders` | Public / Demo | Creates a new order (`PENDING`) |
| `GET` | `/api/v1/orders/{orderNumber}/status` | Public / Demo | Checks live order status |
| `POST` | `/api/v1/orders/{orderNumber}/simulate-webhook` | Public / Demo | Generates valid mock payload & HMAC signature |
| `GET` | `/swagger-ui/index.html` | Public | Interactive OpenAPI 3 / Swagger documentation |

---

## 🧪 Testing Suite (36 Tests, 100% Green)

The project is backed by a rigorous test pyramid:
* **Unit Tests (`HmacSignatureValidatorTest`):** Validates signature calculations, tampered signatures, data alterations, and null-safety.
* **Domain Tests (`OrderProcessingServiceTest`):** Verifies state machine transitions, guard clauses, and amount tampering detection.
* **End-to-End Integration Tests (`WebhookIntegrationTest`):** Uses **Testcontainers** to validate real HTTP calls, PostgreSQL persistence, Redis idempotency locking, and HTTP 401 rejections.
* **Authentication Suite:** Full suite of JWT RS256, Refresh Token rotation, and Redis token blacklist tests.

```bash
# Run all 36 unit and integration tests
./mvnw test
```

---

## 🛠️ Quickstart (Local Development)

### 1. Prerequisites
* **Java 21**
* **Docker & Docker Compose**

### 2. Start PostgreSQL & Redis
```bash
docker compose up -d postgres redis
```

### 3. Run the Spring Boot Application
```bash
./mvnw spring-boot:run
```

### 4. Interactive Simulation Flow
```bash
# 1. Create a new order (status: PENDING)
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H "Content-Type: application/json" \
  -d '{"orderNumber": "ORD-DEMO-001", "amount": 250000.00}'

# 2. Generate a valid mock webhook payload & HMAC signature
curl -s -X POST http://localhost:8080/api/v1/orders/ORD-DEMO-001/simulate-webhook

# 3. Send valid webhook (replace <SIGNATURE> and <RAW_PAYLOAD> from step 2)
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: <SIGNATURE>" \
  -d '<RAW_PAYLOAD>'
# Result: HTTP 200 OK -> Order status becomes PAID!

# 4. Test Idempotency (Send the exact same request again)
# Result: HTTP 200 OK with message: "Duplicate notification ignored"

# 5. Test Hacker Protection (Send request with invalid signature)
curl -i -X POST http://localhost:8080/api/v1/webhooks/payment \
  -H "Content-Type: application/json" \
  -H "X-Signature: invalid_fake_signature" \
  -d '{"transaction_id":"TRX-FAKE","order_number":"ORD-DEMO-001","gross_amount":250000.00,"transaction_status":"settlement"}'
# Result: HTTP 401 Unauthorized!
```
