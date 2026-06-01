# edo-dodo — CLAUDE.md

> Last updated: 2026-06-01

## Status: IN DEVELOPMENT
**What:** Веб-система учёта и отправки актов выполненных работ (ЭДО): реестр актов, очереди бухгалтерии и исключений, контрагенты, интеграция с СБИС (Saby), загрузка XML/файлов, экспорт в Excel/JSON.
**URL/Bot:** http://localhost (Docker Compose; API на :8080)
**GitHub:** https://github.com/Dovion/edo-dodo

## Tech Stack
| Layer | Technology |
|-------|-----------|
| Backend language | Java 17 |
| Backend framework | Spring Boot 3.5.14 (Web, Data MongoDB, Validation) |
| Frontend | React 19, React Router 7, CRA + CRACO |
| UI | Tailwind CSS 3, Radix UI, shadcn/ui-style components |
| Database | MongoDB 7 |
| HTTP client | Apache HttpClient 5 (RestTemplate) |
| Import/export | Apache POI (xlsx), OpenCSV, Jackson |
| Reverse proxy | Nginx (SPA + `/api/` → backend) |
| Deployment | Docker Compose (backend, frontend/nginx, mongo) |
| External EDO | Saby / SBIS API (`online.sbis.ru`) |
| File storage | Локальный volume `uploads` + опционально Emergent objstore |

## Architecture
```
edo-dodo/
├── src/main/java/ru/lukin/edododo/
│   ├── EdoDodoApplication.java     — точка входа Spring Boot
│   ├── config/                     — CORS, HTTP client, startup
│   ├── controller/                 — REST API (/api/...)
│   ├── service/                    — бизнес-логика актов, Saby, файлов, настроек
│   ├── repository/                 — Spring Data MongoDB
│   ├── model/                      — ActDocument, FileDocument, SabySettings...
│   ├── dto/                        — запросы/ответы API
│   └── exception/                  — GlobalExceptionHandler
├── src/main/resources/
│   └── application.yaml            — порт, MongoDB, CORS, uploads, Saby
├── frontend/
│   ├── src/pages/                  — Dashboard, Acts, Exceptions, Accounting, ...
│   ├── src/components/             — Layout, ui/* (shadcn)
│   └── public/
├── nginx/nginx.conf                — SPA + proxy /api/ → backend:8080
├── docker-compose.yml              — backend, frontend, mongo
├── Dockerfile                      — multi-stage Maven → JRE 17
└── src/test/                       — 1 smoke-тест (EdoDodoApplicationTests)
```

**Поток запросов:** браузер → Nginx:80 → статика React или `proxy_pass` `/api/` → Spring Boot:8080 → MongoDB / uploads / Saby.

**Основные сущности:** `ActDocument` (статусы жизненного цикла акта), `FileDocument` (вложения), `SabySettingsDocument` (учётные данные СБИС), история изменений в `HistoryEntry`.

## Key Files
- `src/main/java/ru/lukin/edododo/service/ActServiceImpl.java` (~839 lines) — CRUD актов, фильтры, смена статусов, парсинг XML, seed, пакетная отправка, ZIP/JSON экспорт
- `src/main/java/ru/lukin/edododo/service/SabyServiceImpl.java` (~456 lines) — авторизация и отправка документов в СБИС, формирование JSON-RPC к `online.sbis.ru`
- `src/main/java/ru/lukin/edododo/controller/ActController.java` (~81 lines) — `/api/acts`, upload, send-saby, batch, export
- `src/main/java/ru/lukin/edododo/model/ActDocument.java` (~110 lines) — модель акта (контрагент, ИНН/КПП, период, статус, вложения)
- `src/main/resources/application.yaml` — `MONGO_URL`, `DB_NAME`, CORS, `app.uploads-dir`, `app.storage.url`, `emergent.llm-key`
- `frontend/src/pages/ActsRegistry.jsx` (~324 lines) — реестр актов в UI
- `frontend/src/pages/Dashboard.jsx` (~251 lines) — дашборд и статистика
- `frontend/src/App.js` (~30 lines) — маршруты: `/`, `/acts`, `/exceptions`, `/accounting`, `/counterparties`, `/settings`
- `docker-compose.yml` — оркестрация сервисов и volume для uploads/mongo

## API (кратко)
| Префикс | Назначение |
|---------|------------|
| `GET /api/acts` | Список актов (фильтры: status, legal_entity, counterparty, period, search, page) |
| `PATCH /api/acts/{id}/status` | Обновление статуса |
| `POST /api/acts/{id}/send-saby` | Отправка одного акта в СБИС |
| `POST /api/acts/send-batch` | Пакетная отправка |
| `POST /api/acts/upload` | Загрузка актов (multipart) |
| `GET /api/dashboard/*` | Статистика, «требует внимания», этапы |
| `GET/POST /api/settings/saby` | Настройки и авторизация СБИС |
| `GET /api/acts/export/xlsx` | Excel-выгрузка |

Статусы акта (`ActStatus`): Готов к отправке → Отправлен → Подписан / Нет ответа / Корректировки → В работе бухгалтерии → Закрыт.

## Deployment
- **Локально / демо:** Docker Desktop, из корня репозитория:
  ```bash
  docker compose up -d
  ```
  UI: http://localhost · Backend напрямую: http://localhost:8080 · MongoDB: localhost:27017
- **Сервисы Compose:** `backend` (8080), `frontend` (nginx, 80), `mongo` (7)
- **Переменные:** `MONGO_URL`, `APP_UPLOADS_DIR` (в compose), опционально `EMERGENT_LLM_KEY`
- **Логи backend:** `docker compose logs -f backend`
- **Перезапуск:** `docker compose restart` или `docker compose up -d --build`
- **VPS / systemd:** в репозитории не описаны — продакшен-развёртывание не зафиксировано

## Recent Changes
- 2026-05-17: Обновления интеграции Saby и обработки XML-файлов
- 2026-05-07: README с инструкцией запуска через Docker Compose
- 2026-05-07: Initial commit (Spring Boot + React + MongoDB)

## graphify

Граф знаний **ещё не сгенерирован** (`graphify-out/` отсутствует в репозитории).

Rules (когда появится граф):
- Before answering architecture or codebase questions, read `graphify-out/GRAPH_REPORT.md`
- After modifying code files, run `graphify update .` to keep the graph current
