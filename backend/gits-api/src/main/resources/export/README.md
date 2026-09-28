# Выгрузка GITS для исследования

Архив содержит данные сессий оценки, начатых в указанном периоде (`manifest.json`: `from`, `to` — дни по UTC включительно; `null` — без ограничения). Формат — `gits-research-export/1`.

## Псевдонимизация
- Каждой сессии, заданию и компании присвоен **случайный идентификатор этого архива** (`s-…`, `t-…`, `c-…`). Он не выводится из данных платформы: связать архив с базой данных или с другой выгрузкой по нему нельзя.
- В архиве **нет**: метки кандидата, которую задал работодатель, email, названий компаний, IP-адресов (и их хешей), user agent, токенов приглашений.
- Время — в UTC (ISO 8601). Код, который набирал кандидат, и события ввода сохранены: это предмет исследования. Кандидат давал на это согласие (`consentVersion` — версия текста согласия).

## Файлы
Каждый `.jsonl` — одна JSON-запись на строку.

### sessions.jsonl — сессии
| Поле | Смысл |
|---|---|
| `session` | псевдоним сессии |
| `company` | псевдоним компании-работодателя |
| `targetLevel` | уровень приглашения: `JUNIOR`, `MIDDLE`, `SENIOR` |
| `status` | `IN_PROGRESS`, `FINISHED` (завершил кандидат), `EXPIRED` (время вышло) |
| `timeLimitMin` | длительность сессии, минут |
| `startedAt`, `finishedAt` | начало и конец |
| `consentVersion` | версия принятого текста согласия |
| `preliminaryScore` | предварительный балл 0–100, до психометрической калибровки; `null`, пока не посчитан |
| `scoreComputedAt` | время расчёта балла |
| `scorePerTask` | расчёт балла по заданиям: `tasks[]` — `task` (псевдоним), `kind`, `level`, `weight`, `testsCounted`, `testsCountedPassed` (исправляющие тесты, у разминки — тесты части 2), `guardTests`, `guardTestsBroken` (проверки «ничего не сломано»), `codeUnchanged`, `share`, `note`; в сессиях до доработки — `hiddenTestsPassed`, `hiddenTestsTotal`; у задания, не проверенного из-за сбоя платформы, — `excluded` с пояснением; `note` — пометка о предварительности |

### tasks.jsonl — задания сессий
| Поле | Смысл |
|---|---|
| `task`, `session` | псевдонимы задания и его сессии |
| `orderNo` | номер в сессии (1 — разминка) |
| `kind` | `CALIBRATION` (разминка, в балл с весом 0,5 по тестам части 2) или `TASK` |
| `templateCode`, `variantCode` | шаблон и вариант задачи в банке задач (например, `T02`, `T02-v01`) |
| `level` | уровень варианта |
| `status` | `NOT_STARTED`, `IN_PROGRESS`, `SUBMITTED` |
| `startedAt`, `submittedAt` | открытие и отправка задания |
| `lastSavedCode` | последний сохранённый код: `{путь файла: содержимое}` (редактируемые файлы) |

### runs.jsonl — запуски кода
| Поле | Смысл |
|---|---|
| `task` | псевдоним задания |
| `mode` | `RUN` — видимые тесты, `SUBMIT` — отправка на скрытые тесты |
| `status` | `QUEUED`, `RUNNING`, `DONE`, `ERROR` (сбой платформы), `TIMEOUT` (превышено время) |
| `createdAt`, `startedAt`, `finishedAt` | создание, начало и конец выполнения |
| `code` | код, который запускался: `{путь: содержимое}` |
| `compiled`, `testsTotal`, `testsPassed` | результат; `null`, если его нет |
| `testCases` | тесты: `name`, `status` (`PASSED`, `FAILED`, `ERROR`, `SKIPPED`), `hidden` (скрытый тест), `message`; у скрытых тестов имя — «Скрытый тест N», `message` пуст |
| `durationMs` | время выполнения в песочнице |

### telemetry.jsonl — телеметрия ввода
Одна строка — один пакет, как его прислал браузер кандидата.

| Поле | Смысл |
|---|---|
| `task` | псевдоним задания |
| `seq` | номер пакета задания, с 0 |
| `clientTsStart`, `clientTsEnd` | интервал пакета на шкале задания, мс |
| `receivedAt` | приём сервером |
| `flags` | нарушения монотонности времени (`tNotMonotonic`, `outsideBatchRange`, `overlapsPreviousBatch`, `overlapsNextBatch`) |
| `events` | события: `t` (мс на шкале задания) и `type` с полями своего типа |

Шкала задания начинается при первом открытии задания; после перезагрузки страницы она продолжается с конца последнего пакета, а время, пока страница была закрыта, в неё не входит. Поэтому `t` нельзя переводить во время суток прибавлением к `startedAt`.

Типы событий:
| type | Поля | Смысл |
|---|---|---|
| `kd`, `ku` | `keyClass`, `repeat` | нажатие и отпускание клавиши; только класс (`letter`, `digit`, `space`, `enter`, `backspace`, `delete`, `tab`, `arrow`, `punct`, `bracket`, `modifier`, `other`), без символа |
| `edit` | `file`, `rangeOffset`, `rangeLength`, `text`, `textLength`, `source`, `isUndo`, `isRedo`, при `source: paste` — `ownCode` | изменение текста: диапазон `[rangeOffset, rangeOffset + rangeLength)` заменён на `text`; `source` — `typing`, `paste`, `completion`, `other` (отмена, повтор, вырезание) |
| `cursor` | `file`, `offset` | позиция курсора |
| `select` | `file`, `offset`, `length` | выделение |
| `paste`, `copy` | `file`, `length`, у `paste` — `ownCode` | вставка и копирование, только длина; `ownCode: true` — вставленный текст был в коде задачи, её условии или скопирован в редакторе |
| `completion` | `accepted`, `insertedLength` | принятая подсказка автодополнения |
| `visibility` | `state` | вкладка видна (`visible`) или скрыта (`hidden`) |
| `focus`, `blur` | — | окно получило или потеряло фокус |
| `run`, `submit` | — | нажатие «Запустить тесты» и «Отправить решение» |
| `resize` | `width`, `height` | размер редактора |

Файлы задания восстанавливаются применением событий `edit` по порядку (`seq`, затем порядок в пакете) к начальным файлам варианта из банка задач.

### indicators.jsonl — индикаторы достоверности
| Поле | Смысл |
|---|---|
| `task` | псевдоним задания |
| `trustLevel` | `GREEN`, `YELLOW`, `RED` по экспериментальным правилам v1.0 (у разминки в уровень сессии не входит) |
| `computedAt` | время расчёта |
| `indicators` | `{имя: {value, explanation}}`: `pasteRatio`, `largestPaste`, `externalPastes`, `focusLoss`, `burstMax`, `idleThenBurst`, `linearity`, `editRatio`, `timeToFirstRun`, `runsCount`, `telemetryEvents`, `trustReasons`, у разминки — `retyping`. Определения — в `docs/indicators.md` репозитория GITS |

### manifest.json
`format`, `exportedAt`, `from`/`to` (`null` — без ограничения), `timezone` и `counts` — число строк: `sessions`, `tasks`, `runs`, `telemetryBatches`, `indicators`. Пишется последним: если архив не открывается или в нём нет `manifest.json`, выгрузка прервалась — повторите её.
