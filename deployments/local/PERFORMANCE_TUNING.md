# Performance Tuning & Release Notes (Milestone E)

Dokumen ini merangkum hasil **Milestone E — Performance & Production Readiness**:
baseline load test, panduan tuning, dan release notes. Berlaku untuk stack lokal
(`deployments/local/`) yang berjalan via docker compose.

---

## 1. Baseline (diukur 2026-08-07)

Semua pengukuran diambil dari mesin dev (5.5 GiB RAM, 8 CPU, Docker compose,
stack lengkap 20 service + infra). Alat: `deployments/local/scripts/load-test.sh`
(bash + curl, tanpa dependency tambahan).

| Endpoint | Req | Worker | Throughput | p50 | p95 | p99 | Fail |
|---|---|---|---|---|---|---|---|
| `GET /health` | 100 | 10 | 341 req/s | 23.1 ms | 34.6 ms | 39.7 ms | 0 |

### Catatan baseline
- `/health` hanya memvalidasi gateway (tanpa dependency chain) — ini *upper bound*
  throughput gateway.
- Endpoint dengan chain gRPC (mis. `/api/auth/login` → auth → user/role) akan jauh
  lebih lambat dan lebih peka terhadap pool/koneksi; ukur terpisah bila perlu.
- Untuk benchmark realistis di CI/hardware dedicated, gunakan k6/hey/wrk (tidak
  ditambahkan ke repo agar bebas dependency).

### Cara mengulang
```bash
just up            # atau docker compose -f deployments/local/docker-compose.yml up -d
just smoke         # pastikan 6/6 PASS dulu
bash deployments/local/scripts/load-test.sh http://localhost:5000 200 10
ENDPOINT=/api/product-query bash deployments/local/scripts/load-test.sh
```

---

## 2. Temuan & perbaikan selama validasi (Fase 7–8)

Validasi nyata (minikube → dibatalkan karena hardware, lanjut docker compose)
menemukan beberapa bug yang **tidak terlihat oleh render/config static**:

### 2.1 PostgreSQL connection exhaustion → tidak lagi berlaku
- Sebelum migrasi: semua service `exit 2` dengan `FATAL: sorry, too many clients already`
  karena pool per service (`DB_MIN_IDLE_CONNS=50`) melampaui `max_connections`.
- **Setelah migrasi MongoDB**: satu instance Mongo per bounded context, tanpa
  proxy (PgBouncer dihapus). Default `maxIncomingConnections` Mongo = 65536 dan
  pool per client dikelola driver (`quarkus.mongodb.max-pool-size`, default 100),
  jadi pola kelelahan koneksi PostgreSQL tidak berulang.
- **Tuning**: turunkan `quarkus.mongodb.max-pool-size` per service bila jumlah
  koneksi ke satu instance mendekati batas (6 instance, 20 service).

### 2.2 Migrate service tidak jalan → dihapus
- Sebelum migrasi: tabel tidak pernah dibuat karena service `migrate` (Goose)
  tidak meneruskan argumen / tidak meng-`COPY` direktori `migrations/`.
- **Setelah migrasi MongoDB**: modul `db-migration` dan service `migrate`
  dihapus. Skema kini implisit (schemaless) — constraint unik ditegakkan lewat
  index programatik yang dibuat tiap service saat startup. Tidak ada langkah
  migrasi SQL yang bisa gagal.

### 2.3 Kafka image `bitnami/*:latest` tidak tersedia
- `bitnami/kafka:latest` dan `bitnami/zookeeper:latest` tidak ada manifest di
  registry (validasi nyata: pull gagal). Kafka diganti ke `apache/kafka:latest`
  dengan env `KAFKA_*` (KRaft), zookeeper dihapus (tidak dibutuhkan KRaft).
- Volume kafka perlu `chown 1000:1000` (image berjalan sebagai `appuser`):
  ```bash
  docker run --rm -v local_kafka_ecommerce_data:/data alpine chown -R 1000:1000 /data
  ```

### 2.4 Apigateway: HTTP server tidak pernah start
- Gejala: `RunClient()` di `service/apigateway/apps/client.go` hanya memanggil
  `NewClient()` — `client.Run()` (yang menjalankan `c.Echo.Start`) tidak pernah
  dipanggil, sehingga port 5000 tidak pernah bind meski log sukses.
- Fix: `RunClient()` kini memulai `client.Run()` di goroutine.

### 2.5 Apigateway: `Cache: nil` → panic di `/api/auth/login`
- Gejala: login panic `nil pointer dereference` di handler/auth handler.go:150
  (`h.cache.GetCachedLogin`).
- Akar: `handler.NewHandler` meng-pass `Cache: nil` dengan TODO.
- Fix: `Cache: auth_cache.NewMencache(deps.Cache)` di `service/apigateway/handler/handler.go`.

### 2.6 Apigateway: GRPC service address selalu default (salah)
- Gejala: `/api/product-query` 503; gateway dial `product:50064` padahal service
  listen `50058`.
- Akar: `loadServiceAddresses()` memakai `v.SetEnvPrefix("grpc")` + replacer,
  tetapi `dotenv.Viper()` sudah memanggil `AutomaticEnv()` tanpa prefix lebih
  dulu → `SetEnvPrefix` tidak berpengaruh → env `GRPC_*_ADDR` tidak pernah
  terbaca → selalu fallback ke default hardcoded yang salah.
- Fix: baca `GRPC_<NAME>_ADDR` secara eksplisit via `v.GetString("GRPC_"+name+"_ADDR")`
  dengan fallback default yang sudah dikoreksi (product:50058, user:50053, dst).

---

## 3. Panduan tuning

### 3.1 MongoDB
| Parameter | Dev | Produksi (estimasi) |
|---|---|---|
| `maxIncomingConnections` | 65536 (default) | default cukup untuk 6 instance |
| WiredTiger cache | 50% RAM (default) | 50% RAM container |
| `quarkus.mongodb.max-pool-size` (app) | 100 (default) | 50–100 per service |
| `quarkus.mongodb.uuid-representation` | `standard` | `standard` (wajib) |

> Catatan: PgBouncer dihapus — MongoDB membuka koneksi langsung per instance.
> Jangan menyalakan pool besar-besaran; sesuaikan dengan jumlah koneksi unik ke
> satu instance.

### 3.2 Redis
- Per-service DB (`REDIS_DB_*`) sudah dipisah — jangan digabung di produksi
  tanpa Redis Cluster.
- Healthcheck pakai `redis-cli -a dragon_knight ping` (requirepass).

### 3.3 Kafka
- KRaft (tanpa zookeeper), `apache/kafka:latest`.
- Volume data harus dimiliki `1000:1000`.
- Partisi/topic default OK untuk dev; produksi perlu `replication.factor=3`.

### 3.4 gRPC gateway
- Keepalive client sudah dikonfigurasi (`PermitWithoutStream: true`).
- Trace context dipropagasi lintas gRPC (OTel).
- Rate limit `/api/auth/*` = 10 rps/burst (brute-force guard).

---

## 4. Release notes (ringkasan Fase 7–8)

### Fase 7 — Kubernetes Readiness
- `kustomization.yaml` (baru), render 116 resource, DNS-1123 bersih.
- Fix DNS underscore → dash (7 service).
- Probes 34/34 deployment; Ingress; NetworkPolicy; (migrate Job dihapus pada
  migrasi MongoDB — tidak ada langkah migrasi SQL).
- Email-hpa.yaml diperbaiki (salinan category-hpa).
- **Residual**: validasi server-side (apply/rollout/smoke di cluster nyata)
  tidak dieksekusi penuh — minikube dibatalkan karena hardware (5.5 GiB RAM).
  Sebagai gantinya, validasi nyata dilakukan di docker compose (lihat §2).

### Fase 8 — Backlog Error Handling + Validasi Nyata + Milestone E
- Backlog: `InvalidAccessToken` → `ErrUnauthorized`; round-trip test 429/503;
  test duplicate register (auth service).
- Compose stack: down stack asing, fix kafka/migrate/pool/env, smoke 6/6 PASS.
- Milestone E: secret audit (`secrets.yaml` di-untrack + template example),
  baseline load test (341 req/s), tuning guide ini.

---

## 5. Status & residual jujur
- [x] Smoke test 6/6 PASS (health, ready, register, login, me, product-query).
- [x] Baseline load test `/health` (341 req/s, p99 39.7 ms).
- [x] Secret audit: tidak ada creds hardcoded di kode; `secrets.yaml` k8s di-untrack.
- [ ] Load test chain gRPC (login) — belum diukur, perlu endpoint + setup khusus.
- [ ] Kubernetes server-side validation — butuh cluster ≥ 8 GiB RAM.
- [ ] Audit CVE image (trivy/grype) — belum dijalankan.
