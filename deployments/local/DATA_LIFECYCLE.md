# Local Stack — Data Lifecycle & Recovery

Dokumen ini menjelaskan cara data dikelola di local stack (`deployments/local/`)
sesuai checklist **Fase 6 — Local Docker Compose dan Data Lifecycle**.

Ringkasan cepat:

| Topik | Lokasi |
|---|---|
| Compose file | `deployments/local/docker-compose.yml` |
| Env (dev) | `deployments/local/docker.env` (+ `.example`) |
| Logs | `deployments/local/logs/*.log` (di-scrape Promtail → Loki) |
| Backup DB | `./scripts/backup.sh` → `deployments/local/backups/` |
| Restore DB | `./scripts/restore.sh` |
| Smoke test | `./scripts/smoke-test.sh` |
| Migrations | Tidak ada — MongoDB schemaless; constraint unik via index programatik per service |

---

## 1. Topologi & penyimpanan data

State yang bertahan (persist) didefinisikan sebagai named volumes di compose.
Terdapat **satu instance MongoDB per bounded context** (tanpa proxy):

| Volume | Data |
|---|---|
| `mongo_identity_data` | MongoDB identity (`ec_identity`) |
| `mongo_merchant_data` | MongoDB merchant (`ec_merchant`) |
| `mongo_catalog_data` | MongoDB catalog (`ec_catalog`) |
| `mongo_sales_data` | MongoDB sales (`ec_sales`) |
| `mongo_experience_data` | MongoDB experience (`ec_experience`) |
| `mongo_email_data` | MongoDB email (`ec_email`) |
| `kafka_ecommerce_data` | Kafka `/bitnami/kafka` |
| `redis_ecommerce_data` | Redis `/data` |
| `loki_ecommerce_data` | Log Loki |
| `grafana_ecommerce-storage` | Dashboard Grafana |

Volume ini **tidak dihapus** oleh `docker compose down` (hanya `down -v` yang
menghapusnya). Selama diagnosa normal gunakan `docker compose down` (tanpa `-v`).

```bash
# Hentikan stack, data tetap aman
docker compose -f deployments/local/docker-compose.yml down

# Mulai ulang stack dari data yang sama
docker compose -f deployments/local/docker-compose.yml up -d
```

> ⚠️ **Hati-hati:** `docker compose down -v` menghapus semua volume. Hanya
> gunakan bila memang ingin reset total (lihat §6).

---

## 2. Backup MongoDB

```bash
# Manual: satu archive per context ke deployments/local/backups/
# (mongo_identity_<ts>.archive.gz … mongo_email_<ts>.archive.gz)
./deployments/local/scripts/backup.sh

# Dengan retention 14 hari
./deployments/local/scripts/backup.sh 14
```

Yang dilakukan script:
1. Memastikan keenam container `mongo_*` berjalan.
2. `mongodump --db <ec_*> --archive --gzip` per context → `backups/`.
3. Menghapus backup lebih tua dari retention (default 7 hari).

Archive `mongodump` **portable** sehingga bisa di-restore ke environment sekali
pakai (disposable) dengan `mongorestore`.

---

## 3. Restore MongoDB

```bash
# Restore dengan konfirmasi (context diturunkan dari nama file)
./deployments/local/scripts/restore.sh backups/mongo_sales_20260101_000000.archive.gz

# Restore tanpa prompt (untuk CI/script)
./deployments/local/scripts/restore.sh --yes backups/mongo_sales_20260101_000000.archive.gz
```

Yang dilakukan script:
1. Memvalidasi file backup (gzip + ukuran) dan container `mongo_*` yang sesuai.
2. Menjalankan `mongorestore --archive --gzip --drop` ke context target.

> ⚠️ Restore bersifat **destruktif** — collection di database target di-drop dan
> diganti isi archive. Selalu pastikan backup terbaru tersedia sebelum restore.

**Restore ke environment disposable:** archive bersifat self-contained, cukup
restore terhadap instance Mongo baru di env lain — tidak perlu user/role khusus.

---

## 4. Schema & constraint

Tidak ada migration SQL. MongoDB schemaless, jadi:
- Struktur dokumen mengikuti entity Java (`@MongoEntity(collection=...)`).
- Constraint unik (mis. `slugProduct`, `roleName`) ditegakkan lewat **index
  programatik** yang dibuat tiap service saat startup — bukan oleh DB.
- Verifikasi index, contoh catalog:
  ```bash
  mongosh --port 27019 --eval 'db.getSiblingDB("ec_catalog").products.getIndexes()'
  ```
- Tidak ada langkah migrate/rollback terpisah. Bila perlu mengubah bentuk data,
  lakukan lewat aplikasi atau skrip one-off (bukan Goose/Flyway).

---

## 5. Verifikasi stack (cold start & restart)

Gunakan smoke test setelah **cold start** (volume baru) dan setelah **restart**
(data lama) untuk memastikan state bertahan dan endpoint sehat.

```bash
# 1. Build image + start stack
just build-up

# 2. Tunggu semua service healthy, lalu smoke test
./deployments/local/scripts/smoke-test.sh

# 3. Restart (data harus bertahan — tanpa -v)
docker compose -f deployments/local/docker-compose.yml restart apigateway
./deployments/local/scripts/smoke-test.sh
```

Smoke test memverifikasi (bukan sekadar container "running"):
- `GET /health` → 200
- `GET /ready` → 200 (readiness cek Redis)
- `POST /api/auth/register` + `POST /api/auth/login` → token
- `GET /api/auth/me` (JWT) → 200
- `GET /api/product-query` (JWT) → 200

---

## 6. Reset total (opsional)

Gunakan hanya bila ingin membuang semua state:

```bash
# Hentikan + hapus volume (termasuk keenam mongo_*_data)
docker compose -f deployments/local/docker-compose.yml down -v

# Jalankan seeder dari awal (tidak ada langkah migrate — Mongo schemaless)
just seeder

# Verifikasi
./deployments/local/scripts/smoke-test.sh
```

Backup terlebih dahulu (§2) bila masih ada data yang dibutuhkan.

---

## 7. Retention & diagnosa non-destruktif

| Aset | Retention | Catatan |
|---|---|---|
| DB backup (`backups/`) | 7 hari (configurable) | Script backup mem-prune otomatis |
| Log service (`logs/`) | sesuai kebijakan lokal | Jangan dihapus saat diagnosa; di-scrape Promtail |
| Loki data (volume) | sesuai konfigurasi `loki-config.yaml` | Retention default local |
| Mongo/Kafka/Redis volume | selama stack ada | Dihapus hanya via `down -v` |

Aturan diagnosa:

- Jangan hapus volume saat menyelidiki masalah — gunakan `logs` + `/health` +
  `/ready` + Prometheus/Grafana.
- Untuk "service tidak sehat" cek **readiness** (`/ready`), bukan hanya status
  container.
- Untuk masalah data korup: backup dulu, baru reset service tertentu (bukan
  seluruh volume).

---

## 8. Secret dev vs produksi

- `deployments/local/docker.env` — rahasia development (sudah ada + `.example`).
- Jangan pernah memakai nilai dev di produksi.
- `SECRET_KEY` dev (dipakai JWT gateway) berbeda dari secret produksi.
- SMTP credential dev (Mailtrap dsb.) terpisah dari SMTP produksi.
