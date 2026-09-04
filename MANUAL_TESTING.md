# Panduan Pengujian Manual API (Manual Testing Guide)

Dokumen ini berisi panduan langkah demi langkah untuk melakukan pengujian manual pada REST API **Baseapp** (Spring Boot 4, JWT RS256 Asymmetric, Redis Blacklist, Refresh Token Rotation, dan Flyway).

---

## 🛠️ Prasyarat Sebelum Pengujian

Pastikan service pendukung telah aktif:
1. **PostgreSQL**: `baseapp_db` aktif di port `5432`
2. **Redis**: Berjalan di port `6379` (`brew services start redis` atau `redis-server`)
3. **Aplikasi Spring Boot**: Berjalan di port `8080` (`./mvnw spring-boot:run`)

Variabel dasar yang digunakan dalam panduan ini:
- **Base URL**: `http://localhost:8080`
- **Email Uji**: `tester@example.com`
- **Password Awal**: `Password123!`
- **Password Baru**: `NewPassword456!`

---

## 📋 Alur Pengujian Manual (Step-by-Step)

```
[1. Get Public Key] ──► [2. Register] ──► [3. Get Profile] ──► [4. Update Profile]
         │
         ▼
[5. Change Password] ──► [6. Login Baru] ──► [7. Refresh Token] ──► [8. Logout]
         │
         ▼
[9. Cek Blacklist (Harus 403)] ──► [10. Cek Revoked Refresh (Harus 400)] ──► [11. Swagger & OpenAPI]
```

---

### Step 1: Mengambil RSA Public Key (Public Endpoint)
Mengambil kunci publik RSA format PEM untuk verifikasi signature JWT di sisi client atau resource server.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/auth/public-key`
- **Headers**: *(Tidak ada)*
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/auth/public-key
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "publicKey": "-----BEGIN PUBLIC KEY-----\nMIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEA...\n-----END PUBLIC KEY-----"
  }
  ```

---

### Step 2: Registrasi User Baru
Mendaftarkan akun baru ke PostgreSQL. Sistem akan otomatis mem-hash password dengan BCrypt dan mengembalikan pasangan Access Token (RS256) serta Refresh Token.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/register`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "name": "Tester Manual",
    "email": "tester@example.com",
    "password": "Password123!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/register \
    -H "Content-Type: application/json" \
    -d '{
      "name": "Tester Manual",
      "email": "tester@example.com",
      "password": "Password123!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i...",
    "refreshToken": "4a73752e-bb91-4cf1-8c46-953ebbb57fcf"
  }
  ```
> 💡 **Catatan**: Simpan nilai `token` (Access Token) dan `refreshToken` untuk langkah berikutnya!

---

### Step 3: Verifikasi Algoritma Token (RS256)
Memeriksa bagian Header dari Access Token yang dihasilkan pada Step 2 untuk memastikan algoritma bertipe Asymmetric RSA.

- **cURL / Bash Command**:
  ```bash
  ACCESS_TOKEN="<MASUKKAN_ACCESS_TOKEN>"
  echo "$ACCESS_TOKEN" | cut -d '.' -f 1 | base64 --decode
  ```
- **Ekspektasi Output**:
  ```json
  {"alg":"RS256"}
  ```

---

### Step 4: Akses Profile Sendiri (Protected Endpoint)
Memanggil endpoint profil user yang dilindungi oleh JWT Security Filter & Redis Blacklist Validator.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**: 
  - `Authorization: Bearer <ACCESS_TOKEN>`
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <ACCESS_TOKEN>"
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "id": 1,
    "name": "Tester Manual",
    "email": "tester@example.com",
    "role": "USER"
  }
  ```

---

### Step 5: Update Profil User
Memperbarui nama pengguna yang sedang login.

- **Method**: `PUT`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "name": "Tester Manual Updated"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X PUT http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "name": "Tester Manual Updated"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "id": 1,
    "name": "Tester Manual Updated",
    "email": "tester@example.com",
    "role": "USER"
  }
  ```

---

### Step 6: Ganti Password
Mengubah password user saat ini. Endpoint ini memverifikasi kesesuaian password lama (`currentPassword`) sebelum menyimpan hash BCrypt yang baru.

- **Method**: `PATCH`
- **URL**: `http://localhost:8080/api/v1/users/me/password`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "currentPassword": "Password123!",
    "newPassword": "NewPassword456!",
    "confirmationPassword": "NewPassword456!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X PATCH http://localhost:8080/api/v1/users/me/password \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "currentPassword": "Password123!",
      "newPassword": "NewPassword456!",
      "confirmationPassword": "NewPassword456!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "message": "Password changed successfully"
  }
  ```

---

### Step 7: Login dengan Password Baru
Melakukan autentikasi menggunakan password baru untuk memverifikasi bahwa perubahan password berhasil.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/login`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "email": "tester@example.com",
    "password": "NewPassword456!"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{
      "email": "tester@example.com",
      "password": "NewPassword456!"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i...",
    "refreshToken": "8b919283-9ef2-4bc0-9345-21d491ea944f"
  }
  ```
> 💡 **Catatan**: Gunakan pasangan token baru dari respons login ini untuk langkah selanjutnya!

---

### Step 8: Refresh Access Token (Token Rotation)
Menghasilkan Access Token baru menggunakan Refresh Token yang aktif tanpa perlu login ulang.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/refresh-token`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/refresh-token \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "token": "eyJhbGciOiJSUzI1NiJ9.eyJzdWIiOiJ0ZXN0ZXJAZXhhbXBsZS5jb20i..."
  }
  ```

---

### Step 9: Logout User (Redis Blacklist & Refresh Token Revoke)
Melakukan proses logout ganda:
1. **Access Token** dimasukkan ke Redis dengan key `blacklist:token:<token>` dan TTL sisa waktu kadaluarsa.
2. **Refresh Token** di database di-set `revoked = true`.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/logout`
- **Headers**:
  - `Authorization: Bearer <ACCESS_TOKEN>`
  - `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/logout \
    -H "Authorization: Bearer <ACCESS_TOKEN>" \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<MASUKKAN_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `200 OK`
- **Contoh Response**:
  ```json
  {
    "message": "Logged out successfully"
  }
  ```

---

### Step 10: Pengujian Keamanan 1 — Akses dengan Token yang Di-Blacklist (Harus Ditolak)
Mencoba memanggil kembali `/users/me` dengan Access Token yang baru saja di-logout. Filter keamanan harus memeriksa Redis (kecepatan O(1)) dan menolaknya.

- **Method**: `GET`
- **URL**: `http://localhost:8080/api/v1/users/me`
- **Headers**:
  - `Authorization: Bearer <LOGGED_OUT_ACCESS_TOKEN>`
- **cURL Command**:
  ```bash
  curl -i -X GET http://localhost:8080/api/v1/users/me \
    -H "Authorization: Bearer <LOGGED_OUT_ACCESS_TOKEN>"
  ```
- **Ekspektasi HTTP Status**: `403 Forbidden`
- **Alasan**: Token terdaftar di Redis Blacklist (`isTokenBlacklisted == true`).

---

### Step 11: Pengujian Keamanan 2 — Refresh dengan Token yang Telah Di-Revoke (Harus Ditolak)
Mencoba meminta Access Token baru menggunakan Refresh Token yang sudah di-revoke pada saat logout.

- **Method**: `POST`
- **URL**: `http://localhost:8080/api/v1/auth/refresh-token`
- **Headers**: `Content-Type: application/json`
- **Request Body**:
  ```json
  {
    "refreshToken": "<REVOKED_REFRESH_TOKEN>"
  }
  ```
- **cURL Command**:
  ```bash
  curl -i -X POST http://localhost:8080/api/v1/auth/refresh-token \
    -H "Content-Type: application/json" \
    -d '{
      "refreshToken": "<REVOKED_REFRESH_TOKEN>"
    }'
  ```
- **Ekspektasi HTTP Status**: `400 Bad Request`
- **Alasan**: Di PostgreSQL, kolom `revoked = true`, sehingga `verifyExpiration()` melempar `IllegalArgumentException`.

---

### Step 12: Verifikasi OpenAPI Docs & Swagger UI Portal

1. **Akses OpenAPI 3 JSON**:
   ```bash
   curl -i -X GET http://localhost:8080/v3/api-docs
   ```
   - **Ekspektasi**: `200 OK` berisi JSON OpenAPI 3 dengan metadata `Baseapp REST API`.

2. **Akses Swagger UI**:
   Buka browser di:
   - [http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html) (redirect ke `/swagger-ui/index.html`)
   - Klik tombol **Authorize 🔓** untuk memasukkan JWT Bearer Token.

---

## 🔍 Verifikasi Melalui Database & Redis CLI

### 1. Periksa Data di PostgreSQL
```bash
psql -U postgres -d baseapp_db
```
Query yang berguna:
```sql
-- Lihat daftar user terdaftar
SELECT id, name, email, role FROM _user;

-- Lihat status refresh token dan flag revoked
SELECT id, token, user_id, expiry_date, revoked FROM refresh_tokens;

-- Lihat riwayat migrasi Flyway
SELECT installed_rank, version, description, success FROM flyway_schema_history;
```

### 2. Periksa Token Blacklist di Redis
```bash
redis-cli
```
Perintah redis-cli:
```text
# Lihat semua token yang sedang di-blacklist
KEYS "blacklist:token:*"

# Cek sisa TTL (dalam detik) dari token tertentu
TTL "blacklist:token:<access_token>"
```
