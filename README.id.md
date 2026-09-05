# 🛡️ Secure Payment Webhook Handler & Transaction State Machine
## Solusi Proteksi Webhook Pembayaran & Pencegahan Fraud Transaksi

[![Java](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.1-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring%20Security-7.x-green.svg)](https://spring.io/projects/spring-security)
[![Redis](https://img.shields.io/badge/Redis-7.x-red.svg)](https://redis.io/)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Testcontainers](https://img.shields.io/badge/Testcontainers-Integration%20Tests-black.svg)](https://testcontainers.com/)
[![Flyway](https://img.shields.io/badge/Flyway-Migrations-blueviolet.svg)](https://flywaydb.org/)

Sistem backend *production-ready* berbasis **Java 21 & Spring Boot 4** yang dirancang untuk menangani tantangan keamanan dan arsitektur nyata pada integrasi Payment Gateway (seperti Midtrans, Xendit, DOKU, atau Stripe): **pemalsuan request (spoofing)**, **serangan timing attack**, **pemrosesan ganda akibat network retry (double fulfillment)**, dan **pemalsuan nominal transfer (amount tampering fraud)**.

> 🇬🇧 *For the English documentation and global technical highlights, please refer to [README.md](README.md).*

---

## 📖 Narasi Masalah, Solusi, dan Hasil (Case Study)

### 1. Masalah di Dunia Nyata
Dalam arsitektur e-commerce dan marketplace modern, konfirmasi pembayaran seperti **Virtual Account, QRIS, dan Gerai Ritel** berjalan secara asinkron. Begitu pembeli menyelesaikan transaksi di aplikasi perbankan, server Payment Gateway mengirimkan notifikasi HTTP POST (Webhook) ke backend aplikasi kita.

Namun karena endpoint webhook ini terbuka ke publik, muncul beberapa celah keamanan dan teknis serius:
1. **Pemalsuan Payload (Payload Spoofing):** Pihak tak bertanggung jawab bisa mengirim JSON palsu dengan status `settlement` / `PAID` untuk mendapatkan barang tanpa pernah membayar.
2. **Timing Attack:** Perbandingan signature string biasa (`String.equals()`) membocorkan selisih waktu eksekusi nanodetik yang bisa dipakai penyerang untuk menebak signature yang sah.
3. **Pemrosesan Ganda (Double-Processing):** Gateway menggunakan mekanisme *At-Least-Once Delivery*. Gangguan jaringan sesaat membuat gateway mengirim ulang (retry) notifikasi 2x hingga 5x. Tanpa kontrol idempoten, saldo atau barang pembeli bisa dikirim berulang kali.
4. **Pemalsuan Nominal (Amount Tampering):** Penyerang memanipulasi nominal sehingga hanya membayar Rp1.000 untuk tagihan sebesar Rp10.000.000.
5. **Status Mundur (Regressive State):** Notifikasi `EXPIRED` yang datang terlambat akibat lag jaringan bisa membatalkan pesanan yang sebelumnya sudah `PAID`.

---

### 2. Solusi Arsitektur
Aplikasi ini menerapkan 5 lapis perlindungan:
* 🔐 **Verifikasi Kriptografi HMAC-SHA256:** Memvalidasi integritas data mentah menggunakan secret key rahasia bersama. Perbandingan signature menggunakan `MessageDigest.isEqual(...)` agar tahan terhadap *Timing Attack*.
* ⚡ **Idempotency Engine Atomic dengan Redis:** Menggunakan perintah atomic `SETNX` (Set if Not Exists) dengan TTL 24 jam. Request kedua dan seterusnya yang memiliki `transaction_id` sama otomatis diabaikan tanpa memutasi database.
* 🚦 **Finite State Machine & Fraud Guard:** Menjamin transisi status pesanan (`PENDING ➔ PAID`). Order yang sudah lunas tidak dapat mundur statusnya. Nominal pembayaran selalu dicocokkan secara presisi dengan tagihan database.
* 📜 **Audit Trail Lengkap:** Seluruh payload JSON mentah disimpan di tabel `payment_transaction_logs` untuk kebutuhan rekonsiliasi finansial dan investigasi komplain.
* 🧪 **Integration Test dengan Testcontainers:** Menguji keseluruhan alur secara otomatis menggunakan PostgreSQL 16 dan Redis 7 asli di dalam kontainer Docker sementara (*ephemeral*).

---

### 3. Hasil Pengujian
* **36 Test Cases Lulus 100% (Green Build):** Meliputi Unit Test logika kriptografi, State Machine domain test, dan Testcontainers End-to-End.
* **Zero Double Fulfillment:** Replay notifikasi otomatis dari gateway berhasil dicegat dalam hitungan milidetik.
* **Zero Fraud Risk:** Payload yang diubah bahkan 1 karakter atau nominal yang tidak cocok otomatis ditolak seketika (HTTP 401 / Status FAILED).

---

## 🏗️ Alur Eksekusi Webhook

```
Payment Gateway (Midtrans / Stripe)
       │  POST /api/v1/webhooks/payment (Header: X-Signature)
       ▼
[ Spring Security Whitelist ]
       │
       ▼
[ Verifikasi HMAC-SHA256 ] ──(Invalid)──▶ 401 Unauthorized
       │ Valid
       ▼
[ Redis Idempotency Lock ] ──(Duplikat)──▶ 200 OK (Abaikan Proses)
       │ Unik / Baru
       ▼
[ Order State Machine ]
  ├── Cek nominal (Mencegah Fraud)
  ├── Update status (PENDING ➔ PAID)
  └── Catat Audit Log ke PostgreSQL
       │
       ▼
 200 OK (Selesai)
```

---

## 📊 Daftar Endpoint Utama

| Method | Endpoint | Keterangan |
|---|---|---|
| `POST` | `/api/v1/webhooks/payment` | Menerima notifikasi callback dari payment provider (HMAC Protected) |
| `POST` | `/api/v1/orders` | Membuat pesanan baru (`PENDING`) |
| `GET` | `/api/v1/orders/{orderNumber}/status` | Mengecek status pesanan terkini |
| `POST` | `/api/v1/orders/{orderNumber}/simulate-webhook` | Menghasilkan mock payload & signature valid untuk pengujian Postman |
| `GET` | `/swagger-ui/index.html` | Dokumentasi Swagger / OpenAPI 3 interaktif |

---

## 🛠️ Panduan Menjalankan di Lokal

### 1. Prasyarat
* Java 21
* Docker Desktop

### 2. Jalankan PostgreSQL & Redis
```bash
docker compose up -d postgres redis
```

### 3. Jalankan Aplikasi Spring Boot
```bash
./mvnw spring-boot:run
```

### 4. Menjalankan Rangkaian Test Otomatis
```bash
./mvnw test
```

---

## 👨‍💻 Profil & Relevansi Industri
Proyek ini dibuat oleh **Alfaizuna** sebagai portofolio profesional untuk menunjukkan keahlian di bidang:
* Keamanan Finansial & API Payment Gateway (HMAC, Idempotency, Fraud Prevention)
* Java 21 & Spring Boot 4 Enterprise Architecture
* Distributed Caching & Atomicity dengan Redis
* Database Integrity & Flyway Migration dengan PostgreSQL
* Automated Testing Standar Global (JUnit 5, Mockito, Testcontainers)
