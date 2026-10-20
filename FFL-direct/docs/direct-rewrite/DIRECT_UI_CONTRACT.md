# UI-контракт Direct — эталон текущей ветки

Текущая developer-реализация после снятия gates: три постоянные вкладки рейсов/сданных, компактные строки, drag после долгого нажатия и ↑/↓, локальные order/scroll driver/date/section, детали и отдельный справочник. Рабочая дата явно видна, по умолчанию сегодня на телефоне, есть ручной выбор и возврат «Сегодня». Доставка сохраняет ready guard, 5 секунд в списке и обычное подтверждение в деталях. Уведомления opt-in, WorkManager >=15 минут, без exact interval/foreground service/push; первое чтение baseline, dedup driver/date, tap во второй рейс после доступа. Это рабочие решения по новому поручению пользователя действовать по усмотрению, не заявление полной visual/live приёмки. Телефоны из комментария — только помеченные подсказки, исходный текст сохранён; справочник без split/dedupe.


Ниже сохранены исторические P5/P3/P0 состояния; текущие функции и screen comparison описаны выше и в UI_PARITY.md.

P5 developer актуализация: добавлены отдельный details destination и UI title/client/address/time/comment/invoices/readiness/history, structured phones/copy/ACTION_DIAL, выбор Яндекс Карт/Навигатора, manual route без угаданного destination. При ambiguous address/contacts null. Comment phone extraction и visual/product C02/C05 acceptance остаются открытыми; полный comment сохраняется. P4 list отсутствует, поэтому home не получил фиктивного перехода к заказам. Полные локальные проверки/ограничения — PROGRESS.md/P5_IMPLEMENTATION.md.

P3 source-only актуализация: после «Пропускай все» добавлен developer auth flow search/select/masked password/error/logout и минимальный disabled экран с обновлением списка/возвратом к выбору. Проверки/сборка/visual acceptance пропущены, C02 не объявлен отдельно принятым. Это реализация текущего P3, не новые заказы/P4 или полный P9 parity. Остальная таблица ниже сохраняет reference P0; детали нового кода — P3_IMPLEMENTATION.md/PROGRESS.md.

Решение пользователя 16.10.2026: **«Эталон — текущая ветка; недостающее согласуем отдельно»**. Reference — `/workspace/FFL`, commit из MASTER_CONTEXT. Аудит выполнен по исходникам, не по запуску на устройстве; screenshot/visual parity пока не доказана. Новых экранов в P0 нет.

## Что реально существует

| Область | Текущее поведение | Контракт переноса |
| --- | --- | --- |
| MainActivity | Loading → unauthenticated / authenticated, Scaffold, заголовок водителя, настройки, details/back | Новая state machine/DI без Retrofit/me, сохранить визуальную оболочку |
| Выбор водителя | Автозагрузка, поиск имени case-insensitive contains, выбор строки, Вход, masked password dialog, retry/loading | Сохранить UI-паттерн; убрать поле «Адрес API» и «Сохранить адрес и обновить» |
| Пароль | На select/error/success/cancel очищается ViewModel state; сам UI dialog uses rememberSaveable | В Direct пароль только transient state, не сохранять при process death/backup; не выводить в логи |
| Список | Один LazyColumn, большая DriverOrderCard, сводка, pull-to-refresh, stable numeric keys | Сохранить базовый стиль/feedback; будущие keys — source-aware; компактность и разделы пока не реализованы |
| Карточка | Readiness badge, номер, invoicesCount, title/address preview, Маршрут, Сдать | Сохранить происхождение стилей и смысл данных; отвязать DTO/навигатор через callbacks |
| Ошибки/empty | Отдельный loading, «Заказов нет», full error/retry, inline refresh error при старых данных | Сохранить feedback; не выдавать stale чужого driver/date за свежие данные |
| Readiness | В карточке label вычисляется из boolean, в деталях отображается backend label | Unknown status не терять; требуемое отображение неизвестного состояния зафиксировать отдельно, без скрытого перехода в «Не готово» |
| Подтверждение доставки | Карточка: 5-секундный countdown, disabled подтверждение до 0; детали: обычный AlertDialog | Сохранить различие, пока пользователь не согласует единый сценарий |
| Доступность доставки | UI и обе VM требуют ready, !delivered, canMarkDelivered, !inFlight | Report canMarkDelivered означает только !delivered; C06 не решён автоматически |
| Детали | Название, адрес, время, комментарий, накладные, readiness/quantity label, до 5 previousDrivers, bottom actions | Перевести на domain model и string ID, сохранить секции/empty тексты; доступ только своему заказу |
| История | Отображаются имя и дата, take(5); телефонов/номера предыдущего заказа в DTO/UI нет | Новые контактные действия из report требуют отдельного согласования отображения |
| Маршрут | routeText → `https://yandex.ru/maps/?rtext=~...&rtt=auto`, принудительно ru.yandex.yandexmaps | Resolver/routeUrl нужны в Direct; поддержка Навигатора/4 схем по master, UX ручного режима согласовать |
| Settings | Адрес сервера, тёмная тема, save, HTTP health check | Сохранить только тёмную тему/чистые UI-настройки; no API/DB credentials screen |
| Theme/resources | Material 3, индиго/зелёный/розовый, светлая/тёмная палитра, типографика | Переиспользовать Color/Type, theme integration исправлять в новом проекте; иконки без изменения старого пакета |

## Что в checkout отсутствует

Не обнаружены ни классы, ни аналоги: DirectMySqlDriverDataSource, NotificationRepository, PhoneDirectoryRepository, ManualOrderStore, ContactParsing, DriverOrdersDateProvider. В manifest нет receivers/services для polling. Нет ACTION_DIAL, clipboard actions, drag gestures и явного rememberLazyListState. LazyColumn может хранить состояние в пределах composition автоматически, но гарантий восстановления после смены route/process/date/driver нет. Нет отдельного DRIVER_DISABLED/«Недоступно» состояния: HTTP 403 превращается в общую ошибку.

Отсутствующие требования **не отменены** и не считаются «сохранёнными»: они входят в P3/P4/P5/P7/P8/P9, но до реализации и POSITIVE соответствующего этапа нужна согласованная спецификация. Не искать другую ветку без новой причины — пользователь выбрал текущую.

## Правила, уже заданные мастер-инструкцией

- Только DIRECT_MYSQL. Водитель не вводит host/user/password ни API, ни DB, не выбирает режим. Без автоперехода на backend при ошибке.
- Три постоянных раздела: «Первый рейс», «Второй рейс», «Сданные заказы». Delivered обоих source остаются видимыми в третьем; main readiness и количественная готовность раздельны.
- Локальный drag-order изолировать по driver/date/section, не писать sort_r. Все ID с source. Не терять scroll state при деталях/возврате и refresh в согласованных пределах.
- Комментарий/время/контакты отображать без изменения исходных данных. Phone extraction Android — отдельный чистый parser после согласования, без записи в MySQL.
- Маршруты HTTP/HTTPS/yandexmaps/yandexnavi; при ambiguous/недостоверной link — «Ручной маршрут», не выбирать первый адрес и не делать скрытый geocoding.
- Действия копирования адреса и звонков клиенту/грузчику/менеджеру, телефоны/карта справочника: требуются, но placement/формат/обработка множественных телефонов ещё не заданы текущим reference.
- Disabled access блокирует защищённые действия, фоновые запросы и доставку; сеть/TLS/configuration error не превращать в prompt backend IP.
- Уведомления — polling, не push. Tap ведёт в согласованный раздел/заказ с access gate, без утечки состояния предыдущего водителя.

## Вопросы перед затрагиваемыми этапами

| Решение | Что нужно согласовать | Не позднее |
| --- | --- | --- |
| Дата | fixed test date или рабочая дата/выбор даты, business timezone | P4 |
| Три раздела и компактные строки | Вид строки, call placement, пустые секции, группировка delivered, сводка | P4/P9 |
| Manual order/scroll | Merge новых/удалённых заказов, reset, перенос в delivered, scope восстановления scroll | P4/P9 |
| Детали/контакты | Формат телефонов, extraction из comment, действия/история, raw/display text | P5 |
| Ручной маршрут | Выбор приложения, копирование, отсутствие link/адреса/установленных карт | P5 |
| Доставка | Ready guard и countdown карточка/детали | P6 |
| Справочник | Экран/entrypoint, копирование карты/телефона, dial и fallback | P7 |
| Уведомления | Poll interval, первое наблюдение/дедупликация/сброс по driver/date, tap destination, Android permission/Doze поведение | P8 |
| Disabled | Вид/действия «Недоступно», повторная проверка и logout | P3 |

Ни одна строка этой таблицы не является разрешением изменить поведение молча. P1 может сделать изолированный каркас, не принимая эти продуктовые решения.

## Приёмка UI в будущих этапах

Сравнение с reference: те же данные, theme, font scale, orientation, density, locale; screenshot или запись ручного прогона с SHA-256 APK. Проверить accessibility labels, длинную кириллицу, null/blank, неизвестные статусы, большие числа, два источника с одинаковым numeric ID. Отдельно loading/empty/error/disabled/configuration missing и сохранение scroll.

Ни одна screenshot/runtime-проверка в P0 не выполнена. Старые docs не являются фактическим UI: например YANDEX_ROUTE_MANUAL_TEST описывает приоритет координат, которого в действующем navigator нет. При конфликте доказательством служит наблюдённый source + явно согласованный контракт, конфликт фиксируется, а не скрывается.
