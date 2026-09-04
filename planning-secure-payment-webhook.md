# Planning: Secure Payment Webhook Handler

Portfolio project untuk freelance (target lokal & internasional), dibangun di atas starter `java-springboot-jwt`.

---

## 1. Tujuan Project

- Menajamkan skill auth/security di Java Spring Boot yang sudah dimiliki (5 tahun pengalaman)
- Portfolio yang menunjukkan pemahaman real-world security issue (fraud prevention di payment webhook), terhubung ke pengalaman marketplace microservices di Kimia Farma
- Modal untuk apply freelance di Upwork/Toptal (internasional) dan Sribulancer/Fastwork (lokal)

## 2. Narasi Masalah-Solusi-Hasil (untuk README & case study)

**Masalah:** Marketplace/e-commerce sering menerima payment callback (webhook) tanpa verifikasi yang memadai, sehingga rentan terhadap pemalsuan request dan double-processing transaksi.

**Solusi:** Membangun webhook handler dengan verifikasi HMAC signature, idempotency key untuk mencegah pemrosesan ganda, dan integration test menggunakan Testcontainers.

**Hasil:** Sistem yang tahan terhadap request palsu dan retry duplikat dari payment provider, siap dipakai sebagai referensi implementasi aman untuk klien.

## 3. Starter Project

Menggunakan [java-springboot-jwt](https://github.com/alfaizuna/java-springboot-jwt) sebagai base.

**Dipakai langsung:**
- Struktur package: `config/`, `controller/`, `service/`, `repository/`, `dto/`
- `SecurityConfiguration.java` (tambah whitelist untuk route webhook)
- `application.yaml` (datasource & env variable pattern)
- `pom.xml` base (Spring Boot 4.1.1, Java 21)

**Ditambahkan:**
- `spring-boot-starter-data-redis`
- HMAC verification via `javax.crypto.Mac` (built-in, tanpa library tambahan)
- Testcontainers (Postgres + Redis) untuk integration test

**Disesuaikan:**
- `JwtAuthenticationFilter` harus **exclude** route `/webhooks/payment` — payment provider kirim HMAC signature, bukan JWT

## 4. Arsitektur Alur Webhook

```
Payment Provider
      │  POST /webhooks/payment + signature
      ▼
Verify HMAC signature ──(invalid)──▶ 401 Reject
      │ valid
      ▼
Check idempotency key ──(duplicate)──▶ 200 Skip processing
      │ new
      ▼
Process & update order
      │
      ▼
Return 200 OK
```

## 5. Endpoint

| Endpoint | Fungsi |
|---|---|
| `POST /webhooks/payment` | Terima callback dari payment provider (mock Midtrans/Xendit) |
| `POST /orders/{id}/simulate-payment` | Trigger manual untuk testing tanpa payment gateway asli |
| `GET /orders/{id}/status` | Cek status order setelah webhook diproses |

## 6. Struktur Folder Tambahan

```
payment-webhook-service/
├── controller/WebhookController.java
├── security/HmacSignatureValidator.java
├── service/IdempotencyService.java
├── service/OrderProcessingService.java
├── dto/WebhookPayload.java
├── config/WebhookSecurityConfig.java
└── exception/InvalidSignatureException.java
```

## 7. Tech Stack

- Spring Boot 4.1.1, Java 21
- Spring Security 7 (sudah ada dari starter)
- Redis — idempotency key store
- PostgreSQL — order & transaction log
- Testcontainers — integration test Redis & Postgres
- Docker Compose — local dev environment

## 8. Rencana Eksekusi

| # | Langkah | Catatan |
|---|---|---|
| 1 | Setup project skeleton | `docker-compose.yml` untuk Postgres + Redis, agar reviewer bisa langsung `docker-compose up` |
| 2 | Bangun core webhook flow | Endpoint, HMAC validator, idempotency service, order processing |
| 3 | Tulis test (unit + integration) | Testcontainers, bukan full mock — jadi sinyal kredibilitas untuk klien internasional |
| 4 | Deploy live demo | Railway/Render (free tier), sediakan Postman collection atau Swagger docs |
| 5 | Tulis README bilingual | Utama Bahasa Inggris (untuk global), README.id.md untuk platform lokal |
| 6 | Bikin case study terpisah | Versi Inggris di Dev.to/Medium, versi Indonesia di LinkedIn post |
| 7 | Update profil freelance | Upwork/Toptal (EN), Sribulancer/Fastwork (ID), sebut project di proposal relevan |

**Estimasi waktu:** 1,5–2 minggu di sela kerja (step 1–3 paling menyita waktu).

## 9. Catatan Khusus Target Internasional vs Lokal

- README Bahasa Inggris **wajib** untuk repo publik — reviewer Upwork/Toptal menilai dari GitHub langsung
- Live demo lebih berpengaruh untuk klien luar negeri (mereka jarang mau clone & setup manual)
- Integration test dengan Testcontainers adalah pembeda kelas yang sering dicari reviewer teknis internasional
- Klien lokal cenderung lebih percaya dari testimonial/portfolio langsung dibanding detail teknis repo
