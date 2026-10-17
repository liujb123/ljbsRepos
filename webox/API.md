# WeBox · API Reference

Base URL: `http://localhost:8080`
Interactive documentation (Swagger UI): `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

All endpoints are prefixed with `/api`. Every user-facing message returned by this API is in
**English** (PRD section 1). Money is transmitted as a decimal **string** so that it can be parsed
into integer cents without ever passing through a floating point type.

---

## Conventions

### Authentication

Employee endpoints require a bearer token obtained from `POST /api/auth/register` or
`POST /api/auth/login`:

```
Authorization: Bearer <jwt>
```

The token carries the caller's role. Endpoints under `/api/admin/**` additionally require the
`ADMIN` role; an employee token receives `403 FORBIDDEN`, even if the SPA guard is bypassed.

### Response envelope

Every response - success or failure - uses the same shape:

```json
{
  "code": "OK",
  "message": "Success",
  "data": { },
  "requestId": "8f3c1a2e"
}
```

`requestId` is also returned as the `X-Request-Id` response header and appears in the server log,
so a reported failure can be traced to its log lines.

### Pagination

Paged endpoints return this inside `data`:

```json
{
  "items": [],
  "page": 0,
  "size": 12,
  "total": 50,
  "totalPages": 5
}
```

`size` is capped at 50. `page` is zero-based.

### Money

Amounts are strings with exactly two decimals:

```json
{ "price": "22.50", "totalAmount": "121.50" }
```

### Dates and timestamps

Business dates are `yyyy-MM-dd` (`mealDate`, `menuDate`). Audit timestamps are ISO-8601 UTC.

---

## Error codes

| Code | HTTP | When it happens |
| --- | --- | --- |
| `VALIDATION_FAILED` | 400 | A field is missing, too long, or malformed. `data.fieldErrors` lists the offending fields. |
| `IDEMPOTENCY_KEY_REQUIRED` | 400 | `POST /api/orders` without the `Idempotency-Key` header. |
| `ORDER_SIZE_EXCEEDED` | 400 | More than 5 portions in one order. |
| `OPTION_INVALID` | 400 | A required option group is unanswered, a single-choice group has several selections, an option does not belong to the dish, or it was discontinued. |
| `DISH_NOT_ON_MENU` | 400 | The dish is not on the menu for the resolved meal date. |
| `UPLOAD_INVALID_TYPE` | 400 | An uploaded file is not a real JPG/PNG/WebP (verified by magic bytes). |
| `UPLOAD_TOO_LARGE` | 400 | An uploaded file exceeds 2 MB. |
| `UNAUTHENTICATED` | 401 | Missing, malformed, or expired token. |
| `AUTH_INVALID_CREDENTIALS` | 401 | Wrong email **or** wrong password - deliberately indistinguishable. |
| `FORBIDDEN` | 403 | Insufficient role, or the resource belongs to another employee. |
| `DISH_NOT_FOUND` | 404 | Unknown dish, or an off-shelf dish requested by an employee. |
| `ORDER_NOT_FOUND` | 404 | Unknown order. |
| `NOT_FOUND` | 404 | Unknown route or stored image. |
| `EMAIL_ALREADY_REGISTERED` | 409 | The email is taken. |
| `DISH_UNAVAILABLE` | 409 | The dish was taken off the shelf after it was added to the cart. |
| `ORDER_SLOT_CONFLICT` | 409 | An active order already exists for that meal. `data` carries `existingOrderId` and `orderNo`. |
| `ORDER_IN_PROGRESS` | 409 | The same idempotency key is still being processed. Rare: a duplicate submit normally waits and returns the original order. |
| `ORDER_NOT_CANCELLABLE` | 409 | Only `Pending` orders can be cancelled. |
| `INTERNAL_ERROR` | 500 | Unexpected failure. Contains no stack trace; correlate with `requestId`. |

Example failure:

```json
{
  "code": "ORDER_SLOT_CONFLICT",
  "message": "You already have an active order for this meal. Please view it instead.",
  "data": { "existingOrderId": 42, "orderNo": "WB20260324-000123" },
  "requestId": "8f3c1a2e"
}
```

---

## Authentication

### POST /api/auth/register — public

Creates an employee account and returns a token, so a new user is signed in immediately.

| Field | Type | Rules |
| --- | --- | --- |
| `email` | string | required, valid email, ≤ 200 chars, must be unique (stored lower-cased) |
| `password` | string | required, 8–64 chars, must contain both a letter and a digit, no whitespace |
| `displayName` | string | required, ≤ 50 chars |

```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"alex@example.com","password":"Passw0rd123","displayName":"Alex Chen"}'
```

`201 Created`

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiJ9...",
    "tokenType": "Bearer",
    "expiresInSeconds": 28800,
    "user": { "id": 3, "email": "alex@example.com", "displayName": "Alex Chen", "role": "EMPLOYEE" }
  },
  "requestId": "a1b2c3d4"
}
```

### POST /api/auth/login — public

```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@webox.com","password":"Admin1234"}'
```

Same response shape as register. `200 OK`.

### GET /api/auth/me — authenticated

Returns the current user. The SPA calls this on cold start so a page refresh keeps the session.

```json
{ "id": 3, "email": "alex@example.com", "displayName": "Alex Chen", "role": "EMPLOYEE" }
```

---

## Menu

### GET /api/menu — authenticated

The daily menu, filtered, optionally re-ordered by preference, and paged.

| Query parameter | Type | Default | Notes |
| --- | --- | --- | --- |
| `date` | date | today | Menu date. |
| `categories` | string, repeatable | all | `?categories=Chinese&categories=Japanese` selects the union. |
| `keyword` | string | — | ≤ 50 chars; matched against **name and description**, case-insensitively. |
| `page` | int | 0 | |
| `size` | int | 12 | 1–50 |
| `prefer` | bool | false | Applies the caller's preferred cuisines and spice level to ordering and highlighting. |

```bash
curl 'http://localhost:8080/api/menu?date=2026-03-24&categories=Chinese&categories=Japanese&size=2' \
  -H "Authorization: Bearer $TOKEN"
```

```json
{
  "code": "OK",
  "message": "Success",
  "data": {
    "items": [
      {
        "dishId": 1,
        "name": "Kung Pao Chicken",
        "description": "Classic Sichuan dish, chicken stir-fried with peanuts and dried chili",
        "price": "22.00",
        "category": "Chinese",
        "spiceLevel": "Medium",
        "imageUrl": "/dishes/dish-01.jpg",
        "proteins": ["Chicken"],
        "allergens": ["Peanuts"],
        "dailyQty": 50,
        "remainingQty": 50,
        "hasOptions": false,
        "recommended": false,
        "matchTags": []
      }
    ],
    "page": 0,
    "size": 2,
    "total": 12,
    "totalPages": 6
  },
  "requestId": "3f9a11c2"
}
```

`hasOptions` tells the SPA whether "Add to Cart" can add directly or must open the detail page to
collect the required choices. `remainingQty` is populated from the daily menu row; the deduction
that makes it move is a Tier 3 extension point.

With `prefer=true` and configured preferences, matching dishes come first and carry
`recommended: true` plus `matchTags` such as `["Preferred cuisine","Matches your spice level"]`.

### GET /api/menu/dates — authenticated

Dates between today and today + 7 that have a published menu, ascending.

```json
["2026-03-24", "2026-03-25"]
```

### GET /api/menu/dishes/{id} — authenticated

Full detail: description, proteins, allergens, spice level, and the customization groups.

| Query parameter | Type | Notes |
| --- | --- | --- |
| `date` | date | Used to report that date's `dailyQty` / `remainingQty`. Defaults to today. |

```json
{
  "dishId": 9,
  "name": "Classic Beef Burger",
  "description": "Angus beef patty with lettuce, tomato, onion",
  "price": "38.00",
  "category": "Western",
  "spiceLevel": "None",
  "imageUrl": "/dishes/dish-09.jpg",
  "proteins": ["Beef"],
  "allergens": ["Gluten", "Dairy"],
  "available": true,
  "dailyQty": 50,
  "remainingQty": 50,
  "optionGroups": [
    {
      "groupId": 12,
      "name": "Bun",
      "required": true,
      "selectionType": "SINGLE",
      "options": [
        { "optionId": 31, "name": "Plain", "priceDelta": "0.00", "available": true },
        { "optionId": 32, "name": "Whole Wheat", "priceDelta": "0.00", "available": true }
      ]
    }
  ]
}
```

Requesting an off-shelf dish returns `404 DISH_NOT_FOUND`, because employees cannot see it.

---

## Preferences

### GET /api/users/me/preferences — authenticated

```json
{
  "allergens": ["Peanuts", "Egg"],
  "preferredCategories": ["Japanese", "Light Meal"],
  "spicePreference": "Mild",
  "tastePreference": "Light",
  "budgetMin": "15.00",
  "budgetMax": "30.00",
  "configured": true
}
```

All enum values travel as their English labels. `configured` is false when nothing has been saved.

### PUT /api/users/me/preferences — authenticated

Replaces the whole object, so the call is idempotent. All fields are optional.

```bash
curl -X PUT http://localhost:8080/api/users/me/preferences \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "allergens": ["Peanuts"],
        "preferredCategories": ["Japanese"],
        "spicePreference": "Mild",
        "tastePreference": "Light",
        "budgetMin": "15.00",
        "budgetMax": "30.00"
      }'
```

Returns `400 VALIDATION_FAILED` when `budgetMin > budgetMax`.

**Semantics worth noting**: allergens produce a warning when a matching dish is added to the cart -
they never hide a dish. `tastePreference` is stored for recommendations and never reorders the menu.

### GET /api/users/me/addresses — authenticated

Delivery addresses used previously, most recent first. The checkout page offers these and
remembers any new address after a successful order.

```json
[ { "id": 5, "addressText": "Building A, Desk 101", "useCount": 3, "lastUsedAt": "2026-03-24T02:10:11Z" } ]
```

---

## Orders

### POST /api/orders/checkout-preview — authenticated

Prices and validates the cart before anything is created, so problems surface on page entry
rather than after pressing "Place Order". It accepts **only** what it needs; the delivery address
is not part of this contract.

```bash
curl -X POST http://localhost:8080/api/orders/checkout-preview \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{
        "items": [{ "lineKey": "9#31-40", "dishId": 9, "quantity": 2, "optionIds": [31, 40] }],
        "deliveryDate": "2026-03-24",
        "mealSlot": "Lunch"
      }'
```

```json
{
  "bookableDates": ["2026-03-24", "2026-03-25"],
  "resolved": {
    "mealDate": "2026-03-24",
    "mealSlot": "Dinner",
    "adjusted": true,
    "reasonCode": "LUNCH_CUTOFF_PASSED"
  },
  "pricedItems": [
    {
      "lineKey": "9#31-40",
      "dishId": 9,
      "name": "Classic Beef Burger",
      "unitPrice": "43.00",
      "quantity": 2,
      "subtotal": "86.00",
      "optionLabels": ["Plain", "Mustard"]
    }
  ],
  "total": "86.00",
  "unavailableItems": [],
  "existingOrder": null,
  "budget": { "min": "15.00", "max": "30.00", "cartTotal": "86.00", "exceedsMax": true }
}
```

* `resolved.adjusted = true` means the cut-off rules moved the meal. `reasonCode` is
  `LUNCH_CUTOFF_PASSED` or `DINNER_CUTOFF_PASSED`.
* `unavailableItems[].reason` is one of `DISH_NOT_FOUND`, `DISH_UNAVAILABLE`, `DISH_NOT_ON_MENU`,
  `OPTION_INVALID`.
* `existingOrder` being non-null is the signal to render **"View Existing Order"** instead of
  "Place Order" (PRD 4.2).
* `budget.exceedsMax` drives a non-blocking warning; ordering is never blocked by budget.

### POST /api/orders — authenticated

Places an order. **The `Idempotency-Key` header is mandatory.** Generate it once when the checkout
page mounts and reuse it for every retry of that submission.

| Header | Value |
| --- | --- |
| `Idempotency-Key` | client-generated UUID, ≤ 64 chars |

| Body field | Type | Rules |
| --- | --- | --- |
| `items[].lineKey` | string | optional, ≤ 120 chars, echoed back so the client can match rows |
| `items[].dishId` | long | required |
| `items[].quantity` | int | 1–5 per line; the **sum** across lines must be ≤ 5 |
| `items[].optionIds` | long[] | chosen option ids; validated against the dish |
| `deliveryDate` | date | required; today … today + 7 |
| `mealSlot` | `Lunch` \| `Dinner` | required |
| `deliveryAddress` | string | required, ≤ 200 chars |
| `note` | string | optional, ≤ 200 chars |

The server ignores any price the client sends and recomputes everything from the database.

```bash
curl -X POST http://localhost:8080/api/orders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Idempotency-Key: $(uuidgen)" \
  -H 'Content-Type: application/json' \
  -d '{
        "items": [{ "lineKey": "9#31-40", "dishId": 9, "quantity": 2, "optionIds": [31, 40] }],
        "deliveryDate": "2026-03-24",
        "mealSlot": "Lunch",
        "deliveryAddress": "Building A, Desk 101",
        "note": "No cutlery please"
      }'
```

```json
{
  "id": 42,
  "orderNo": "WB20260324-000123",
  "status": "Pending",
  "mealDate": "2026-03-24",
  "mealSlot": "Dinner",
  "requestedMealSlot": "Lunch",
  "slotAdjusted": true,
  "deliveryAddress": "Building A, Desk 101",
  "note": "No cutlery please",
  "itemCount": 2,
  "totalAmount": "86.00",
  "createdAt": "2026-03-24T02:10:11.123Z",
  "cancelledAt": null,
  "duplicated": false,
  "items": [
    {
      "id": 77,
      "dishId": 9,
      "dishName": "Classic Beef Burger",
      "imageUrl": "/dishes/dish-09.jpg",
      "unitBasePrice": "38.00",
      "unitPrice": "43.00",
      "quantity": 2,
      "subtotal": "86.00",
      "options": [
        { "groupName": "Bun", "optionName": "Plain", "priceDelta": "0.00" },
        { "groupName": "Sauce", "optionName": "Mustard", "priceDelta": "0.00" }
      ]
    }
  ]
}
```

`duplicated: true` marks a response that replayed an existing order instead of creating one. The
same is true of the order lines: they are immutable snapshots, so later edits to the dish never
change a historical order.

**Guarantees**

* Submitting the same key concurrently 10 times creates exactly one order; all 10 responses carry
  the same `orderNo`, and 9 of them report `duplicated: true`.
* At most one `Pending`/`Confirmed` order exists per employee per meal. A racing request receives
  `409 ORDER_SLOT_CONFLICT` with the existing order in `data`.
* Both guarantees are enforced by unique indexes in MySQL, so they hold even if Redis is down or
  several application instances run in parallel.

### GET /api/orders — authenticated

My orders, newest first. `page` (default 0) and `size` (default 10, max 50).

### GET /api/orders/{id} — authenticated (owner only)

Full order detail including the option snapshot. Another employee's order returns
`403 FORBIDDEN`.

### POST /api/orders/{id}/cancel — authenticated (owner only)

Cancels a `Pending` order and releases its meal slot immediately.

* `Pending` → cancelled, `200`.
* Already `Cancelled` → `200` with the current state (idempotent).
* `Confirmed` or `Completed` → `409 ORDER_NOT_CANCELLABLE`.

---

## Console (ADMIN role required)

### GET /api/admin/dishes

| Query parameter | Type | Notes |
| --- | --- | --- |
| `keyword` | string | ≤ 50 chars, matched against the dish name |
| `category` | string | one of the six cuisine labels |
| `available` | bool | shelf-status filter |
| `page`, `size` | int | default 0 / 10, max 50 |

Returns the same paged envelope, with each dish carrying its full option graph.

### GET /api/admin/dishes/{id}

A single dish, including option groups and options.

### POST /api/admin/dishes — `201 Created`

### PUT /api/admin/dishes/{id}

Both take the same body. `optionGroups` **replaces** the dish's option graph in one transaction.
Orders already placed are unaffected because their lines are snapshots.

```json
{
  "name": "Smoke Test Bowl",
  "description": "Dish created by the automated test",
  "price": "19.90",
  "category": "Light Meal",
  "spiceLevel": "Mild",
  "imageUrl": "/api/files/images/6f1c...e2.jpg",
  "allergens": ["Soy"],
  "proteins": ["Tofu"],
  "available": true,
  "optionGroups": [
    {
      "name": "Base",
      "required": true,
      "selectionType": "SINGLE",
      "options": [
        { "name": "Rice", "priceDelta": "0.00", "available": true },
        { "name": "Quinoa", "priceDelta": "3.00", "available": true }
      ]
    },
    {
      "name": "Add-ons",
      "required": false,
      "selectionType": "MULTI",
      "options": [{ "name": "Avocado", "priceDelta": "4.00", "available": true }]
    }
  ]
}
```

Validation: name ≤ 100, description ≤ 500, price > 0 with at most 2 decimals, option names ≤ 60,
extra price ≥ 0, and a required group must contain at least one option.

### PATCH /api/admin/dishes/{id}/availability

```json
{ "available": false }
```

Taking a dish off the shelf makes it disappear from employee menus immediately (the menu cache is
invalidated in the same transaction).

### GET /api/admin/dishes/cache-stats

Menu-cache counters, useful for demonstrating the caching strategy.

```json
{ "hits": 128, "misses": 6, "fallbacks": 0, "ttlMinutes": 10 }
```

`hits` - served from Redis, `misses` - loaded from MySQL, `fallbacks` - Redis was unreachable and
MySQL served the request.

### GET /api/admin/daily-menus/{date}

Menu published for a date; an empty list when nothing is scheduled.

```json
{
  "menuDate": "2026-03-25",
  "items": [
    { "dishId": 1, "name": "Kung Pao Chicken", "category": "Chinese", "available": true,
      "totalQty": 40, "soldQty": 0, "displayOrder": 0 }
  ]
}
```

### PUT /api/admin/daily-menus/{date}

Replaces the dish list for that date, so resubmitting the same body is a no-op. Rows for dishes
that stay on the menu are updated in place, which preserves `soldQty` if today's menu is edited
mid-service.

```json
{ "items": [ { "dishId": 1, "totalQty": 40 }, { "dishId": 2, "totalQty": 25 } ] }
```

The SPA defaults the date to tomorrow and offers a "Today" shortcut, so the same screen both
schedules tomorrow and adjusts today.

### POST /api/admin/files/images

`multipart/form-data` with a single `file` field. JPG, PNG or WebP, at most 2 MB. The type is
verified from the **magic bytes**, not the declared name, and the stored name is a fresh UUID -
the original filename is discarded.

```json
{ "url": "/api/files/images/6f1c8f0e-3a1f-4d2b-9d4e-7c2f9a1b3e5d.jpg" }
```

### GET /api/files/images/{name} — public

Serves a stored image. The name must match the generated-UUID allow-list, so path traversal
attempts are rejected before the filesystem is touched.

---

## Order administration (supplement)

PRD section 1 describes the administrator as someone who manages dishes **and processes orders**,
while section 4.3 details only dish management for Tier 2. These two endpoints are the smallest
addition that honours that description and can be removed without affecting Tier 1 or Tier 2
completeness.

### GET /api/admin/orders

| Query parameter | Type | Notes |
| --- | --- | --- |
| `date` | date | filter by meal date |
| `status` | string | `Pending` / `Confirmed` / `Completed` / `Cancelled` |
| `page`, `size` | int | |

### PATCH /api/admin/orders/{id}/status

```json
{ "targetStatus": "Confirmed" }
```

Allowed transitions: `Pending → Confirmed`, `Confirmed → Completed`. `Cancelled` is accepted and
routed through the normal cancellation rule, so only `Pending` orders can be cancelled.

---

## Health and operations

| Endpoint | Purpose |
| --- | --- |
| `GET /actuator/health` | readiness, including `db` and `redis` component status |
| `GET /actuator/metrics` | Micrometer metrics |
| `GET /swagger-ui.html` | interactive API documentation |
| `GET /v3/api-docs` | OpenAPI 3 document |
