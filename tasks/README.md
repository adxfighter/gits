# Банк задач GITS

Задачи профиля «Java-разработчик» лежат в `tasks/java/`. Каждая задача — **шаблон** с несколькими **вариантами**. В базу данных попадают только варианты, прошедшие валидатор, и только с совпадающей контрольной суммой.

## Структура
```
tasks/
  schema/                         JSON Schema для template.yaml и task.yaml
  java/
    T01-race-counter/             <код шаблона>-<короткое имя>
      template.yaml
      variants/
        v01/
          task.yaml               параметры варианта
          statement.md            условие для кандидата (русский язык)
          starter/src/main/java/…         код, который получает кандидат (editable + readonly)
          solution/src/main/java/…        эталонное решение — только editable-файлы
          tests-visible/src/test/java/…   2–5 видимых тестов
          tests-hidden/src/test/java/…    5–15 скрытых тестов
          validation.json         результат валидатора (создаётся автоматически)
```

Пути в `task.yaml` (`editable`, `readonly`) указываются так, как файлы лежат в песочнице: `src/main/java/ru/gits/task/...`. Пакет задачи: `ru.gits.task.<домен>.<код шаблона в нижнем регистре>`.

## template.yaml
| Поле | Описание |
|---|---|
| `code` | `T00`…`T99`; каталог шаблона начинается с этого кода |
| `title` | название на русском |
| `competencies` | 1–4 кода компетенций (`java.concurrency.atomicity` и т. п.) |
| `base_level` | `junior` / `middle` / `senior` |
| `description` | для авторов: что проверяет шаблон, типичные ошибки, как устроены тесты |
| `difficulty_model` | параметры сложности и их допустимые значения |
| `domains` | предметные области вариантов |

## task.yaml
| Поле | Описание |
|---|---|
| `code` | `<шаблон>-vNN`, совпадает с каталогом |
| `template` | код шаблона |
| `kind` | `task` (по умолчанию) или `calibration` |
| `level` | `junior` / `middle` / `senior` |
| `domain` | предметная область |
| `time_limit_min` | 1–120 минут, для задач обычно 20–30 |
| `difficulty_params` | значения параметров из `difficulty_model` |
| `editable` | файлы, которые кандидат может менять |
| `readonly` | файлы, которые кандидат видит, но не меняет |
| `flaky_policy` | `runs`, `reference_pass_min`, `starter_fail_min`: многопоточные задачи — 20/20/18, детерминированные — 3/3/3 |
| `rubric` | 3–6 пунктов для ревьюера |

## Проверки валидатора
`java -jar backend/gits-taskbank/target/gits-taskbank.jar validate tasks/java [--variant T01-v03] [--runs N] [--parallel N]`, либо `scripts/validate-tasks.sh` / `scripts/validate-tasks.ps1`.

| № | Проверка | Условие |
|---|---|---|
| 1 | `schema` | `template.yaml` и `task.yaml` соответствуют схеме; код варианта совпадает с каталогом |
| 2 | `files` | starter = editable ∪ readonly; solution ⊆ editable; пути допустимы; 2–5 видимых и 5–15 скрытых тестов |
| 3 | `starter_compiles` | starter + видимые тесты компилируются |
| 4 | `reference_passes` | solution + все тесты проходят не менее `reference_pass_min` раз из `runs`, отчёт совпадает с тестами задачи |
| 5 | `starter_fails` | starter + скрытые тесты проваливают хотя бы один тест не менее `starter_fail_min` раз из `runs` (кроме `calibration`) |
| 6 | `reference_time` | один прогон решения — не более 10 секунд |
| 7 | `forbidden` | нет сети, файлового ввода-вывода, `System.exit`, `Runtime.exec`, `ProcessBuilder`, рефлексии, нативного кода, `Thread.sleep` дольше 200 мс в тестах |
| 8 | `sizes` | `statement.md` — 400–3000 символов; код starter — 40–400 непустых строк |
| 9 | `no_leak` | видимые тесты не содержат фрагментов решения длиннее 3 строк |
| 10 | `content_hash` | SHA-256 всех файлов варианта, кроме `validation.json` |

Прогоны выполняются в том же образе `gits-sandbox-java` и с теми же параметрами изоляции, что и решения кандидатов.

`java -jar … verify-hashes tasks/java` быстро проверяет без песочницы, что каждый `validation.json` соответствует текущим файлам (так делает CI и загрузчик в БД).

## Шаблоны и план
- `tasks/competencies.yaml` — матрица компетенций профиля с ожиданиями по уровням.
- `tasks/java/PLAN.md` — план 50 вариантов: домен, уровень, параметры сложности, суть дефекта.
- `validate tasks/java --schema-only` проверяет схемы всех шаблонов и вариантов без песочницы.

## Образец
`tasks/java/T00-example` — простая задача (граничное условие в подсчёте), по которой генерируются остальные шаблоны.
