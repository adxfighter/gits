# Песочница gits-sandbox-java

Образ компилирует и тестирует код кандидата. **Каждый запуск — новый контейнер**, который удаляется после завершения. Код в контейнер передаётся tar-архивом через stdin.

## Состав
- `eclipse-temurin:21-jdk-alpine`, пользователь `sandbox` (uid 10001).
- `/opt/libs`: JUnit Platform Console Standalone 1.14.4 (Jupiter 5.14), AssertJ 3.27.7, Mockito 5.24.0 с зависимостями. Библиотеки скачиваются при сборке образа с проверкой контрольных сумм репозитория (`--strict-checksums`); SHA-256 записаны в `/opt/libs/SHA256SUMS`.
- `/opt/gits/entrypoint.sh` — протокол запуска (ниже).

## Обязательные параметры запуска
Runner (`gits-runner`) и самопроверка используют ровно эти параметры. Ослаблять их можно только через ADR.

```
docker run --rm -i \
  --network=none \
  --hostname localhost \
  --read-only \
  --tmpfs /work:rw,nosuid,nodev,size=64m,mode=1777 \
  --tmpfs /tmp:rw,nosuid,nodev,size=16m,mode=1777 \
  --memory=768m --memory-swap=768m \
  --cpus=1 \
  --pids-limit=128 \
  --cap-drop=ALL \
  --security-opt=no-new-privileges \
  --user 10001:10001 \
  --label gits.sandbox=true \
  --runtime "$SANDBOX_RUNTIME" \
  gits-sandbox-java:local < sources.tar
```

| Параметр | Зачем |
|---|---|
| `--network=none` | Нет доступа к сети и DNS |
| `--hostname localhost` | XML-отчёт JUnit определяет имя хоста; без сети поиск имени контейнера ждёт таймаута DNS (~15 с), а `localhost` есть в `/etc/hosts` |
| `--read-only` + `tmpfs` | Образ неизменяем; писать можно только во временные `/work` и `/tmp` в памяти |
| `--memory`, `--memory-swap` | Лимит памяти без swap |
| `--cpus=1` | Один процессор на запуск |
| `--pids-limit=128` | Останавливает fork-бомбы и массовое создание потоков |
| `--cap-drop=ALL`, `no-new-privileges`, `--user 10001` | Без привилегий и без возможности их получить |
| `--label gits.sandbox=true` | Runner находит и удаляет «осиротевшие» контейнеры |
| `--runtime` | `runc` по умолчанию; `runsc` (gVisor) — на Linux-хостах с установленным gVisor |

Таймаут (30 секунд) обеспечивает вызывающая сторона: по истечении контейнер принудительно останавливается (`docker kill`).

## Протокол вывода
Архив на stdin должен содержать `src/main/java/...` и/или `src/test/java/...`.

| Результат | Вывод | Код выхода |
|---|---|---|
| Ошибка компиляции или пустой архив | `===GITS-COMPILE-ERROR===`, затем вывод javac (до 64 КБ) | 2 |
| Тесты выполнены | `===GITS-OUTPUT-BEGIN===` … `===GITS-OUTPUT-END===` — консоль тестов (до 64 КБ); `===GITS-REPORT-BEGIN===` … `===GITS-REPORT-END===` — XML-отчёт JUnit в base64 одной строкой (отчёт больше 1 МБ не передаётся) | 0 |

Отчёт передаётся в base64: сообщения об ошибках внутри него формирует код кандидата, и в них могут оказаться маркеры протокола; в base64 они появиться не могут. Кандидат может напечатать поддельные маркеры в консольный вывод, поэтому подлинными считаются **последние** блоки — entrypoint печатает их уже после завершения JVM тестов.

Если тесты печатают больше 64 КБ, JVM останавливается, в вывод добавляется «[Вывод превысил 65536 байт, выполнение остановлено]», отчёт может отсутствовать.

JVM тестов: `-Xmx384m -Xss512k -XX:+UseSerialGC -XX:TieredStopAtLevel=1` — быстрый старт важнее пиковой производительности.

## Самопроверка
`scripts/sandbox-selftest.sh` (Linux) или `scripts/sandbox-selftest.ps1` (Windows, через Git Bash) собирает образ и запускает «злые» программы из `selftest/`: сетевые запросы, бесконечный цикл, fork-бомбу, выделение 2 ГБ, запись в образ, поиск секретов и docker-сокета, подделку маркеров протокола, поток вывода. Запускается в CI на каждый pull request. Переменные: `SANDBOX_IMAGE`, `SANDBOX_RUNTIME`, `SANDBOX_TIMEOUT` (30 с), `SKIP_BUILD=1` — без сборки образа.

Типичное время запуска простой задачи — около 3 секунд на прогретой машине (Windows, Docker Desktop).
