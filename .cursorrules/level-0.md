# Global Instructions

## Project Map (Level 0)

When asked about a project — read its Level 1 doc first (`.cursorrules/level-1.md` in this repo), don't grep blindly.

## Project Map

| Проект | Путь | Сервер | Статус |
|--------|------|--------|--------|
| edo-dodo (ЭДО, акты + СБИС) | `.` (репозиторий) · GitHub: [Dovion/edo-dodo](https://github.com/Dovion/edo-dodo) | local: Docker Compose (`http://localhost`) | IN DEVELOPMENT |

**Level 1:** `.cursorrules/level-1.md` — стек, архитектура, API, деплой.

### Серверы

| Имя | Адрес | Назначение |
|-----|-------|------------|
| local | http://localhost | UI (Nginx:80), API (backend:8080), MongoDB (:27017) — `docker compose up -d` |

> Продакшен-VPS в репозитории не описан. После выкладки на сервер — дополнить таблицу «Серверы» и колонку «Сервер» у edo-dodo.

### Правило

Прежде чем читать исходный код — прочитай **`.cursorrules/level-1.md`** (Level 1 этого проекта).
