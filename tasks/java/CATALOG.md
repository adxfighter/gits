# Каталог банка задач

Файл создаётся командой `gits-taskbank stats tasks/java --catalog tasks/java/CATALOG.md` по task.yaml, template.yaml, тестам, validation.json и REVIEW.md. Не редактируйте его вручную.

Вариантов задач: 50, калибровочных: 3.

## Варианты

Оценки ревью (1–5): ясность / реалистичность / уровень / скрытые тесты / уникальность / русский язык.
Число тестов — число различных тестовых методов (параметризованный тест считается один раз), так же считает валидатор. «Прогон, мс» — самый долгий прогон эталона при последней валидации.

| Код | Шаблон | Домен | Уровень | Компетенции | Параметры сложности | Видимых | Скрытых | Прогон, мс | Ревью |
|---|---|---|---|---|---|---|---|---|---|
| T01-v01 | T01 | банк | junior | java.concurrency.atomicity, java.concurrency.visibility | threads=4, shared_fields=1, compound_ops=false, defects_count=1 | 2 | 5 | 4724 | 5/5/5/4/5/5 |
| T01-v02 | T01 | телеком | junior | java.concurrency.atomicity, java.concurrency.visibility | threads=8, shared_fields=2, compound_ops=false, defects_count=2 | 2 | 5 | 4294 | 4/5/4/4/4/4 |
| T01-v03 | T01 | интернет-магазин | junior | java.concurrency.atomicity, java.concurrency.visibility | threads=8, shared_fields=1, compound_ops=true, defects_count=1 | 2 | 5 | 3604 | 5/5/5/5/5/5 |
| T01-v04 | T01 | энергетика | middle | java.concurrency.atomicity, java.concurrency.visibility | threads=16, shared_fields=1, compound_ops=true, defects_count=1 | 2 | 5 | 4505 | 5/5/4/4/5/5 |
| T01-v05 | T01 | образование | middle | java.concurrency.atomicity, java.concurrency.visibility | threads=8, shared_fields=3, compound_ops=true, defects_count=2 | 2 | 5 | 4132 | 4/5/4/4/3/4 |
| T02-v01 | T02 | банк | junior | java.concurrency.locking, java.concurrency.atomicity | resources=2, call_depth=1, nested_locks=false | 2 | 5 | 3524 | 4/5/5/4/4/4 |
| T02-v02 | T02 | логистика | middle | java.concurrency.locking, java.concurrency.atomicity | resources=3, call_depth=1, nested_locks=false | 2 | 6 | 3998 | 4/5/3/5/3/5 |
| T02-v03 | T02 | телеком | middle | java.concurrency.locking, java.concurrency.atomicity | resources=3, call_depth=2, nested_locks=true | 2 | 5 | 6622 | 4/5/5/5/5/5 |
| T02-v04 | T02 | медицина | senior | java.concurrency.locking, java.concurrency.atomicity | resources=5, call_depth=3, nested_locks=true | 2 | 6 | 6102 | 4/5/5/5/5/5 |
| T02-v05 | T02 | энергетика | senior | java.concurrency.locking, java.concurrency.atomicity | resources=5, call_depth=2, nested_locks=true | 2 | 5 | 6139 | 4/5/3/5/3/5 |
| T03-v01 | T03 | интернет-магазин | junior | java.memory.caching, java.design.api | eviction=capacity, ttl=false, loc=60 | 2 | 5 | 4187 | 4/5/5/5/3/4 |
| T03-v02 | T03 | медицина | junior | java.memory.caching, java.design.api | eviction=capacity, ttl=false, loc=60 | 2 | 6 | 5602 | 3/4/4/5/3/5 |
| T03-v03 | T03 | банк | junior | java.memory.caching, java.design.api | eviction=capacity, ttl=false, loc=96 | 2 | 5 | 3904 | 4/5/4/5/4/5 |
| T03-v04 | T03 | логистика | middle | java.memory.caching, java.design.api | eviction=capacity_ttl, ttl=true, loc=120 | 2 | 5 | 3828 | 4/5/4/4/5/5 |
| T03-v05 | T03 | телеком | middle | java.memory.caching, java.design.api | eviction=capacity_ttl, ttl=true, loc=105 | 2 | 6 | 4163 | 4/5/4/4/5/5 |
| T04-v01 | T04 | образование | junior | java.memory.leaks, java.concurrency.executors | leak_sources=1, thread_pool=false, nested_context=false | 2 | 5 | 4239 | 4/5/5/5/5/4 |
| T04-v02 | T04 | интернет-магазин | middle | java.memory.leaks, java.concurrency.executors | leak_sources=1, thread_pool=false, nested_context=false | 2 | 5 | 4562 | 5/5/4/5/5/5 |
| T04-v03 | T04 | банк | middle | java.memory.leaks, java.concurrency.executors | leak_sources=1, thread_pool=true, nested_context=false | 2 | 5 | 3913 | 4/4/4/5/4/5 |
| T04-v04 | T04 | телеком | senior | java.memory.leaks, java.concurrency.executors | leak_sources=2, thread_pool=true, nested_context=true | 3 | 11 | 3887 | 4/5/5/5/5/5 |
| T04-v05 | T04 | медицина | senior | java.memory.leaks, java.concurrency.executors | leak_sources=1, thread_pool=true, nested_context=true | 2 | 6 | 4158 | 4/5/3/5/3/5 |
| T05-v01 | T05 | интернет-магазин | junior | java.refactoring.legacy, java.basics.conditions | loc=152, cyclomatic=35, rules=6 | 4 | 10 | 6618 | 5/5/4/5/5/5 |
| T05-v02 | T05 | логистика | middle | java.refactoring.legacy, java.basics.conditions | loc=164, cyclomatic=26, rules=5 | 4 | 7 | 7359 | 4/5/5/5/5/5 |
| T05-v03 | T05 | телеком | middle | java.refactoring.legacy, java.basics.conditions | loc=152, cyclomatic=29, rules=5 | 4 | 11 | 9600 | 5/5/5/5/5/5 |
| T05-v04 | T05 | энергетика | senior | java.refactoring.legacy, java.basics.conditions | loc=154, cyclomatic=35, rules=7 | 3 | 14 | 4640 | 4/5/5/5/5/5 |
| T05-v05 | T05 | банк | senior | java.refactoring.legacy, java.basics.conditions | loc=150, cyclomatic=35, rules=7 | 4 | 7 | 5490 | 5/5/5/5/5/5 |
| T06-v01 | T06 | образование | junior | java.collections.contracts, java.design.api | key_fields=2, inheritance=false, defect_type=missing_hashcode | 2 | 5 | 5690 | 3/4/4/4/4/5 |
| T06-v02 | T06 | медицина | junior | java.collections.contracts, java.design.api | key_fields=2, inheritance=false, defect_type=mutable_key | 2 | 7 | 6778 | 4/5/5/4/5/5 |
| T06-v03 | T06 | интернет-магазин | junior | java.collections.contracts, java.design.api | key_fields=1, inheritance=false, defect_type=reference_equality | 2 | 5 | 5237 | 4/4/5/4/4/5 |
| T06-v04 | T06 | банк | middle | java.collections.contracts, java.design.api | key_fields=2, inheritance=true, defect_type=asymmetric_equals | 2 | 5 | 4729 | 5/4/4/4/5/5 |
| T06-v05 | T06 | логистика | middle | java.collections.contracts, java.design.api | key_fields=3, inheritance=false, defect_type=inconsistent_fields | 2 | 7 | 4492 | 5/5/4/5/5/5 |
| T07-v01 | T07 | образование | junior | java.streams.api, java.testing.boundaries | pipeline_ops=3, edge_cases=2 | 2 | 5 | 9947 | 4/5/5/5/4/5 |
| T07-v02 | T07 | интернет-магазин | junior | java.streams.api, java.testing.boundaries | pipeline_ops=5, edge_cases=2 | 2 | 6 | 5417 | 5/5/5/5/5/5 |
| T07-v03 | T07 | банк | middle | java.streams.api, java.testing.boundaries | pipeline_ops=5, edge_cases=4 | 2 | 7 | 7243 | 5/5/4/5/5/5 |
| T07-v04 | T07 | медицина | middle | java.streams.api, java.testing.boundaries | pipeline_ops=7, edge_cases=3 | 2 | 7 | 7874 | 4/5/5/5/5/5 |
| T07-v05 | T07 | энергетика | middle | java.streams.api, java.testing.boundaries | pipeline_ops=7, edge_cases=4 | 2 | 6 | 7022 | 4/5/4/5/5/5 |
| T08-v01 | T08 | интернет-магазин | middle | java.concurrency.async, java.concurrency.executors | sources=2, timeouts=false, partial_result=true | 2 | 6 | 5302 | 5/5/4/5/3/5 |
| T08-v02 | T08 | логистика | middle | java.concurrency.async, java.concurrency.executors | sources=3, timeouts=false, partial_result=true | 2 | 5 | 4811 | 5/5/4/4/3/4 |
| T08-v03 | T08 | банк | senior | java.concurrency.async, java.concurrency.executors | sources=3, timeouts=true, partial_result=true | 2 | 7 | 5624 | 5/5/5/4/4/5 |
| T08-v04 | T08 | телеком | senior | java.concurrency.async, java.concurrency.executors | sources=2, timeouts=true, partial_result=false | 2 | 7 | 8001 | 5/5/4/4/5/5 |
| T08-v05 | T08 | медицина | senior | java.concurrency.async, java.concurrency.executors | sources=4, timeouts=true, partial_result=true | 2 | 7 | 5307 | 5/5/5/4/4/5 |
| T09-v01 | T09 | телеком | middle | java.concurrency.atomicity, java.design.api | threads=1, multi_key=false, permits=single | 2 | 6 | 8059 | 4/5/4/4/5/5 |
| T09-v02 | T09 | интернет-магазин | middle | java.concurrency.atomicity, java.design.api | threads=4, multi_key=true, permits=single | 2 | 6 | 5155 | 4/5/5/4/4/5 |
| T09-v03 | T09 | банк | senior | java.concurrency.atomicity, java.design.api | threads=8, multi_key=false, permits=single | 2 | 5 | 5405 | 4/5/3/4/4/5 |
| T09-v04 | T09 | энергетика | senior | java.concurrency.atomicity, java.design.api | threads=4, multi_key=true, permits=multiple | 2 | 7 | 4970 | 5/5/5/5/4/5 |
| T09-v05 | T09 | логистика | senior | java.concurrency.atomicity, java.design.api | threads=1, multi_key=true, permits=multiple | 2 | 8 | 5772 | 5/5/3/5/4/5 |
| T10-v01 | T10 | логистика | middle | java.concurrency.executors, java.concurrency.visibility | producers=1, consumers=2, bounded_queue=false | 2 | 6 | 5840 | 4/5/4/5/5/5 |
| T10-v02 | T10 | телеком | middle | java.concurrency.executors, java.concurrency.visibility | producers=2, consumers=2, bounded_queue=false | 2 | 5 | 7175 | 5/5/5/4/5/5 |
| T10-v03 | T10 | банк | senior | java.concurrency.executors, java.concurrency.visibility | producers=4, consumers=2, bounded_queue=true | 2 | 5 | 5916 | 5/5/5/5/4/5 |
| T10-v04 | T10 | энергетика | senior | java.concurrency.executors, java.concurrency.visibility | producers=2, consumers=4, bounded_queue=true | 2 | 6 | 6298 | 5/5/4/5/4/5 |
| T10-v05 | T10 | медицина | senior | java.concurrency.executors, java.concurrency.visibility | producers=2, consumers=2, bounded_queue=true | 2 | 6 | 4666 | 5/5/4/5/5/5 |
| CAL-v01 | CAL | телеком | калибровка | java.basics.strings | fragment_lines=26, short_task=phone | 4 | 0 | 3895 | не оценивается |
| CAL-v02 | CAL | образование | калибровка | java.basics.strings | fragment_lines=28, short_task=name | 4 | 0 | 4407 | не оценивается |
| CAL-v03 | CAL | логистика | калибровка | java.basics.strings | fragment_lines=29, short_task=duration | 4 | 0 | 4188 | не оценивается |

## Распределение по уровням

| Шаблон | Название | junior | middle | senior | Всего |
|---|---|---|---|---|---|
| T01 | Состояние гонки в счётчике и агрегаторе | 3 | 2 | 0 | 5 |
| T02 | Взаимная блокировка при переводах между ресурсами | 1 | 2 | 2 | 5 |
| T03 | Утечка памяти в кэше | 3 | 2 | 0 | 5 |
| T04 | Утечки через слушателей и ThreadLocal | 1 | 2 | 2 | 5 |
| T05 | Рефакторинг унаследованного расчёта стоимости | 1 | 2 | 2 | 5 |
| T06 | Контракт equals/hashCode и изменяемые ключи | 3 | 2 | 0 | 5 |
| T07 | Stream API и Optional в отчёте | 2 | 3 | 0 | 5 |
| T08 | Асинхронная агрегация на CompletableFuture | 0 | 2 | 3 | 5 |
| T09 | Ограничитель частоты запросов (token bucket) | 0 | 2 | 3 | 5 |
| T10 | Производитель–потребитель и корректная остановка пула | 0 | 2 | 3 | 5 |
| **Итого** |  | 14 | 21 | 15 | 50 |

## Распределение по компетенциям

| Компетенция | Название | junior | middle | senior | Всего |
|---|---|---|---|---|---|
| java.basics.conditions | Условия и граничные значения | 1 | 2 | 2 | 5 |
| java.collections.contracts | Контракты equals/hashCode и коллекции | 3 | 2 | 0 | 5 |
| java.concurrency.async | Асинхронные вычисления (CompletableFuture) | 0 | 2 | 3 | 5 |
| java.concurrency.atomicity | Атомарность операций над общим состоянием | 4 | 6 | 5 | 15 |
| java.concurrency.executors | Пулы потоков и корректная остановка | 1 | 6 | 8 | 15 |
| java.concurrency.locking | Блокировки и взаимные блокировки | 1 | 2 | 2 | 5 |
| java.concurrency.visibility | Видимость изменений между потоками | 3 | 4 | 3 | 10 |
| java.design.api | Проектирование API класса | 6 | 6 | 3 | 15 |
| java.memory.caching | Кэширование и ограничение памяти | 3 | 2 | 0 | 5 |
| java.memory.leaks | Утечки памяти через ссылки и ThreadLocal | 1 | 2 | 2 | 5 |
| java.refactoring.legacy | Сопровождение и рефакторинг унаследованного кода | 1 | 2 | 2 | 5 |
| java.streams.api | Stream API и Optional | 2 | 3 | 0 | 5 |
| java.testing.boundaries | Тестирование граничных случаев | 2 | 3 | 0 | 5 |

## Распределение по доменам

| Домен | Вариантов |
|---|---|
| банк | 10 |
| интернет-магазин | 8 |
| логистика | 7 |
| медицина | 7 |
| образование | 4 |
| телеком | 8 |
| энергетика | 6 |

## Известные ограничения

- Решение кандидата выполняется в одной JVM с тестами JUnit; целостность результата обеспечивается изоляцией песочницы и сверкой отчёта (docs/adr/0004-runner.md).
- Многопоточные задачи проверяются многократными прогонами (`flaky_policy`); редкий провал эталона на перегруженной машине возможен и выявляется повторной валидацией.
- Требования «без глобальной блокировки» и стиль кода проверяются не только тестами, но и пунктами рубрики для ручной проверки.
