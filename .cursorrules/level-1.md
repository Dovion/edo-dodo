# edo-dodo — Level 1

> Last updated: 2026-06-15

## Status: IN DEVELOPMENT
**What:** Веб-система учёта и отправки актов сверки (ЭДО): реестр актов, очереди бухгалтерии и исключений, контрагенты, интеграция с СБИС (Saby), загрузка XML/файлов из 1С, экспорт в Excel/JSON, периодический опрос статусов в СБИС.
**URL/Bot:** http://localhost (Docker Compose; API на :8080)
**GitHub:** https://github.com/Dovion/edo-dodo

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.5.14 (Web, Security, Data MongoDB, Validation, Thymeleaf) |
| Auth | Spring Security — form login, session cookie (24h), in-memory admin |
| Frontend | React 19, React Router 7, CRA + CRACO, axios (`withCredentials`) |
| UI | Tailwind CSS 3, Radix UI, shadcn/ui-style components |
| Database | MongoDB 7 |
| Scheduling | `@EnableScheduling` — опрос статусов СБИС по cron |
| HTTP client | Apache HttpClient 5 (RestTemplate) |
| Import/export | Apache POI (xlsx), OpenCSV, Jackson (SNAKE_CASE) |
| Reverse proxy | Nginx (SPA + `/api/` → backend) |
| Deployment | Docker Compose (backend, frontend/nginx, mongo) |
| External EDO | Saby / SBIS API (`online.sbis.ru`, JSON-RPC 2.0) |
| External ERP | 1С — выгрузка XML актов (реализовано); обратная синхронизация — целевой шаг |
| File storage | Локальный volume `uploads` + опционально Emergent objstore |

## Architecture
```
edo-dodo/
├── src/main/java/ru/lukin/edododo/
│   ├── EdoDodoApplication.java     — точка входа, @EnableScheduling
│   ├── config/                     — CORS, Security, HTTP client, SabyAppProperties, startup
│   ├── controller/                 — REST API (/api/...), LoginPageController (/login)
│   ├── service/                    — акты, Saby, polling, файлы, настройки, экспорт
│   ├── scheduler/                  — SabyPollingScheduler (cron-опрос СБИС)
│   ├── repository/                 — Spring Data MongoDB
│   ├── model/                      — ActDocument, FileDocument, SabyAccount, SabySettings...
│   ├── dto/                        — запросы/ответы API
│   └── exception/                  — GlobalExceptionHandler
├── src/main/resources/
│   └── application.yaml            — порт, MongoDB, CORS, security, Saby poll, uploads
├── frontend/
│   ├── src/pages/                  — Dashboard, Acts, Exceptions, Accounting, Settings, Login
│   ├── src/components/             — Layout, RequireAuth, ui/* (shadcn)
│   └── src/lib/api.js              — axios + redirect на /login при 401/403
├── nginx/nginx.conf                — SPA + proxy /api/ → backend:8080
├── docker-compose.yml              — backend, frontend, mongo
├── Dockerfile                      — multi-stage Maven → JRE 17
├── .env.example                    — APP_ADMIN_PASSWORD, APP_LOGIN_SUCCESS_URL
└── src/test/                       — 1 smoke-тест (EdoDodoApplicationTests)
```

**Поток запросов:** браузер → Nginx:80 → статика React или `proxy_pass` `/api/` → Spring Boot:8080 (Security filter) → MongoDB / uploads / Saby.

**Основные сущности:**
- `ActDocument` — акт сверки (контрагент, ИНН/КПП, период, статус, вложения, `sabySendId`, `sabyAccountId`, дедлайн ответа контрагента)
- `FileDocument` / `AttachmentFile` — вложения к актам
- `SabySettingsDocument` + `SabyAccount` — несколько учётных записей СБИС
- `CounterpartyExceptionDocument` — контрагенты-исключения (отправка в СБИС запрещена)
- `HistoryEntry` — история смены статусов

**Жизненный цикл акта (ActStatus):**
```
Загружено → Отправлено в СБИС → Отправлено Контрагенту
  → Получен подписанный / Нет ответа / Корректировки
  → В работе бухгалтерии → Закрыт
```
Планировщик (`SabyPollingScheduler`, по умолчанию каждые 2 мин) опрашивает акты в статусе «Отправлено Контрагенту» через `СБИС.ПрочитатьДокумент`: обновляет PDF со штампом, проверяет подпись, переводит в «Получен подписанный» или «Нет ответа» по истечении срока.

## Key Files
- `src/main/java/ru/lukin/edododo/service/SabyServiceImpl.java` (~1547 lines) — JSON-RPC к `online.sbis.ru`: auth, ЗаписатьДокумент, ВыполнитьДействие, ПрочитатьДокумент, отложенное подписание, sync PDF/вложений
- `src/main/java/ru/lukin/edododo/service/ActServiceImpl.java` (~913 lines) — CRUD актов, фильтры, смена статусов, парсинг XML 1С, seed, пакетная отправка, ZIP/JSON экспорт
- `src/main/java/ru/lukin/edododo/service/SabyActPollingService.java` (~110 lines) — логика опроса статусов и переходов по дедлайну
- `src/main/java/ru/lukin/edododo/scheduler/SabyPollingScheduler.java` — cron-триггер опроса (`app.saby.poll.cron`)
- `src/main/java/ru/lukin/edododo/config/SecurityConfig.java` — защита `/api/**`, form login, CSRF cookie
- `src/main/java/ru/lukin/edododo/config/SabyAppProperties.java` — deferred-cert-type, wait days, poll enabled/cron
- `src/main/java/ru/lukin/edododo/controller/ActController.java` (~102 lines) — `/api/acts`, upload, send-saby, batch, export
- `src/main/java/ru/lukin/edododo/model/ActDocument.java` (~134 lines) — модель акта с полями контрагента и СБИС
- `src/main/resources/application.yaml` — MongoDB, security, Saby poll, CORS, uploads, storage
- `frontend/src/pages/ActsRegistry.jsx` (~507 lines) — реестр актов, отправка в СБИС, фильтры
- `frontend/src/pages/Settings.jsx` (~359 lines) — настройки и авторизация нескольких аккаунтов СБИС
- `frontend/src/pages/Dashboard.jsx` (~250 lines) — дашборд и статистика
- `frontend/src/App.js` — маршруты: `/login`, `/`, `/acts`, `/exceptions`, `/accounting`, `/counterparties`, `/settings`
- `docker-compose.yml` — оркестрация сервисов, `.env`, volume uploads/mongo

## API (кратко)

Все `/api/**` требуют аутентификации (кроме `GET /api/auth/csrf`).

| Префикс | Назначение |
|---------|------------|
| `GET /api/auth/csrf` | CSRF-токен для SPA |
| `GET /api/auth/session` | Текущая сессия |
| `GET /api/acts` | Список актов (фильтры: status, legal_entity, counterparty, period, search, page, limit) |
| `GET /api/acts/{id}` | Один акт |
| `PATCH /api/acts/{id}/status` | Обновление статуса |
| `POST /api/acts/{id}/send-saby` | Отправка одного акта в СБИС (body: documentType, counterpartyResponseWaitDays, sabyAccountId) |
| `POST /api/acts/send-batch` | Пакетная отправка готовых актов |
| `POST /api/acts/upload` | Загрузка актов из 1С (multipart) |
| `GET /api/acts/export/json` | JSON-выгрузка |
| `GET /api/acts/export/xlsx` | Excel-выгрузка |
| `GET /api/acts/samples` | ZIP с примерами файлов |
| `POST /api/acts/{id}/files` | Загрузка вложения к акту |
| `GET /api/acts/{id}/files` | Список вложений |
| `GET /api/files/{id}/download` | Скачивание файла |
| `DELETE /api/files/{id}` | Удаление файла |
| `GET /api/dashboard/stats` | Статистика |
| `GET /api/dashboard/attention` | Акты, требующие внимания |
| `GET /api/dashboard/stages` | Распределение по этапам |
| `GET /api/counterparties` | Список контрагентов |
| `PATCH /api/counterparties/exceptions` | Исключения контрагентов |
| `GET /api/legal-entities` | Юрлица |
| `GET /api/periods` | Периоды |
| `GET /api/settings/app` | Настройки приложения |
| `GET/PUT /api/settings/saby` | Настройки СБИС |
| `POST /api/settings/saby/auth` | Авторизация аккаунта СБИС |
| `DELETE /api/settings/saby/accounts/{id}` | Удаление аккаунта СБИС |

**Аутентификация:** `POST /login` (form), `POST /logout`. Логин по умолчанию: `admin`, пароль — `APP_ADMIN_PASSWORD` из `.env`.

## Deployment
- **Локально / демо:** Docker Desktop, из корня репозитория:
  ```bash
  cp .env.example .env   # задать APP_ADMIN_PASSWORD
  docker compose up -d
  ```
  UI: http://localhost · Backend напрямую: http://localhost:8080 · MongoDB: localhost:27017
- **Сервисы Compose:** `backend` (8080), `frontend` (nginx, 80), `mongo` (7)
- **Переменные (.env):**
  - `APP_ADMIN_PASSWORD` — обязателен
  - `APP_LOGIN_SUCCESS_URL` — редирект после входа (`/` для Docker, `http://localhost:3000/` для `npm start`)
  - `MONGO_URL`, `APP_UPLOADS_DIR` — задаются в compose
  - `SABY_POLL_ENABLED`, `SABY_POLL_CRON`, `SABY_DEFERRED_CERT_TYPE`, `SABY_COUNTERPARTY_WAIT_DAYS` — опционально
- **Dev frontend:** `cd frontend && npm start` → :3000, proxy `/api` через `setupProxy.js`
- **Логи backend:** `docker compose logs -f backend`
- **Перезапуск:** `docker compose restart` или `docker compose up -d --build`
- **VPS / systemd:** в репозитории не описаны — продакшен-развёртывание не зафиксировано

## Recent Changes
- 2026-06-15: Актуализация level-1.md — auth, polling СБИС, мультиаккаунты, обновлённые статусы и API
- 2026-06: Планировщик опроса СБИС (`SabyPollingScheduler`, `SabyActPollingService`), отложенное подписание, sync PDF/вложений
- 2026-06: Spring Security (form login, session 24h), страница `/login`, `RequireAuth` на frontend
- 2026-06: Несколько аккаунтов Saby (`SabyAccount`), выбор аккаунта при отправке
- 2026-06: Расширенная модель контрагента (ИНН/КПП, тип UL/IP/FL, дедлайн ответа)
- 2026-05-17: Обновления интеграции Saby и обработки XML-файлов
- 2026-05-07: README с инструкцией запуска через Docker Compose
- 2026-05-07: Initial commit (Spring Boot + React + MongoDB)

## graphify

Граф знаний **ещё не сгенерирован** (`graphify-out/` отсутствует в репозитории).

Rules (когда появится граф):
- Before answering architecture or codebase questions, run `graphify query "<question>"` or read `graphify-out/GRAPH_REPORT.md`
- After modifying code files, run `graphify update .` to keep the graph current
