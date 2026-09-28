# Чек-лист безопасности v1.0

Проверено перед выпуском v1.0.0 (2026-09-28, P17, [ADR 0016](adr/0016-release-v1.md)). Автоматические пункты повторяются в CI на каждый pull request.

| № | Пункт | Как проверено | Результат |
|---|---|---|---|
| 1 | **Изоляция песочницы** | `scripts/sandbox-selftest.ps1` на Windows (Docker Desktop, `runc`); в CI — `scripts/sandbox-selftest.sh` (задание `sandbox`). «Злые» программы: сеть, fork-бомба, 2 ГБ памяти, запись в образ, поиск секретов и docker-сокета, подделка маркеров протокола, бесконечный цикл, поток вывода; после — ни одного оставшегося контейнера | ✅ все 11 проверок прошли |
| 2 | **Параметры контейнера** | `SandboxConfig` и `sandbox/java/README.md`: `--network=none`, `--read-only`, tmpfs `/work` и `/tmp`, `--memory=768m --memory-swap=768m`, `--cpus=1`, `--pids-limit=128`, `--cap-drop=ALL`, `no-new-privileges`, пользователь 10001, таймаут 30 с, `--rm` | ✅ совпадает с правилами CLAUDE.md |
| 3 | **Скрытые тесты и решения не уходят кандидату** | `SessionApiTest.taskResponseNeverContainsSolutionOrHiddenTests`, `submitResultShowsOnlyTheNumberOfPassedHiddenTests`; runner хранит скрытые тесты под именами «Скрытый тест N» без сообщений | ✅ тесты в CI (`backend`) |
| 4 | **…и не уходят работодателю** | `EmployerApiTest.reportHasResultsPerTaskAndNoSecrets`, `replayGivesStartingFilesRunsAndReplayEventsPageBySeq` (`assertNoSecrets`: ни файлов SOLUTION и HIDDEN_TEST, ни их содержимого) | ✅ |
| 5 | **Выгрузка для исследования без личных данных** | `AdminApiTest`: в архиве нет меток кандидатов, email, названий компаний, IP и их хешей, user agent; все файлы варианта — только в `GET /admin/tasks/variants/{id}` для ADMIN | ✅ |
| 6 | **Разграничение доступа** | `EmployerApiTest.anotherCompanySeesNothing` (чужое — 404), `candidateCannotUseTheEmployerApi`, роли EMPLOYER/ADMIN (403), кандидат видит только свою сессию; `TelemetryApiTest.beaconToAForeignClosedOrExpiredTask` | ✅ |
| 7 | **CSRF** | `AuthIntegrationTest.loginWithoutCsrfTokenIsForbidden`, `csrfEndpointIsPublic`, `csrfCookieIsReadableBySpaServedFromRoot`; sendBeacon — одноразовый токен вместо CSRF (`TelemetryApiTest.sendBeaconUsesTheOneTimeTokenInsteadOfCsrf`); `scripts/smoke-auth.sh` в CI | ✅ |
| 8 | **Лимиты** | Вход — 10 в минуту с IP (`loginIsRateLimitedPerClientAddress`), вход по ссылке — 20 в минуту; запуски — один одновременно и 60 на задачу (`oneActiveRunAtATimeAndSixtyRunsPerTask`); пакет телеметрии — 2 000 событий и 256 КБ (`batchLimits`); код — 256 КБ; тело запроса в nginx — 1 МБ; архив исходников — `SourceArchiveTest`; вывод песочницы — 64 КБ | ✅ |
| 9 | **Пароли и секреты** | Пароли — BCrypt; секреты только в `.env` (в git — `.env.example` с заглушками); в коде и тестах секретов нет (`git grep` по паролям из `.env` пуст) | ✅ |
| 10 | **Логи без секретов** | Логи всех сервисов чистого стенда после E2E и нагрузки (20 тыс. строк): значения `POSTGRES_PASSWORD`, `DEMO_*_PASSWORD`, `GITS_CANDIDATE_SECRET` не встречаются; cookie сессий — тоже. **Найдено и исправлено:** журнал доступа nginx хранил токен приглашения `/c/<токен>` в адресе и Referer — теперь `/c/***` (`frontend/nginx.conf`). CI после E2E проверяет, что токенов в логах нет | ✅ после исправления |
| 11 | **Зависимости npm** | `npm audit` в `frontend` (все и `--omit=dev`) и `e2e`; в CI — `npm audit --audit-level=critical` | ✅ 0 уязвимостей |
| 12 | **Зависимости Java и образы** | Trivy 0.74.0 по образам `gits-api`, `gits-runner`, `gits-web`, `gits-sandbox-java` (пакеты ОС и библиотеки в jar), `--severity HIGH,CRITICAL`. **Найдено и исправлено:** в `gits-api` Tomcat 10.1.55 (3 CRITICAL) → 10.1.60, в `gits-api` и `gits-runner` драйвер PostgreSQL 42.7.11 (HIGH) → 42.7.13 (`backend/pom.xml`); образ web на `nginx:1.28-alpine` (Alpine 3.23: 2 CRITICAL, 55 HIGH) → `nginx:1.30-alpine` с `apk upgrade`. В CI — Trivy по собранным образам, CRITICAL с исправлением роняет сборку | ✅ после исправления |
| 13 | **Телеметрия и согласие** | Сбор только после согласия и на странице оценки, символы клавиш не передаются — только категория (`docs/telemetry.md`, тесты frontend и `TelemetryApiTest`) | ✅ |
| 14 | **Сеть стека** | PostgreSQL слушает только `127.0.0.1:5432`; наружу — только web (`WEB_PORT`); доступ к Docker-сокету — только у runner | ✅ `docker-compose.yml` |

## Проверка с нуля
| Платформа | Как | Результат |
|---|---|---|
| Windows 10 Pro, Docker Desktop 29.8 (WSL2) | свежий клон `main` с GitHub, `.env` из `.env.example` со своими паролями, `scripts/up.ps1`, затем E2E (`npx playwright test`) и `demo-seed` с `tests/demo-seed.spec.ts` | ✅ стек готов, E2E: 5 прошли, 1 пропущен (демо — отдельно), демо-тест прошёл. Найдено: клон в папку с длинным путём не выкладывает файлы (MAX_PATH) — описано в README |
| Linux (Ubuntu, GitHub Actions) | задание `e2e`: `bash scripts/up.sh` на чистой машине, E2E, `demo-seed`, проверка логов | ✅ в CI |

## Вне v1.0
- HTTPS и заголовки безопасности для публичного размещения: v1.0 — только локальный стенд на `localhost`.
- gVisor (`SANDBOX_RUNTIME=runsc`) — только на Linux-хостах; на Windows песочница работает на `runc`.
- Ручная проверка «сломать контейнер» самостоятельно и вычитка текстов согласия — за командой (docs/prompts/prompts-v1.0.md, раздел 8).
