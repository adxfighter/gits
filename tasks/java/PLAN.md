# План вариантов банка задач (профиль «Java-разработчик»)

Каждый шаблон — 5 вариантов в разных доменах. Уровни внутри шаблона: 1–2 ниже базового, 2 базовых, 1–2 выше. Итого: junior — 14, middle — 21, senior — 15. Для любого целевого уровня подбор (P07) находит задачи из трёх разных шаблонов.

Пакет варианта: `ru.gits.task.<домен>.<шаблон>` (bank, logistics, shop, telecom, medicine, energy, education).

| Вариант | Домен | Уровень | Параметры сложности | Суть дефекта / задания |
|---|---|---|---|---|
| T01-v01 | банк | junior | threads=4, shared_fields=1, compound_ops=false, defects=1 | Счётчик обработанных платежей: `count++` без синхронизации |
| T01-v02 | телеком | junior | threads=8, shared_fields=2, compound_ops=false, defects=2 | Агрегатор трафика: байты и пакеты суммируются неатомарно (два поля) |
| T01-v03 | интернет-магазин | junior | threads=8, shared_fields=1, compound_ops=true, defects=1 | Резервирование остатка «проверить и уменьшить» — перепродажа товара |
| T01-v04 | энергетика | middle | threads=16, shared_fields=1, compound_ops=true, defects=1 | Суммирование показаний по счётчикам в HashMap через get+put |
| T01-v05 | образование | middle | threads=8, shared_fields=3, compound_ops=true, defects=2 | Статистика посещений (count/sum/max) синхронизирована частично, агрегат несогласован |
| T02-v01 | банк | junior | resources=2, call_depth=1, nested_locks=false | Перевод между двумя счетами: блокировки в порядке аргументов |
| T02-v02 | логистика | middle | resources=3, call_depth=1, nested_locks=false | Перемещение товаров между тремя складами, встречные перемещения |
| T02-v03 | телеком | middle | resources=3, call_depth=2, nested_locks=true | Взаиморасчёты операторов: второй замок захватывается во вспомогательном методе |
| T02-v04 | медицина | senior | resources=5, call_depth=3, nested_locks=true | Перераспределение коек между отделениями, замки на трёх уровнях вызовов |
| T02-v05 | энергетика | senior | resources=5, call_depth=2, nested_locks=true | Перетоки мощности между узлами сети, пакетный перевод по нескольким узлам |
| T03-v01 | интернет-магазин | junior | eviction=capacity, ttl=false, loc=60 | Кэш цен товаров на HashMap без ограничения размера |
| T03-v02 | медицина | junior | eviction=capacity, ttl=false, loc=60 | Кэш карточек пациентов растёт с каждым запросом |
| T03-v03 | банк | junior | eviction=capacity, ttl=false, loc=96 | Кэш курсов валют: вытесняется не самая давняя по использованию запись |
| T03-v04 | логистика | middle | eviction=capacity_ttl, ttl=true, loc=120 | Кэш маршрутов с истечением срока жизни, срок проверяется только при записи |
| T03-v05 | телеком | middle | eviction=capacity_ttl, ttl=true, loc=105 | Кэш сессий с мемоизацией загрузки и статистикой попаданий, статистика удерживает ключи |
| T04-v01 | образование | junior | leak_sources=1, thread_pool=false, nested_context=false | Подписка на события курса: закрытие подписки не удаляет слушателя |
| T04-v02 | интернет-магазин | middle | leak_sources=1, thread_pool=false, nested_context=false | Виджеты цен отписываются новым экземпляром лямбды — слушатель остаётся |
| T04-v03 | банк | middle | leak_sources=1, thread_pool=true, nested_context=false | Контекст пользователя в ThreadLocal «переезжает» в следующий запрос пула |
| T04-v04 | телеком | senior | leak_sources=2, thread_pool=true, nested_context=true | Утечка слушателей и ThreadLocal-контекста при ошибке тарификации; вложенная тарификация переадресованного плеча затирает внешний лицевой счёт; ошибка повторного расчёта подменяет исходную |
| T04-v05 | медицина | senior | leak_sources=1, thread_pool=true, nested_context=true | Вложенные контексты врача: внутренний вызов не восстанавливает внешний контекст |
| T05-v01 | интернет-магазин | junior | loc=152, cyclomatic=35, rules=6 | Стоимость заказа (курьер/почта/самовывоз × промокоды): в курьерской ветке скидка за объём не применяется, если введён любой промокод |
| T05-v02 | логистика | middle | loc=164, cyclomatic=26, rules=5 | Стоимость доставки: для срочной межгородней доставки хрупкого груза надбавка за хрупкость входит в итог дважды (через промежуточную сумму и отдельно) |
| T05-v03 | телеком | middle | loc=152, cyclomatic=29, rules=5 | Счёт абонента: междугородние звонки в выходные неверно списывают минуты пакета — пакет расходуется медленнее положенного |
| T05-v04 | энергетика | senior | loc=154, cyclomatic=35, rules=7 | Счёт по зонам суток: стоимость зонных тарифов относит час к месяцу по его началу через проверку (from, to], рассчитанную на конец часа — первый ночной час месяца оплачивается в предыдущем месяце и сдвигает соцнорму и превышение |
| T05-v05 | банк | senior | loc=150, cyclomatic=35, rules=7 | Комиссия по кредиту: онлайн-скидка зарплатным клиентам (ветка switch с проваливанием) вычитается после ограничений min/max — комиссия ниже минимума и недобор у максимума |
| T06-v01 | образование | junior | key_fields=2, inheritance=false, defect=missing_hashcode | Студент в HashSet: equals без hashCode — дубликаты в списке группы |
| T06-v02 | медицина | junior | key_fields=2, inheritance=false, defect=mutable_key | Ключ записи на приём меняется после вставки — запись «теряется» в карте |
| T06-v03 | интернет-магазин | junior | key_fields=1, inheritance=false, defect=reference_equality | Артикул сравнивается через == — корзина не находит товар |
| T06-v04 | банк | middle | key_fields=2, inheritance=true, defect=asymmetric_equals | Счёт и валютный счёт: equals несимметричен при наследовании |
| T06-v05 | логистика | middle | key_fields=3, inheritance=false, defect=inconsistent_fields | Ключ отправления: hashCode учитывает поле, которого нет в equals, и не согласован с equals по регистру трек-номера |
| T07-v01 | образование | junior | pipeline_ops=3, edge_cases=2 | Средний балл по курсам: неверный ключ группировки и ошибка на пустом курсе |
| T07-v02 | интернет-магазин | junior | pipeline_ops=5, edge_cases=2 | Топ-N товаров: сортировка по возрастанию и потеря товаров с равными продажами |
| T07-v03 | банк | middle | pipeline_ops=5, edge_cases=4 | Помесячный отчёт по операциям: Optional.get() на пустом месяце, неверная сортировка месяцев, группировка по месяцу без года |
| T07-v04 | медицина | middle | pipeline_ops=7, edge_cases=3 | Визиты пациентов: дубликаты после flatMap, исключён последний день периода, число уникальных врачей считается по визитам вне периода |
| T07-v05 | энергетика | middle | pipeline_ops=7, edge_cases=4 | Статистика потребления: неверное тождество в reduce и среднее по пустым данным |
| T08-v01 | интернет-магазин | middle | sources=2, timeouts=false, partial_result=true | Цены двух поставщиков: сбой одного роняет весь ответ |
| T08-v02 | логистика | middle | sources=3, timeouts=false, partial_result=true | Тарифы трёх перевозчиков: join() внутри цепочки и потеря ответов при ошибке |
| T08-v03 | банк | senior | sources=3, timeouts=true, partial_result=true | Кредитный скоринг трёх бюро: нет таймаута, медленное бюро задерживает решение |
| T08-v04 | телеком | senior | sources=2, timeouts=true, partial_result=false | Проверка номера: join() внутри thenApply вместо thenCompose, вычисления в общем пуле, нет таймаутов шагов |
| T08-v05 | медицина | senior | sources=4, timeouts=true, partial_result=true | Результаты четырёх лабораторий: allOf().join() и потеря исключения |
| T09-v01 | телеком | middle | threads=1, multi_key=false, permits=single | Ограничение SMS: целочисленное деление теряет токены при пополнении |
| T09-v02 | интернет-магазин | middle | threads=4, multi_key=true, permits=single | Лимиты API по клиентам: карта вёдер небезопасна при параллельных запросах |
| T09-v03 | банк | senior | threads=8, multi_key=false, permits=single | Лимит платёжных операций: гонка между проверкой и списанием токена |
| T09-v04 | энергетика | senior | threads=4, multi_key=true, permits=multiple | Квоты телеметрии: tryAcquire(n) частично списывает токены при отказе |
| T09-v05 | логистика | senior | threads=1, multi_key=true, permits=multiple | Лимит заказов перевозчика: пополнение сверх ёмкости после простоя |
| T10-v01 | логистика | middle | producers=1, consumers=2, bounded_queue=false | Обработка заказов: один «ядовитый» элемент на двух потребителей — зависание |
| T10-v02 | телеком | middle | producers=2, consumers=2, bounded_queue=false | Запись детализации звонков: shutdownNow теряет принятые записи |
| T10-v03 | банк | senior | producers=4, consumers=2, bounded_queue=true | Платёжный конвейер: производитель навсегда блокируется на put после остановки |
| T10-v04 | энергетика | senior | producers=2, consumers=4, bounded_queue=true | Приём телеметрии: потребитель завершается по isEmpty() и теряет элементы |
| T10-v05 | медицина | senior | producers=2, consumers=2, bounded_queue=true | Очередь анализов: прерывание проглатывается, остановка зависает |

## Калибровочный блок (не оценивается)

| Вариант | Домен | Уровень | Параметры | Суть |
|---|---|---|---|---|
| CAL-v01 | телеком | junior | fragment_lines=26, short_task=phone | Перепечатка фрагмента; нормализация телефонного номера |
| CAL-v02 | образование | junior | fragment_lines=28, short_task=name | Перепечатка фрагмента; форматирование ФИО |
| CAL-v03 | логистика | junior | fragment_lines=29, short_task=duration | Перепечатка фрагмента; разбор длительности «1h 30m» |
