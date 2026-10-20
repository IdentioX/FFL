# Проверенный изолированный стенд

Это отдельный тестовый MySQL8.4.11/InnoDB, созданный только для этого продолжения. Он не является рабочей legacy БД пользователя и не доказывает совместимость её версии/schema/auth plugin/grants.

- Официальный Docker mysql:8.4; digest, server version и логи в evidence/completion.
- Loopback127.0.0.1:19065, Android adb reverse; require_secure_transport=ON.
- Доверенная test CA, hostname localhost, explicit verify-full. Корректная CA не отключает hostname check.
- Публичный синтетический DDL/data: tools/fixtures/completion_mysql.sql. Это НЕ migration, не запускать на существующей БД.
- Отдельная fixture identity имеет только SELECT/EXECUTE и UPDATE(st_vv,date_att_log). Пароли DB сгенерированы случайно, файлы600 в task-private каталоге вне проекта. Реквизиты не сохранены в отчёте/аргументах/исходниках/основном APK.
- Вымышленные driver97/98, дата2026-05-18, reys/reys_den с совпадающим id1 плюс delivered/foreign строки. Никаких реальных заказов, имён или телефонов.
- Config вводится stdin в private files/completion-db.json test target на эмуляторе. Два integration tests требуют marker отдельного стенда; без config получают JUnit assumption skip, не PASS. Ни один тест не выбирает production endpoint из BuildConfig для записи.

## Что реально проверяется

DirectDatabaseUiTest: настоящий DirectApp → выбор driver → пароль → production AuthRepository → JDBC/TLS/MySQL → список → production details → «Сдал» → подтверждение → чтение st_vv/date_att_log с сервера. Затем только собственная fixture строка сбрасывается для следующего теста.

DirectDatabaseFlowTest: DIRECT_MYSQL, SELECT1, active drivers, неверный пароль, access0, три раздела/обе таблицы, contacts/history resolver, foreign details/delivery denied, directory, notification counters/source IDs, реальный UPDATE с injected client SQLException и контроль rollback; обычные delivery/повтор timestamp для обеих таблиц; неизменность status/col и logout gate.

XML listener фиксирует failures/skips. Process exit0 от am instrument сам по себе не PASS. Финальная приёмка опирается на свежий XML и OK(28 tests), а также сравнение таблиц до/после. Снимки экранов содержат только синтетические данные. Основной установленный APK и выдаваемый APK имеют один SHA; конфигурация заменяется исключительно в androidTest через AppGraph.create, fake в main APK отсутствует.

TLS-wire fixture в tools/p2_tls_fixture.py отдельно проверяет trusted/wrong-host/untrusted/timeout на Android. Он закрывается до аутентификации и не считается MySQL. Compose fakes используются только для UI edge cases и не считаются SQL-path evidence.

## Границы

Android11/API30 x86_64 emulator с software emulation проверен. Physical device, API24/36, minified runtime, реальные Яндекс/звонки, измеренный15мин/Doze/reboot и рабочая БД — отдельные непройденные направления. После сбора evidence удаляются task-owned container/volume/config и выключаются только процессы этого стенда. Исходные FFL/FFL-backend и старый AVD сохранены.
