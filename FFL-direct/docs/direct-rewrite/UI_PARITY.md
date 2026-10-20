# UI comparison — developer continuation

Reference: unchanged FFL branch/HEAD in MASTER_CONTEXT. This is source comparison plus actual Direct Compose checks, not a claim of pixel parity between two live applications. User authorized completing missing screens/behaviour by our judgment after removing phase gates.

| Screen/action | Reference | Direct 0.2.1 | Evidence/limit |
| --- | --- | --- | --- |
| Startup/settings | API address and health setup | Safe missing config; no API/DB address input, one fixed Direct runtime | StartupTest, APK/graph checks |
| Driver choice/login | Search/select/password/retry | Same pattern, transient password, current access/local identity | Existing Compose auth; navigation and actual test MySQL login |
| Main shell | Driver header/settings/back/theme | Driver header, settings/back, directory entry, persistent theme/logout | StartupTest/WorkspaceNavigationTest |
| Orders | Single large-card list, summary, pull-to-refresh | Three permanent tabs, counts, compact rows, refresh/pull, delivered from both sources | WorkspaceUiTest; actual fixture MySQL list screenshot |
| Identity/sorting | Numeric IDs, fixed SQL order | Source-aware IDs, exact backend SQL baseline, local drag/↑/↓ overlay; reset order | SQL/model tests and Compose callbacks; no sort_r write |
| Scroll/date | No explicit driver/date/section restore; fixed date | Visible selectable date, today by default; separate local scroll/order scopes | Preference/merge tests, settings; last250ms can be lost on abrupt kill |
| Order details | Title/address/time/comment/invoices/readiness/history | Same field groups, source-aware routing, resolver, <=5 same-source history, contacts | OrderDetailsUiTest + actual MySQL details screenshot |
| Addresses/maps | Text URL, Maps only | Trusted resolver, explicit Maps/Navigator/manual choice, no guessed ambiguous address | Golden/JDBC/ART tests; real Yandex apps not launched |
| Contacts | No directory or comment-phone parser in reference | Copy/ACTION_DIAL in details; comment numbers labelled as suggestions; original comment retained | Intent/phone/clipboard cases; no real calls |
| Delivery | Ready guard;5sec card dialog, ordinary details dialog | Same guard/confirmation distinction, transactional/idempotent source-aware write | JVM transaction tests, real synthetic MySQL rollback/commit/repeat checks |
| Directory | Absent | Card/holder, important phones, active drivers; raw copy/dial | SQL/mapper/UI cases, actual test DB reads; missing card explicit |
| Notifications | Absent | Opt-in WorkManager >=15min; baseline/dedup driver/date; immutable guarded tap | Policy/store/channel/scheduler/navigation cases; measured Doze/reboot/15min timing and physical device unrun |
| Loading/error/disabled | Loading/empty/retry, inline stale-data error | Loading/empty/safe errors/retry/disabled; protected rows hidden while access is rechecked | Safe-error/access/Compose tests; conservative clearing replaces stale inline display |

Visual differences are deliberate developer choices for the requested missing features. The large reference cards are replaced by compact rows and three tabs; “Детали / Маршрут” opens details with route choice. No compact-row/directory/notification screen was falsely described as an existing reference screen. Font-scale/rotation/process-death coverage is partial; real device, minified runtime and full accessibility audit remain unverified.

Current continuation: restored source schema/data, six DB Android methods and eight UI component regression methods passed on Compose1.10.6. A normal compiled MainActivity was used for UI and actual notification tap; source data stayed private. Screenshot restored-db/controlled-second-route.png contains only explicitly fictional records. This still does not establish live-reference pixel parity or physical-device behaviour.
