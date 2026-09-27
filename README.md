# GITS v1.0 — локальная демо-версия

Платформа оценки практических навыков Java-разработчиков: кандидат решает задачи в веб-редакторе кода, код исполняется в изолированном Docker-контейнере, работодатель получает отчёт и воспроизведение сессии.

Версия v1.0 работает полностью локально в Docker Compose — без облака и внешних API.

## Требования
- Docker Desktop (Windows: бэкенд WSL2) или Docker Engine с Compose v2 (Linux).
- 16 ГБ RAM рекомендуется, ~5 ГБ диска под образы.
- Для разработки: JDK 21, Maven 3.9+, Node.js 24 LTS.

## Быстрый старт
1. Скопируйте `.env.example` в `.env` и задайте свои локальные значения паролей (скрипт запуска создаст `.env` сам, если его нет).
2. Запустите:
   - Windows: `powershell -ExecutionPolicy Bypass -File scripts/up.ps1`
   - Linux/macOS: `scripts/up.sh`
3. Откройте http://localhost:8080.

Остановка: `scripts/down.ps1` или `scripts/down.sh` (флаг `-Purge` / `--purge` удаляет базу данных).

## Сервисы
| Сервис | Назначение | Порт хоста |
|---|---|---|
| web | интерфейс (nginx) и прокси `/api` | 8080 |
| api | REST API, миграции БД | — |
| runner | исполнение кода кандидатов в контейнерах песочницы | — |
| postgres | PostgreSQL 17 | 127.0.0.1:5432 |

## Разработка
```bash
cd backend
mvn verify   # unit- и интеграционные тесты; нужен запущенный Docker (Testcontainers поднимает PostgreSQL 17)
```
Правила проекта для разработчиков и ИИ-ассистента — в [CLAUDE.md](CLAUDE.md), архитектурные решения — в [docs/adr](docs/adr), план разработки — в [docs/prompts/prompts-v1.0.md](docs/prompts/prompts-v1.0.md).

## Статус
| Этап | Состояние |
|---|---|
| P00 Каркас репозитория | готово: backend, Angular 21, Docker Compose, CI |
| P01 Схема БД | готово |
| P02 Доступ: работодатель, приглашения, согласие | готово |
| P03 Песочница для Java | готово |
| P04 Сервис runner | готово |
| P05 Формат банка задач и валидатор | готово |
| P20–P24 Банк задач: шаблоны, 50 вариантов T01–T10, ревью, калибровочный блок, каталог | готово ([tasks/java/CATALOG.md](tasks/java/CATALOG.md)) |
| P06 Загрузка банка задач в БД | готово |
| P07 Сессия оценки, подбор задач, запуск кода | готово ([docs/api.md](docs/api.md)) |
| P08–P17 | в работе |

### Frontend
```bash
cd frontend
npm ci
npm start          # http://localhost:4200, /api проксируется на http://localhost:8080 (нужен запущенный стек)
npm run lint
npm test
npm run build
```
