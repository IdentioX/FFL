# Readiness Logic

This project treats order readiness and invoice quantity readiness as separate backend-owned values.

## Backend Source Fields

In MySQL table `reys`:

- `status` is the main order readiness field:
  - `1` = not ready
  - `2` = ready
- `col` is the total invoice count.
- `col_s` is quantitative invoice readiness:
  - `0` = no invoices ready
  - `1` = partially ready
  - `2` = all invoices ready

## API Rule

The API must map readiness fields this way:

- `isReady` comes from `reys.status`.
- `readinessLabel` comes from `reys.status`.
- `invoicesCount` comes from `reys.col`.
- `quantityReadiness` comes from `reys.col_s`.
- `quantityLabel` comes from `reys.col_s`.

The API must not use `col_s` to decide `isReady`.

## Android Rule

Android must trust backend readiness fields:

- Use `isReady` for the main ready/not-ready visual state.
- Display `readinessLabel` as the main readiness label.
- Display `quantityLabel` as the invoice readiness hint.
- Do not calculate readiness from `quantityReadiness`, `col_s`, invoice counts, or any local rule.

## Example JSON

```json
{
  "orderId": 12345,
  "orderNumber": "A-2026-00123",
  "driverId": 97,
  "driverName": "Иван Петров",
  "clientInfo": "ООО Пример, Москва",
  "deliveryDate": "2026-05-26",
  "invoicesCount": 4,
  "isReady": false,
  "readinessLabel": "Не готов",
  "quantityReadiness": "partial",
  "quantityLabel": "Часть накладных готова",
  "isDelivered": false,
  "deliveredAt": null,
  "canMarkDelivered": true
}
```
