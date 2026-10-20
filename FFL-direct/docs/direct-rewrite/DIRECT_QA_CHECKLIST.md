# QA и требования к доказательствам

Каждый этап оценивается по собственным acceptance criteria из [плана](DIRECT_REWRITE_PLAN.md). POSITIVE предыдущего этапа не распространяется на следующий. NEGATIVE/BLOCKED этапа → остановка и отчёт; не отключать assertions/проверки, не заменять live тест mock JDBC.

## Раздельные статусы

| Направление | Что является достаточным доказательством | Что недостаточно |
| --- | --- | --- |
| STATIC_CHECK | Проверенный граф зависимостей, manifest/source/APK scan, lint относящегося target, контракты/links | Простое отсутствие слова Retrofit в одном файле |
| UNIT_TEST | Реально выполненные tests с counts, fresh XML и exit code | UP-TO-DATE, zero tests, пропущенный набор |
| BUILD | Точный target/variant, exit=0, путь APK, SHA-256 и package metadata | Только работающий Gradle или desktop jar |
| EMULATOR_RUNTIME | Установлен конкретный APK, модель/API/ABI, функциональный сценарий и logcat | PID/open port/успешная JVM-компиляция |
| REAL_DEVICE_RUNTIME | Тот же APK SHA, модель/Android/сеть и результат сценария | Эмулятор либо открытый экран без проверки источника данных |
| LIVE_DB_READ | Разрешённая реальная read operation, fixture/driver/date без PII в отчёте, данные сопоставлены | Mock JDBC, TestClient с monkeypatch, health или select1 без бизнес-чтения |
| LIVE_DB_WRITE | Явно разрешённый безопасный заказ, before/after и неизменность прочих полей, transaction/idempotency | Mock commit, UI toast, готовый APK |
| SECURITY_REVIEW | Негативные тесты прав вне клиента, TLS/identity/grants + review evidence | WHERE id_v в APK, TLS/VPN или column UPDATE сами по себе |

Для неприменимого направления использовать `BLOCKED — не выполнялось в scope этапа`, а не ложный PASS. Итог P0 может быть POSITIVE при документированных будущих runtime blockers: P0 требует законченного анализа, не реализованного Direct-клиента. Дефекты legacy фиксируются отдельно, не становятся «исправленными» от документации.

## P0

- [x] Git HEAD/status проверены; source snapshot hashes зафиксированы.
- [x] Report целиком прочитан и сохранён без редактирования; серверная схема не выдана за проверенную.
- [x] Текущие Android source/models/UI/Gradle/tests/docs исследованы; отсутствующие классы перечислены.
- [x] Пользователь подтвердил текущую ветку как UI-reference; недостающее требует отдельного согласования.
- [x] A/B/C сопоставлены; выбран отдельный проект, старый код не меняется.
- [x] Семь обязательных документов содержат последовательность P0–P11, gates, риски и продолжение.
- [x] Финальная автоматическая проверка полноты ссылок, source hashes и отсутствия runtime; результат в PROGRESS и evidence/p0-validation.json.

## Будущие contract/unit cases

| Область | Обязательные случаи |
| --- | --- |
| ID/SQL | reys:1 ≠ reys_den:1, invalid source/format/0/negative/overflow/injection, dynamic SQL только enum, параметры отдельно |
| Auth/access | Только __hr_typepos, active list без parol, wrong/missing/disabled user, session restore, access сменился, config/TLS error отдельно от bad password |
| Mapping | status 0/1/2/unknown, col_s 0/1/2/unknown, null/blank, col=0 и большие значения, st_vv null/0/1/нестандартное, Unicode title |
| Date/order | Согласованная дата, отсутствие silent today; обе таблицы, empty sections, stable ordering, driver/date/section storage isolation, delivery move и refresh |
| Resolver | 0/1/несколько candidates, normalization, score120/100/tokens cap3, tie, top0, delta39/40, confidence none, invalid link при сохранившихся контактах, кандидаты кешируются, решения отдельно по каждой строке |
| Details | Чужой заказ, same-source previous history<=5/date DESC/id DESC, точный name_c, time regex examples, raw plannedTime сохранён, адрес только resolver, comment phone parsing по отдельному решению |
| Delivery | disabled/no owner/no row/already delivered/concurrent double tap, rollback на query/timeout/verify failure, affectedRows, unknown commit, только два SET columns |
| Directory | null card, blank important phone, mobile→whatsapp, inactive/no-phone skipped, no silent dedupe/splitting |
| Notifications | active counters/IDs, no duplicates, first observation/date/driver reset, stop on disabled/logout, tap navigation, permissions/Doze/reboot согласно согласованному дизайну |

Fixtures переносить из сохранённого report и backend tests; golden comparison не требует HTTP в новом runtime. Expected labels/SQL/ordering должны проверяться независимыми cases, не копией реализации.

## Android runtime и безопасность — будущие проверки

- Точный APK, отдельный package, старый reference остаётся установленным; cold start без backend URL и без DB config fields в UI.
- API 24 и target 36 representative runtimes, rotation/process death, тёмная тема, font scale, long Cyrillic, back и scroll.
- D8/R8/classloading фактического JDBC artifact на ART, CA/hostname errors, TLS/auth plugin negotiation, connect/read/query timeouts, no Main-thread network, закрытие ресурсов при cancel.
- Без реальных test driver/password не отмечать E2E login как успешный.
- Без безопасного test order и явного разрешения не выполнять live write, в том числе «просто повторить Сдал».
- Проверка write на стенде: до/после только разрешённые delivery fields, другая таблица/driver не изменены, двойное подтверждение idempotent, недоставляемая row/потеря сети/commit ambiguity явно разобраны.
- Соблюдать Android background ограничения; WorkManager periodic обычно не даёт произвольного малого интервала, Doze откладывает работу. Не заявлять exact polling или push без доказательства; foreground service требует отдельного UX/permission решения.
- Проверить notification permission/channel, receivers exported flags, PendingIntent identity/immutability, поведение при отключённом доступе и чужом session.
- Не публиковать credentials/PII/сырые DB dumps в logs/артефактах. Secret scan дополнить APK inspection, не выдавать его за серверную авторизацию.

## Шаблон отчёта каждого этапа

```text
Этап: Pn, дата/часовой пояс
Результат: POSITIVE | NEGATIVE | BLOCKED
Изменённые файлы: точные пути
Реализовано:
Не реализовано:
Команды: cwd, команда без секретов, exit code, путь лога
Тесты: target, executed/passed/failed/skipped/disabled/unrun, fresh report
Сборка: variant, exit, APK path/SHA либо не выполнялась
БД: разрешённая read/write операция или не использовалась
Android: установленный APK SHA, emulator/device/API либо не устанавливался
STATIC_CHECK:
UNIT_TEST:
BUILD:
EMULATOR_RUNTIME:
REAL_DEVICE_RUNTIME:
LIVE_DB_READ:
LIVE_DB_WRITE:
SECURITY_REVIEW:
Непроверенное и ограничения:
Блокеры / разрешённый следующий шаг:
DIRECT_REWRITE_Pn_RESULT: POSITIVE | NEGATIVE | BLOCKED
```

Не считать nonzero-exit замаскированным tee/logging успехом. Сохранять исходный exit code; перед подсчётом tests проверять свежесть/target XML. Новые инструкции/план не являются подтверждением исполненных команд.
