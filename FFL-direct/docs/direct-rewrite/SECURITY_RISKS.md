# Модель угроз и граница developer-only

## Актуализация19.10.2026: административный runtime-профиль0.3.1

Новый запрос пользователя разрешает отдельный редактор Direct MySQL. Публичный APK не содержит DB-реквизитов. Профиль сохраняется целиком AES-256/GCM с Android Keystore в noBackupFilesDir; saved profile приоритетнее BuildConfig, ошибка расшифровки закрывает доступ. TLS verify-full/CA/hostname остаются обязательными, root/admin отклоняются. Изменение сохранённого профиля требует системного PIN/пароля устройства; первая настройка доступна на ненастроенной установке.

Системное подтверждение проверяет владельца устройства, а не серверную роль администратора. Очистка app data сбрасывает bootstrap-защиту. Keystore не исключает извлечение расшифрованного пароля из скомпрометированного процесса. Аппаратный Keystore и успешный системный PIN на телефоне не проверены. Общий DB account по-прежнему позволяет читать разрешённые таблицы вне одного водителя; **DRIVER_ROLLOUT=BLOCKED_SECURITY** сохраняется. Production не используется.

Смена профиля закрывает старые соединения, инвалидирует сессию, отменяет worker и изолирует локальные данные endpoint/account/CA namespace. Бизнес-SQL и серверные привилегии не расширялись. Пароль/CA не попадают в публичные скриншоты; экран защищён FLAG_SECURE. Plaintext JSON с реквизитами существует только как явно инжектируемый instrumentation-input в выделенном тестовом процессе и удаляется runner-ом после тестов; production его не создаёт и не использует.

Подробности и доказательства: [DB_CONNECTION_SETTINGS](../DB_CONNECTION_SETTINGS.md). Прежние ограничения BuildConfig-only ниже относятся к историческим версиям.

---

Это предварительная оценка P0, не завершённый SECURITY_REVIEW P11.

**DRIVER_ROLLOUT: BLOCKED_SECURITY.** Общий DB account в APK не обеспечивает серверную изоляцию водителей. Ни клиентский WHERE id_v=?, ни повторный access=1, ни TLS/VPN не заменяют авторизацию на стороне сервера/БД. Нельзя называть такой pilot production-ready.

## Активы, участники и границы доверия

Активы: DB credentials, legacy parol, персональные телефоны/адреса/история, карта из nastroiki, целостность delivery timestamp/flag, доступность legacy MySQL. Клиент и local session не являются доверенной серверной границей. Рассматривать извлечённый APK, модифицированный клиент, украденный телефон, SQL-инструмент с извлечённым DB логином, сетевого атакующего и ошибочную конфигурацию.

Границы: UI → data layer → JDBC → сеть/VPN/TLS → DB account/grants → таблицы/функции. UI access/ownership guard защищает от ошибок штатного клиента, но не от SQL напрямую с теми же grants.

## Реестр угроз

| ID | Угроза / ущерб | Контроль developer-only | Что осталось доказать |
| --- | --- | --- | --- |
| S01 | Извлечение общего DB password из BuildConfig/APK | Только ограниченный dev account, отсутствие секретов в Git/логах, rotation/revocation | Серверная identity/isolation; local.properties не решает извлечение |
| S02 | Чтение чужих заказов/контактов через общий SELECT | Минимальные SELECT columns и scope в клиенте | Негативные проверки другим SQL-клиентом: DB должна отказать без WHERE защиты приложения |
| S03 | Чтение parol всех водителей | Login SQL сравнивает параметр, не отдаёт parol в app | При broad SELECT атакующий всё ещё читает пароли; DBA policy/процедуры/отдельные identities |
| S04 | Изменение чужой доставки через разрешённые UPDATE columns | Только два столбца, транзакция/ownership/access в штатном коде | Column grants не дают row-level security; сервер обязан ограничить строки |
| S05 | TLS downgrade/MITM, недоверенный endpoint | Требовать CA+hostname verification, отказ на ошибке | Реальный сертификат/цепочка, hostname mismatch tests на Android, драйвер/auth plugin |
| S06 | Старые данные/сессия другого водителя, подмена local driverId | Отдельное storage, driver/date keys, purge на logout/disabled, access перед каждой операцией | Local session не является неподлежащею подмене identity; серверное связывание с аккаунтом |
| S07 | Логи, crash report, backup содержат password/PII | Redaction без connection string/SQL params, запрет сохранения пароля, новые backup exclusions | APK/logcat/backup inspection. Старый navigator логирует адрес: не переносить |
| S08 | Потеря связи/повтор commit даёт неправильный статус | Сериализация write per ID, транзакция, postconditions, unknown-result + read-before-retry | Настоящие network-fault/concurrent client tests и engine/grants |
| S09 | Смена access между check и UPDATE | Проверка на той же connection перед write, блок действий при disabled | DBA lock/isolation/revocation design; клиентский check не достаточен |
| S10 | SQL injection через source/order/date/номер | Allowlist table enum + PreparedStatement для значений | Тесты injection/overflow/Unicode; запрет произвольного SQL из UI |
| S11 | Неконтролируемый polling/connection storm/locks | Ограничить concurrency/retries, таймауты, закрытие ресурсов, чтение summary вместо всех заказов | Android background limits, DB load на согласованной частоте, no Main-thread JDBC |
| S12 | Неправильный адрес/звонок из неоднозначного legacy match | Точная scoring parity, ambiguous→manual, URI allowlist, ACTION_DIAL вместо необратимого auto-call как кандидат | Конкретный UX звонков согласовать; runtime обработка unsafe URI/отсутствующих apps |

## Запреты

Не использовать root/admin/full-access account для распространяемого APK. Не открывать MySQL всему интернету. Не выполнять production DB UPDATE, CREATE/ALTER/DROP/DELETE/INSERT или grants автоматически. Не добавлять таблицы для route-opened. Не выключать cert/hostname verification, не менять trust/checksum ради passing build. Не помещать секреты в отчёт, аргументы shell, сохранённый setup/start script или пример заполненного properties.

DIRECT_MYSQL_USE_SSL должен иметь безопасную семантику: запрос отключить TLS не разрешает production/driver rollout, silent fallback на plaintext запрещён. Политика developer exceptions, если понадобится для изолированного стенда, требует отдельного явного решения и не считается проверенным TLS.

## Минимальные grants — снижение ущерба, не решение S02–S04

Только чтение необходимых columns из __hr_typepos/reys/reys_den/meneger_contr_ffl/meneger_ffl/phone/nastroiki и нужное право вызова name_v. Запись только `reys(st_vv,date_att_log)` и `reys_den(st_vv,date_att_log)`. На все auth/settings/contact tables запись запрещена. Конкретные GRANT не выполнять в P0 и не считать достаточными для разграничения водителей.

## Варианты серверно-принудительного разграничения для P11

| Вариант | Что проверить | Совместимость с текущим runtime scope |
| --- | --- | --- |
| Отдельные DB identities на водителя и контролируемые views/procedures | Реальное связывание DB identity с driverId, запрет base tables, чужие строки недоступны, lifecycle/отзыв | Возможный Direct-путь; нужна DBA-реализация и runtime-доказательства |
| Stored procedures | Идентичность не должна задаваться произвольным p_driver_id; только EXECUTE, без обхода base-table grants, DEFINER/INVOKER, проверки auth/access | Возможный Direct-путь, но не считать безопасным автоматически |
| Минимальный API на имеющейся инфраструктуре | Серверная identity/ownership, secret isolation, совместимость интерфейса | Альтернатива rollout; **меняет требование Direct-only**, нужна отдельная санкция пользователя; скрытого API fallback не делать |
| VPN + TLS с общим account | Уменьшает сетевую поверхность/перехват | Не решает row isolation и parol exposure |

Если безопасный Direct не доказуем при доступных полномочиях — продолжать только developer-only реализацию по явно разрешённым этапам, DRIVER_ROLLOUT остаётся BLOCKED_SECURITY. Не скрывать конфликт безопасности сменой режима или маркетинговой формулировкой.

## Критерии будущего SECURITY_REVIEW

Доказательства: actual SHOW GRANTS/schema согласованного стенда без публикации секретов; попытки чужого SELECT/UPDATE вне штатного APK должны получать отказ БД; недействующий водитель не может обойти запрет прямым SQL; пароль и PII не читаются за пределами разрешённого; отозванный account действительно перестаёт работать; network/CA/hostname failures fail closed; inspected APK/logcat/storage/backup; безопасный rollback не раскрывает dev secrets. Команды, exit codes, APK SHA, устройство/Android version и разрешённые данные фиксировать отдельно.

P0 не выполнял эти проверки. Подтверждён только архитектурный риск общей учётной записи. Нет credentials/grants/CA/device/live schema; SECURITY_REVIEW = BLOCKED, а не POSITIVE.

## Наблюдения P2 — 16.10.2026

P2 BLOCKED, DRIVER_ROLLOUT=BLOCKED_SECURITY. Developer connection layer использует explicit CA + JSSE HTTPS endpoint identification, без system/deferred trust и plaintext fallback. На API30 wire fixture подтвердил TLS handshake при доверенной CA и отказ при чужой CA/неверном hostname; это не real MySQL/auth/grants/device review.

Lint обнаружил два TrustAllX509TrustManager warnings внутри официального mariadb-java-client 3.5.10.jar (пустые checkClientTrusted/checkServerTrusted в vendor trust-all варианте). Они оставлены в отчёте без suppress/baseline. В app factory принудительны verify-full, explicit CA и ffl-android plugin; USE_SSL=false/URL options/redirect не принимаются. AndroidTlsSocketPlugin строит свой TrustManagerFactory из явной CA, не вызывает vendor trust-all manager. Негативные Android TLS tests проверяют этот реальный путь; наличие permissive vendor classes не считается их использованием или security certification. Расширение config/plugin путей требует повторной проверки.

R8 missing optional desktop types явно перечислены в app/proguard-rules.pro: JNA/Windows/GSS/LDAP/JMX/XA/IAM/PARSEC/SLF4J и SQLType overloads не поддерживаются. Это не разрешение использовать их, minified-runtime ещё не проверен. DNS/native cancellation, service connection для KILL QUERY и partial-connect resource closure требуют реального стенда. BuildConfig credentials извлекаются из APK/generated files/caches; проверенный текущий APK собран без credentials. Production rollout и live writes не разрешались; P3 не начат.

## P3 после указания «Пропускай все» — без проверки

Пользователь разрешил продолжить текущую реализацию без ожидания P2 POSITIVE и без проверок. Добавлены source-only auth/session/access; build/test/lint/runtime/security review не запускались, старые P2 PASS не распространяются на новый код. Предыдущая фраза «P3 не начат» выше историческая.

По исходникам: login сравнивает parol параметром SQL, не SELECT-ит его; DataStore содержит только version/id/name, без password/JWT/DB config. Input не rememberSaveable, CharArray очищается после операции/отмены; временные immutable String для Compose/JDBC не могут быть гарантированно стёрты из JVM/IME. Raw DB exceptions не выводятся. Это описание намерений кода, не доказательство отсутствия утечек.

При restore текущий memory grant сбрасывается до проверки access; network/storage ошибки не дают offline доступ. Disabled/missing/logout очищают local session; при disk IO failure успешная очистка не гарантируется и UI не показывает успешный logout. Revision/Mutex/cancellation paths требуют race/process-death тестов. Password/access queries остаются последовательными reads: изменение access после check и подмена local id/shared-account SQL клиентом не предотвращаются сервером этим кодом. Будущие protected operations обязаны повторять gate; P4 не реализован. DRIVER_ROLLOUT=BLOCKED_SECURITY.

---


## P5 developer implementation

По явному waiver пользователя P5 реализован без ожидания внешних gates. Read-only parameterized details/history/contacts и route ownership validation используют существующий IO/TLS executor; runtime DB account/grants/isolation по-прежнему неизвестны. Repository проверяет session revision, datasource — current access и owner до публикации; внешний SQL-клиент с общим account этими мерами не ограничен. Нет новых DB/HTTP writes, delivery/P6 или credential UI.

В неоднозначном resolver результате адрес/контакты не публикуются. Intent dispatch дополнительно проверяет URI, scheme/package Яндекс-приложения; manual открывает launcher без destination, без парсинга адреса/geocoding. Телефоны целиком display/copy; только conservative single dial target через ACTION_DIAL, без USSD/автозвонка. Clipboard содержит PII после явного copy действия пользователя; системное управление/очистка/backup/logcat на настоящем устройстве не проверены. Адресные модели имеют redacted toString, ошибок с SQL/host/params в UI нет по исходникам. Это не завершённый security review.

Локальные golden/JDBC proxy/session tests не доказывают настоящую MySQL schema/collation/name_v, атомарную authz или malicious-client isolation. P2/P3/P4 статусы и DRIVER_ROLLOUT=BLOCKED_SECURITY сохранены. Реальные установленные Карты/Навигатор/номеронабиратель, process death и физическое устройство требуют отдельного прогона. Точный APK/локальный Android subset — PROGRESS.md.

## Продолжение после отмены gates — 17.10.2026

Пользователь отменил prerequisite POSITIVE/STOP и разрешил завершить developer-приложение. Исторические ограничения «P4/P6 не реализованы» выше больше не описывают текущие исходники. Это не разрешение менять production данные и не доказательство безопасного массового Direct rollout.

Добавлен production-path delivery: PreparedStatement, enum source, id/id_v, access до и после, FOR UPDATE, affected rows и postconditions, только SET st_vv/date_att_log, общий write Mutex, rollback, без автоматического write retry. Неопределённый commit отображается как неизвестный результат. Клиентская отмена/session revision отбрасывает поздний результат; она не гарантирует отмену уже доставленного SQL. Статус access может измениться после последнего check, а общий DB account по-прежнему позволяет обход штатного клиента.

На собственном одноразовом MySQL8.4.11/InnoDB стенде с вымышленными данными подтверждены Android TLS/JDBC/SELECT1, реальное чтение, owner/access guards, транзакция/повтор доставки и rollback после настоящего UPDATE. Account стенда имеет SELECT/EXECUTE и UPDATE только двух delivery columns; это демонстрация совместимости, не row-level isolation. Root использовался только для создания/удаления этого изолированного стенда, никогда не был включён в APK. Сертификат CA и test-only config передаются только instrumentation runtime; текущий main APK без DB credentials, фикстур и fake runtime. Установленный APK hash сверяется с артефактом. Точные результаты — PROGRESS/evidence/completion.

Уведомления opt-in, общий текст без имени/адреса клиента, private visibility, immutable PendingIntent с driver identity, проверка текущей сессии при открытии. В preferences хранятся только ID/порядок/scroll/date/theme/seen; это данные, не серверная authority. В момент opt-out/logout работа отменяется; eventual Android cancellation не заменяет guards самого worker. Длительный Doze/reboot/переход даты/force-stop и прямые SQL-атаки вне клиента на реальной БД остаются непроверенными. DRIVER_ROLLOUT=BLOCKED_SECURITY.

## Локальный восстановленный стенд / DB integration

Новый пользовательский запрос разрешает импорт и свободные эксперименты только в собственной тестовой копии. Получить предоставленный дамп не удалось: лимит32МиБ. Новый helper использует официальный MariaDB10.6.25 image с digest, loopback publish, explicit CA/hostname, require-secure-transport, отдельный column-limited account, ownership labels и named volume. Ключ CA не копируется в DB container; root остаётся только в локальном контейнере. Event scheduler/local-infile выключены, FILE доступ ограничен контейнерным /tmp; это не обещание безопасного исполнения произвольного SQL из недоверенного источника. Перед полным импортом следует изучить private файл/DEFINER/schema, когда он будет доступен.

State вне source checkout, Linux directory700/files600, Windows user LocalAppData и наследуемые ACL. Full SQL/logs/PII не выводятся в публичные evidence. Случайные локальные пароли передаются дочернему Gradle через environment, buildDir/cache/private APK вынесены отдельно. Они всё ещё извлекаются из настроенного APK. Windows ACL/Android backup на физическом устройстве не проверены.

Synthetic automated writes допускаются только при profile marker и том же compiled configuration. Real restored profile не допускается в эти write tests без подготовки отдельных обезличенных сценариев. Общий DB account и column grants не предоставляют server row isolation; production/driver rollout по-прежнему BLOCKED_SECURITY.

## Проверки восстановленного дампа — 17.10.2026

Все части получены и хеши совпали; прежний transfer blocker снят. Personal source dump, реальные data rows, private tests/XML/logs, CA keys и настроенный APK хранятся вне checkout. Публично только counts/hash/schema и контролируемый экран fictional водителя. Нельзя включать private state/дамп в source ZIP или public environment.

Успешный импорт потребовал локальной смены DEFINER у9objects (4routines+5triggers); bodies/SQLmode/charset/attributes сохранены, production account не восстанавливался. UPDATE вызывает исходные server triggers, включая BF synchronization. Реальный injected failure подтвердил rollback связанной InnoDB BF строки; repeat не создаёт дубль. Клиент по-прежнему SET-ит только st_vv/date_att_log, а серверные side effects отдельно учитываются.

На настоящем restricted account проверены отказы UPDATE status/auth, INSERT и DELETE. Также отдельным SQL-клиентом подтверждён прежний риск: общий account может изменить два разрешённых поля чужого fictional заказа в обход клиентского id_v/access guard. Probe откатан, чужие production строки не использовались. Таким образом, серверной row isolation нет; функциональный PASS не является SECURITY_REVIEW/P11 POSITIVE. DRIVER_ROLLOUT=BLOCKED_SECURITY.

## Итоговый аудит 0.2.2 — 17.10.2026

Независимо подтверждены чтение parol/чужих строк и возможность direct SQL UPDATE двух delivery columns чужого fictional заказа; probe откатан, production не использовался. Роль сужена до SELECT7контрактных таблиц, EXECUTE name_v и UPDATE(st_vv,date_att_log) обеих source tables, REQUIRE SSL. Отдельный101-table integrity reader не входит в Main BuildConfig/APK и не имеет UPDATE. Это сокращает права, но не устраняет общий account/row-authorization риск.

Штатный login усилен BINARY password equality, delivery держит shared access-row lock до commit, session/logout races закрыты. R8 startup constructor rule исправляет работоспособность, не скрывает извлекаемые credentials. Публичные APK/source ZIP без DB config; private configured debug/release не публикуются. Полная оценка/неизвестные/практический gateway без отдельного VPS — [REMAINING_RISKS](../REMAINING_RISKS.md). Готовность только DEVELOPER_TEST_READY.
