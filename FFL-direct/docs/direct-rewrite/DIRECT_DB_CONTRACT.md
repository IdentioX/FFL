# Контракт Direct MySQL — проектирование P0

Актуализация финального аудита: Direct0.2.2 сравнивает пароль параметризованным `BINARY parol = BINARY ?` (без SELECT пароля и без изменения схемы). На восстановленной MariaDB прежнее `parol = ?` принимало другой регистр и хвостовой пробел. Это намеренное усиление относительно legacy SQL; остальные правила ниже сохранены. Сдача берёт shared lock строки access до блокировки заказа и держит его до commit; повторный snapshot SELECT не используется как доказательство отзыва. Подробности — `../FINAL_ENGINEERING_AUDIT.md`.

Статус: спецификация, не реализация. Первоисточник — [BACKEND_CONTRACT_REPORT.md](BACKEND_CONTRACT_REPORT.md), разделы 2–15; сверка с `/workspace/FFL-backend/app/` на commit из MASTER_CONTEXT. Ни одно SQL-выражение ниже в живой БД не выполнялось. Схема production не исследована.

## Границы и будущие типы

`Compose → ViewModels → Repositories → DirectMySqlDataSource → MySqlConnectionFactory → Legacy MySQL`.

Data source методы: `getDrivers`, `login`, `checkAccess`, `getOrders`, `getOrderById`, `getOrderDetails`, `markDelivered`, `getNotificationSummary`, `getPhoneDirectory`, `validateRouteOpened`. Repositories получают driverId из локального session context, а не из произвольного параметра UI. Backend URL, JWT, DriverApiService и сетевой fallback отсутствуют.

Domain OrderId: source enum `REYS`/`REYS_DEN` + положительный legacyId; wire/display identity `reys:<id>`/`reys_den:<id>`. Единственный allowlist таблиц: `reys → reys`, `reys_den → reys_den`. Не интерполировать имя таблицы из строки пользователя. Параметры данных — PreparedStatement; table enum выбирает заранее определённый SQL. ID в navigation, LazyColumn keys, dialog state, pending writes и local order store не преобразовывать в Int. Проверять переполнение; реальные типы/диапазоны БД неизвестны.

Источник `main` у водителя означает только `__hr_typepos`; `__hr_typepos_bf` не читать. Сессия хранит driverId, displayName и локальную версию состояния; не содержит пароль, backend JWT или DB password. Наличие локального driverId не является серверным доказательством аутентификации.

## Инвентарь таблиц/полей

| Источник | Используемые поля/функции | Назначение | Запись |
| --- | --- | --- | --- |
| `__hr_typepos` | posTypeId/PosTypeId, posTypeName/PosTypeName, fio, mobile, whatsapp, parol, access | список, login/access, история, справочник | Запрещена |
| `reys`, `reys_den` | id, nomer_z, id_v, name_c, col, vremya, date, status, col_s, sort_r, st_vv, date_att_log | первый/второй рейс, история, counters | Только st_vv/date_att_log, P6 |
| `meneger_contr_ffl` | id, nomer_z, link, ooo, adress, tell_klient, tell_gruzchik, id_m | кандидаты адресов/контактов | Запрещена |
| `meneger_ffl` | id, meneger, tel, short_tel | manager LEFT JOIN по id_m | Запрещена |
| `phone` | name, nomer_full | важные телефоны | Запрещена |
| `nastroiki` | variable, value | karta/imya_karta | Запрещена |
| `name_v(id_v)` | MySQL function, реализация неизвестна | driverName заказов/истории | Не заменять JOIN без подтверждения эквивалентности |

Имена подтверждены текстом SQL, но регистр, типы, nullability, engine, ключи, индексы, charset/collation и права — нет. Глобальный SELECT на __hr_typepos создаёт риск чтения parol, даже если клиент выбирает только безопасные столбцы.

## Список и авторизация водителей

Список: SELECT driver id/name, fio, mobile, whatsapp из `__hr_typepos`; условия `posTypeId IS NOT NULL`, `posTypeName IS NOT NULL`, `posTypeName <> ''`, `access = 1`; ORDER BY driverName. `parol` не должен появляться в списке/DTO. Не усиливать условие имени до TRIM без фиксации изменения: SQL report исключает пустую строку, не все пробельные строки.

Основной login — parameterized `WHERE posTypeId = ? AND parol = ? AND access = 1`, имя not null/не пустое, LIMIT 1. Возвращать только id/name. Не выбирать parol в приложение, если достаточно SQL-сравнения. Сравнение зависит от реальной collation и преобразований legacy пароля; серверное хранение пароля/правила не доказаны. Пароль только в краткоживущем вводе, очищать после успеха/ошибки/закрытия, не сохранять в saved state, DataStore, exception/logging.

На рестарте и перед каждой защищённой Direct-операцией читать текущий access по driverId. Отсутствующий водитель → завершить локальную сессию/повторный вход; access, не приводимый к 1 → `DriverDisabled` и состояние «Недоступно». Соединение/timeout не равно неверный пароль/disabled. Нельзя показывать старые данные нового водителя или продолжать запись после disabled. Повторные запросы и polling также проходят gate.

## Заказы и mapping

SELECT/UNION ALL по обеим таблицам с `date = ? AND id_v = ?`; не сканировать все заказы с клиентской фильтрацией. Current-driver ownership обязателен для одного заказа/деталей/route-opened/delivery. Чужой и отсутствующий заказ дают одинаковое безопасное not-found состояние. Детали и один заказ не имеют date-фильтра в backend — не добавлять его скрыто.

| Domain / DTO | Legacy и правило |
| --- | --- |
| orderId, legacyId, source | source enum + id; таблицы не смешивают identity |
| section/label | reys → FIRST_ROUTE/«Первый рейс»; reys_den → SECOND_ROUTE/«Второй рейс» |
| sortIndex | int(sort_r or 0) |
| orderNumber | trim nomer_z; null/пусто → «Без номера» |
| driverId/name | id_v / name_v(id_v); name nullable |
| clientName/clientInfo | нормализованный name_c или пустая строка |
| deliveryNote/plannedTime | нормализованный vremya; для note пустая строка, plannedTime nullable |
| deliveryDate | str(date), без скрытого timezone shift |
| invoiceCount/invoicesCount | int(col or 0); единственный счётчик накладных |
| notReadyInvoices | legacy alias int(col_s or 0), **не количество** |
| isReady/readinessLabel | status == 2 / «Готово»; 1 / «Не готово»; прочее / «Неизвестно» |
| isPartiallyReady | col_s == 1, независимо от status |
| quantityReadiness/quantityLabel | int col_s: 0 «Накладные не готовы», 1 «Частично готово», 2 «Готово», прочее «Неизвестный статус накладных» |
| isDelivered/deliveredAt | st_vv == 1; date_att_log строкой или null |
| canMarkDelivered | в backend только !isDelivered; дополнительный UI guard isReady — конфликт C06 |
| displayTitle | первые 80 символов clientInfo; Python и Kotlin Unicode slicing требуют parity cases |

ready filters: all — без дополнительного условия; ready → status=2; not_ready → status=1; partial → status=1 AND col_s=1. Не выводить main readiness из col_s/col. sort=id_desc backend принимает, но не использует: не обещать другой порядок без решения.

Базовый SQL order: `COALESCE(st_vv,0) ASC`, затем first/second section, затем null/0 sort_r в конец, sort_r ASC, id ASC. При обычных значениях st_vv 0/1 это active/delivered. Нестандартные st_vv не нормализовать незаметно: в summary используются CASE ==1, а в списке — сырой COALESCE. Для UI требуются три постоянных раздела, delivered из обоих источников — третий. Локальный reorder только в storage, ключ `(driverId, queryDate, section)`, IDs полные. Никакого UPDATE sort_r. Merge новых/исчезнувших заказов, переход в delivered, reset порядка и scroll restoration требуют reference/решения C01; до этого P4 не POSITIVE.

Дата — обязательный параметр. Backend не определяет current date. В Android жёстко задана `2026-05-18`; переход к «сегодня» и timezone должен быть согласован, а не выведен из даты машины. MySQL NOW timezone также неизвестен.

## Resolver адресов/контактов — точная parity

SQL: кандидаты из meneger_contr_ffl LEFT JOIN meneger_ffl ON m.id=c.id_m, `TRIM(c.nomer_z)=TRIM(?)`, ORDER BY c.id ASC. Пустой номер → not_found без запроса. В рамках загрузки списка кешировать кандидатов на каждый distinct нормализованный номер, но scoring выполнять отдельно по name_c каждой строки; нельзя кешировать готовый выбор только по номеру.

Нормализация сравнения: whitespace split/join; lower; ё→е; №→пробел; regex `[\"'\x60«»“”„.,;:()\[\]{}]+` → пробел; `[-/\\]+` → пробел; снова collapse spaces. Не удалять дополнительные символы или менять последовательность.

Токены address — set; исключить length<4, только цифры и stopwords: `г, город, москва, обл, область, ул, улица, д, дом, корп, корпус, стр, строение, пр, проспект, ш, шоссе, наб, набережная, пер, переулок, офис`.

Scoring кандидата:

1. Нет нормализованного текста заказа → 0/none.
2. Непустой adress целиком входит в order text → +120/high; **else if** непустой `ooo + adress` входит → +100/high. Не «оптимизировать» ветвь или менять приоритет.
3. Для каждого адресного token проверять `token in order_tokens OR token in order_text`; добавить `min(число совпадений,3)*50`. Если confidence ещё none → medium.
4. Сортировать score DESC, candidate id DESC. Этот tie-break **не разрешает неоднозначность**.
5. top<=0, несколько равных top, second>0 и разница<40, либо confidence=none → ambiguous. Не выбирать первый результат.

Ноль кандидатов → manual/not_found, все контакты/address null. Один → high, без scoring. При ambiguous → manual/ambiguous, confidence none, все контакты/address null. При выбранном кандидате address и контакты остаются даже с невалидной link; confidence high/medium.

URL: после whitespace normalization проверить case-insensitive prefix `http://`, `https://`, `yandexmaps://`, `yandexnavi://`. Дополнительная безопасная URI-валидация и обработка Intent не должны скрыто менять выбор кандидата. Valid → routeMode link, routeButtonLabel «Маршрут», routeUrl=link, routeText=selected address (может быть null). Blank/invalid → manual, «Ручной маршрут», routeUrl/routeText=null. Клиентский parsed name_c не заменяет resolver address. Без geocoding и внешних address API.

Контакты: tell_klient, tell_gruzchik; managerName=meneger; managerPhone=tel, если blank — short_tel. Отображаемые строки нормализованы по backend, сырые источники в БД не меняются.

## Детали, история и parsing

Текущий заказ только `id=? AND id_v=?`, затем resolver. История из **той же** source-table, `name_c = ?` точное SQL-сравнение, `id <> ?`, `date < current date`, ORDER BY date DESC,id DESC, LIMIT 5; join __hr_typepos ON PosTypeId=id_v, поля mobile и whatsapp отдельно, orderNumber и source-aware orderId сохранять. Не расширять историю на другой рейс и не исключать исторических inactive-водителей новым фильтром.

parse_order_text нормализует spaces. Earliest marker из `Москва, г.Москва, г. Москва, Московская обл, МО,, ул., улица, пр-т, проспект, шоссе, ш., наб., набережная, пер., переулок, дом, д., стр., строение, корп., корпус`. Title — до earliest marker, иначе первые 80 символов; пустой prefix → first80 fallback. Распарсенный адрес **не** итоговый адрес деталей: итог через resolver.

Время — первое case-insensitive regex-совпадение `\b(?:с\s*)?\d{1,2}(?::\d{2}|(?:\s*-\s*\d{1,2})?\s*ч)\b` в нормализованном vremya. PlannedTime сохраняет исходный нормализованный vremya. Comment — оставшиеся части, collapse spaces, trim ` ,.;:-`, пусто → null. Поддержать 11-13ч, 11-13 ч, с 12ч, 11 ч, 11:30, с 11:30 и отсутствие времени. Python/Kotlin regex Unicode parity проверить fixtures. Phone parsing из comment в backend отсутствует; Android ContactParsing в checkout тоже отсутствует — C02, не выдумывать алгоритм.

## Единственная запись: markDelivered

Это будущая спецификация, а не разрешение выполнить live write. Только явно одобренный пользователем безопасный тестовый заказ и ограниченный DB account. Любая production-запись запрещена текущим scope.

1. Получить driverId только из текущей локальной сессии; распарсить OrderId allowlist.
2. Открыть одну connection вне Main thread, начать transaction (`autoCommit=false`), проверить access=1. Стратегию конкурентного отзыва access/lock isolation подтвердить с DBA; не заявлять атомарную серверную авторизацию на основании клиентского SELECT.
3. `SELECT id, st_vv, date_att_log FROM <allowlisted_table> WHERE id=? AND id_v=? FOR UPDATE`.
4. Нет строки → rollback/not-found, без UPDATE. Уже st_vv==1 → commit/already_delivered, timestamp не менять.
5. `UPDATE <allowlisted_table> SET st_vv=1, date_att_log=NOW() WHERE id=? AND id_v=? AND COALESCE(st_vv,0)<>1`.
6. Проверить affected rows=1; повторно прочитать id/st_vv/date_att_log с тем же ownership, подтвердить delivered. Не принимать null/0 rows за успех.
7. Только после проверок commit; любое исключение/timeout/cancel → rollback при доступной connection, закрыть ResultSet/Statement/Connection. Уточнить поведение при потере соединения в commit: результат может быть неизвестен, **не** повторять запись вслепую; сначала безопасный ownership read.

Поля вне разрешённого SET: status, col_s, col, sort_r, date, id_v, nomer_z, name_c, vremya и всё остальное. Не создавать таблицы/миграции/логирование. Backend в текущей реализации проверяет access раньше, отдельно от write connection, и коммитит до проверки updated_row; Direct обязан выполнять заданные пользователем более строгие postconditions, не переносить эти ограничения как образец безопасности. Гарантии FOR UPDATE зависят от engine/транзакций живой схемы — неизвестны.

## Route-opened

Проверить текущий access и `id + id_v` в allowlisted source. Успех → ownership validation + no-op. Никаких INSERT/UPDATE, новых event tables, HTTP telemetry или backend fallback. Не логировать адрес, пароль, connection string, телефон или полный заказ. Когда проверять ownership относительно открытия внешней навигации — согласовать UX/error-path в P5, не добавлять блокирующий write.

## Notification summary и телефонный справочник

Summary по заданным date/driver: firstRouteCount — активные reys; secondRouteCount — активные reys_den; deliveredCount — st_vv==1 обеих; activeTotal=sum active; secondRouteOrderIds — активные `reys_den:ID`, sort_r null/0 last, sort_r ASC,id ASC. Только read, access gate. Два SELECT backend не гарантируют один snapshot; consistency-требования и временные изменения между ними учитывать в P8.

Directory глобальный для активного водителя: nastroiki MAX(CASE variable='karta'/'imya_karta' THEN value END), missing→null; phone name/nomer_full, не blank, ORDER BY name; __hr_typepos active, name not empty, phone mobile если nonblank, иначе whatsapp, без номера строку пропустить. Значения directory backend не trim/split/deduplicate при выдаче, а только проверяет blank. Без скрытого форматирования/UPDATE. Copy/dial — UI-действия отдельно.

## Connection layer и безопасные ошибки

Controlled development config names: DIRECT_MYSQL_HOST, DIRECT_MYSQL_PORT, DIRECT_MYSQL_DATABASE, DIRECT_MYSQL_USER, DIRECT_MYSQL_PASSWORD, DIRECT_MYSQL_USE_SSL. Источники — ignored local.properties или безопасная Gradle injection; требовать полный набор до соединения, валидировать port, не выводить values. Отсутствующая конфигурация → безопасное состояние конфигурации, а не ввод host/password водителем.

TLS должен проверять CA и hostname; USE_SSL=true не означает, что проверка выполнена. Непроверенный сертификат/hostname → отказ, без downgrade. Нужен отдельный дизайн trust material, endpoint discovery/VPN и auth-plugin совместимости в P2. В report режим `required` backend отключает cert verification: это не переносится в новую реализацию. Драйвер/версия не выбраны и не объявлены Android-совместимыми по desktop JVM тесту.

Сетевые SQL только в Dispatchers.IO/ограниченном dispatcher, никакого runBlocking в UI. Подтвердить connect/socket/query/network timeouts средствами выбранного драйвера. Ограниченная конкурентность, scoped connections без singleton shared Connection; закрытие всех ресурсов на любой ветке. Coroutine cancellation сама не гарантирует отмену JDBC. Read retries ограничены и только для transient failures; write retry после неизвестного commit запрещён. Пользовательские ошибки: configuration missing, connection unavailable, TLS rejected, driver disabled, invalid credentials, not found, delivery result unknown; без SQL/host/credentials.

Блокеры реальных проверок: тестовый endpoint/схема/credentials/grants, CA/hostname, сетевой маршрут, тестовый driver/password и явно разрешённый безопасный test order отсутствуют. Данные не заменять вымышленными секретами.
