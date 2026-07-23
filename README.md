# Stylista Boutique — Backend

Spring Boot 3 + Supabase Postgres backend for the Stylista Boutique system.

## Domains
| Domain | What it stores |
|--------|----------------|
| Customers | name, mobile, measurements (flexible JSON) |
| Orders | product, due date, price, advance, status (priority sorted) |
| Cashbacks | amounts, expiry, live balance, full history |
| Tailors | name, boutique, city, specialization |

## Complete API

### Auth
| Method | Path | Body | Response |
|--------|------|------|----------|
| POST | `/api/admin/login` | `{username, password}` | `{token}` |

All admin routes need header: `Authorization: Bearer <token>`

### Stats
| GET | `/api/admin/stats` | — | totals: customers, orders, cashbacks |

### Customers
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/customers` | list all |
| POST | `/api/admin/customers` | `{name, mobile, measurements?}` |
| GET | `/api/admin/customers/{id}` | full profile + orders + cashbacks + live_balance |
| PUT | `/api/admin/customers/{id}/measurements` | `{measurements: "JSON string"}` |

### Orders (sorted by due_date, overdue flagged)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/orders` | all, priority sorted |
| POST | `/api/admin/orders` | create + optional cashback |
| PATCH | `/api/admin/orders/{id}` | update status, price, advance, tailor, etc. |

Order statuses: `ORDER_RECEIVED` → `PAYMENT_RECEIVED` → `MEASUREMENT_TAKEN` → `STITCHING_IN_PROGRESS` → `READY_FOR_PICKUP` → `DELIVERED`

Product types: `BLOUSE` `LEHENGA` `BRIDAL_WEAR` `GOWN` `SUIT` `ALTERATION` `OTHER`

### Cashbacks
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/cashbacks` | all cashbacks |
| POST | `/api/admin/cashback` | assign cashback to customer |
| POST | `/api/admin/cashback/{id}/redeem` | mark as used |

Defaults: 20% / 60 days. Expiry is per-cashback. Live balance = sum of active ones.

### Tailors
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/admin/tailors` | all tailors |
| POST | `/api/admin/tailors` | add tailor |
| PATCH | `/api/admin/tailors/{id}` | update |
| DELETE | `/api/admin/tailors/{id}` | deactivate |

### Public (no auth)
| Method | Path | Notes |
|--------|------|-------|
| GET | `/api/customer/cashback?mobile=xxx` | balance + history by mobile |
| GET | `/health` | health check |

## Deploy

### 1. Supabase (database — do first)
1. supabase.com → New Project → Region: Singapore
2. Settings → Database → Connection string → URI → copy it

### 2. GitHub
```bash
git init && git add . && git commit -m "Initial backend"
git remote add origin https://github.com/stylistaboutique/stylista-backend.git
git push -u origin main
```

### 3. Render
1. render.com → New → Web Service → connect GitHub repo
2. Render detects render.yaml (Docker) → Create
3. Add env var: `DATABASE_URL` = your Supabase URI
4. Change `ADMIN_PASSWORD` to something secure
5. Wait ~5 min → get `https://stylista-backend.onrender.com`

### 4. DNS
In Netlify: CNAME `api` → `stylista-backend.onrender.com`
Your frontend already calls `https://api.stylistaboutique.com` ✅

## Run locally
```bash
export DATABASE_URL="postgresql://postgres:PASS@db.xxx.supabase.co:5432/postgres"
mvn spring-boot:run
# test: curl http://localhost:8080/health
```

## Adding notifications later
Edit ONLY `src/main/java/com/stylista/service/NotificationService.java`
Set `NOTIFICATIONS_ENABLED=true` in Render env vars.
No other files need changing.
