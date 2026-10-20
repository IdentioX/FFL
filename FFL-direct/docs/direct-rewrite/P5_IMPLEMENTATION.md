# P5 — детали, адреса, контакты

Текущий пользовательский waiver: «Ты пока пропускай эти блоки. У меня сейчас нету возможности проверять на реальных». Принят как разрешение реализовать P5 и выполнить доступные локальные проверки без ожидания external gates. Повторяющееся STOP/P4 условие проверено: P4 действительно BLOCKED, не реализован. Его статус не изменён и POSITIVE не выдуман. Полная приёмка P5 остаётся отдельной от работоспособности локального кода; фактические команды/результаты — в PROGRESS.md и evidence/p5.

## Вход из будущего P4

`OrderDetailsDestination.open(navController, OrderId)` открывает внутренний Compose destination `orders/{orderId}`. Он защищён текущим auth state и repository access/ownership проверками. Нет внешнего exported deep link, ввода DB/backend реквизитов или фиктивного списка заказов. **Пока P4 отсутствует, в пользовательском home нет перехода из списка в детали.** Компонент деталей проверяется отдельно на контролируемых данных; это не сквозная проверка приложения. Три раздела, дата списка, reorder и scroll списка не добавлялись в P5. Из P4 введён только необходимый общий тип source-aware OrderId.

OrderId хранит enum REYS/REYS_DEN и positive Long, сохраняет source в истории/навигации. Parse поддерживает canonical ASCII decimal, leading zeros/`+`/внешние пробелы, отклоняет overflow/другой source/injection. Экзотические Python int формы с underscore/Unicode digits специально не принимаются; нормальный backend output canonical. Legacy range/collation требуют реальной схемы.

## Чтения и session lifecycle

`DirectMySqlOrderDetailsDataSource` использует существующий MySqlReadExecutor: IO, общий Semaphore(2), TLS, таймауты, readOnly, scoped Connection/PreparedStatement/ResultSet, safe enum errors. P2 connection factory и старые P3 auth/session классы не менялись. AppGraph дополнен общим session context и details repository.

- Детали: enum выбирает таблицу; `id=? AND id_v=?`, без date-фильтра. Current driver берётся из repository session, а не из UI. Проверяются возвращённые ID. Только необходимые для DTO столбцы; name_v вызывается для истории, где имя используется.
- История из той же source-table: исходный **ненормализованный** name_c, `id<>?`, `date<?`, `ORDER BY date DESC,id DESC LIMIT 5`, LEFT JOIN __hr_typepos mobile/whatsapp отдельно. Нет нового access-фильтра для исторических водителей. NULL driver_id считается data error, а не превращается в вымышленного водителя0. JDBC maxRows=5 и mapper limit=5. При null name_c SQL equality сохраняет поведение backend.
- Resolver query: `TRIM(c.nomer_z)=TRIM(?)`, LEFT JOIN manager, ORDER BY candidate id. Пустой номер не вызывает query. Нет глобального кеша выбора адреса; каждый details load получает кандидатов и оценивает текущий name_c заново. List-scoped candidate cache — будущий P4, его здесь нет.
- До/после чтений повторяются access; перед возвратом повторно проверяется ownership. Repository сравнивает revision/session до/после асинхронной работы; logout/смена пользователя/повторный login не публикуют поздний результат. Это клиентские меры, не серверная driver isolation/атомарная гарантия против внешнего SQL.
- Route-opened — отдельный parameterized ownership SELECT после access, без события/UPDATE/HTTP. Перед открытием маршрута ViewModel заново загружает детали и выполняет validation, чтобы не открывать ранее сохранённый чужой/устаревший адрес.
- На ON_STOP/dispose details job отменяется, данные скрываются. На ON_START данные читаются заново; Main coroutine не выполняет JDBC. Пароли/JWT/реквизиты не добавлены в model/navigation/storage/logging. Модели с адресами/телефонами имеют redacted toString.

## Resolver parity и mapping

Нормализация/scoring/stopwords/thresholds соответствуют текущим чистым функциям backend: whitespace/lower/ё/№/punctuation/separators; address +120, else combined +100, token matches +50 с cap=3, score/id DESC. Tie-break id не разрешает одинаковые top scores. top<=0, tie, positive second с delta<40, confidence none → AMBIGUOUS, все адресные/контактные поля null. Один кандидат → high без scoring. Invalid/blank link сохраняет выбранные address/contacts, но даёт manual. Четыре схемы links сохранены.

`OrderTextParsing`: earliest address marker только для title, Unicode code-point first80, первый time regex match и comment после его удаления. Python Unicode word boundaries заданы явно через letters/numbers/underscore; Nd digits и CASE_INSENSITIVE/UNICODE_CASE поддерживаются Android без запрещённого flag256. Регрессии покрывают combining marks/connector punctuation, где Java Unicode word class отличается от Python. plannedTime сохраняет полное нормализованное vremya; rawClientText/rawDeliveryNote хранят исходный текст отдельно. Итоговый address приходит **только из resolver**, не из parsed client text. status, col_s, col, st_vv независимы; unknown labels не теряются. Доставка не реализуется, canMarkDelivered — только parity property.

Offline `tools/p5_generate_golden.py` извлекает через AST только перечисленные чистые backend функции/константы, без импорта app/config/database и без сети. Генерирует 119 resolver cases и 644 text cases. Expected outputs получены от неизменённого backend, source/fixture hashes сохранены. Фикстуры синтетические, не реальные адреса/номера. Сравнение не доказывает live schema/SQL/server behavior. При текущих весах score кратен 10 и не все boundary values (39/40) достижимы; дополнительно проверяется реальный positive gap20 и совпавшие top scores.

## Локальный UX и внешние действия

Секции деталей следуют имеющемуся экрану reference: номер/source/title/client/address/time/comment/invoices/readiness/history, loading/error/not-found/disabled/retry. Новой кнопки доставки нет. Добавлены structured client/loader/manager/previous-driver phones и явное копирование. Это developer UX P5; отдельной visual/product приёмки C02/C05 не было.

- Display/copy сохраняют целую строку телефона. Conservative dial target только для одной строки допустимых цифр/format separators, длина5–15; labels/multiple `+`/разделители/USSD не угадываются. ACTION_DIAL, без CALL permission/автоматического звонка. Mobile и WhatsApp истории не объединяются.
- **Извлечение телефонов из свободного comment не реализовано:** backend такого parser не содержит, контракт требует отдельного согласования. Полный comment доступен для чтения/копирования; structured DB phones работают независимо. Не объявлять C02 закрытым.
- Валидная ссылка: явный выбор Яндекс Карт/Навигатора с учётом scheme compatibility. Intent только в выбранный package, URI scheme нормализуется. Дополнительный dispatch guard отклоняет unsupported scheme, userinfo, пустой authority/host HTTP, whitespace/control/malformed URI. Он не меняет выбор кандидата resolver.
- Нет достоверной link/ambiguous: «Ручной маршрут», пояснение, копирование исходного текста, открытие выбранного Яндекс-приложения **без destination**. Адрес/координаты из client text не подставляются, geocoding нет. Для выбранного address без link доступно его отдельное копирование.
- ActivityNotFound/SecurityException → безопасное сообщение. Не переключается автоматически на браузер/HTTP backend. Реальные установленные Яндекс-apps/номеронабиратель/clipboard/process death должны проверяться на устройстве позже.

## Границы

Нет reys/reys_den write, route telemetry, DDL, API fallback, изменений legacy проектов или P6. P2/P3/P4 исторические статусы не повышены. Конкретные counts/build/APK/Android evidence и неизвестные перечислены в PROGRESS. DRIVER_ROLLOUT=BLOCKED_SECURITY сохраняется.

## Повтор локальных Android проверок

Для software API30 runner может не успеть прочитать getprop за стандартные2 секунды. Подтверждено чтением ddmlib PropertyFetcher через javap: поддерживается `-Dddmlib.getprop.timeout.sec=30`. Это настройка test runner, не ослабление сетевых таймаутов app/SQL или assertions. Команда после `source /workspace/.ffl/env.sh`:

```bash
ANDROID_SERIAL=emulator-5556 bash gradlew --offline --no-daemon --max-workers=2 \
  -Dddmlib.getprop.timeout.sec=30 :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=ru.ffl24.direct.OrderDetailsUiTest,ru.ffl24.direct.StartupTest#coldStartAndNavigationHaveNoConnectionInput,ru.ffl24.direct.StartupTest#recreationPreservesNavigationAndThemeWithoutSetupFlow
```

Эмулятор запускается существующим `/workspace/.ffl/p1-emulator-start.sh`; нужны boot **и** рабочие activity/package services. Один sys.boot_completed=1 недостаточен: system_server может перезапускаться. Исходные ошибки Android/runner и провал regex flag256 сохранены отдельно, а окончательный результат — в PROGRESS/evidence, не в этой инструкции. Остальные старые instrumentation cases P2 требуют своего TLS fixture; устаревший P2 UI refused-connection case не объявляется проверкой P3 auth UI.
