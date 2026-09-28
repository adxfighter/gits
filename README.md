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
3. Откройте http://localhost:8080. Кабинет работодателя — http://localhost:8080/employer (email и пароль — `DEMO_EMPLOYER_EMAIL` и `DEMO_EMPLOYER_PASSWORD` из `.env`), раздел администратора — http://localhost:8080/admin (`DEMO_ADMIN_EMAIL`, `DEMO_ADMIN_PASSWORD`).

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
| P08 Приём телеметрии | готово ([docs/telemetry.md](docs/telemetry.md)) |
| P09 API кабинета работодателя | готово ([docs/api.md](docs/api.md)) |
| P10 Интерфейс кандидата | готово: вход по ссылке, согласие, правила, рабочая область с Monaco, автосохранение, запуск и отправка |
| P11 Сбор телеметрии в браузере | готово ([docs/telemetry.md](docs/telemetry.md#сбор-в-браузере)) |
| P12 Индикаторы достоверности и предварительный балл | готово ([docs/indicators.md](docs/indicators.md)) |
| P13 Кабинет работодателя | готово: вход, приглашения, отчёт по сессии ([ADR 0011](docs/adr/0011-employer-dashboard-ui.md)) |
| P14 Воспроизведение сессии | готово: проигрыватель, шкала времени, график скорости набора ([ADR 0012](docs/adr/0012-session-replay.md)) |
| P15 Раздел администратора и выгрузка для исследования | готово: банк задач, все сессии, пересчёт, экспорт с псевдонимизацией ([ADR 0013](docs/adr/0013-admin-and-research-export.md)) |
| P16–P17 | в работе |

### Frontend
```bash
cd frontend
npm ci
npm start          # http://localhost:4200, /api проксируется на http://localhost:8080 (нужен запущенный стек)
npm run lint
npm test
npm run build
```
Редактор кода — Monaco (`monaco-editor`, версия закреплена): при сборке он копируется из node_modules в `assets/monaco` и загружается только оттуда, внешних запросов страница не делает. Интерфейс кандидата — маршруты `/c/:token` (вход по ссылке), `/c/consent`, `/c/intro`, `/c/session`, `/c/done`; `/c/closed` — доступ закрыт (ссылка отозвана или оценка завершена), `/c/offline` — нет связи с сервером. Кабинет работодателя — `/login`, `/employer` (приглашения), `/employer/sessions/:id` (отчёт), `/employer/replay/:sessionTaskId` (воспроизведение сессии). Раздел администратора — `/admin/tasks` (банк задач), `/admin/tasks/:variantId` (вариант со всеми файлами), `/admin/sessions` (сессии, пересчёт, выгрузка для исследования).
