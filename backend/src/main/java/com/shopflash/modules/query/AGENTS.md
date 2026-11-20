# Module query

Chủ đề: Query processing.

## Chức năng

- F5 Báo cáo doanh thu (theo tháng, theo danh mục)
- F6 Top bán chạy, khách VIP, sản phẩm chưa từng bán
- Phòng thí nghiệm so sánh Oracle và Redis: chạy cùng một báo cáo trên hai nguồn, xem kế hoạch thực thi, đo thời gian

## Quyết định đã chốt

- **Đơn được tính:** `status IN ('CONFIRMED','COMPLETED')`. Doanh thu theo đơn lấy `orders.total_amount`; doanh thu theo danh mục/sản phẩm lấy `SUM(order_items.line_total)` (trước `discount_amount`, nên hai con số có thể lệch nhau).
- **Múi giờ:** mọi mốc ngày và tháng tính theo `+07:00`. Tham số `from`/`to` là ngày `YYYY-MM-DD`, cả hai đầu đều tính trọn ngày. Oracle: `created_at >= :fromTs AND created_at < :toExclusiveTs`, tháng = `TO_CHAR(created_at AT TIME ZONE '+07:00','YYYY-MM')`.
- **Oracle là nguồn gốc.** Redis chỉ giữ read model denormalized, do module này dựng lại toàn bộ (`POST .../analytics/rebuild`). Không động vào luồng checkout của module transaction. Dữ liệu Redis có thể trễ (eventual consistency) cho đến lần rebuild sau.
- **Client Redis:** Jedis `RedisClient` (pool, RESP2; `JedisPooled` đã deprecated ở Jedis 7.4), dùng cho `JSON.SET`, `FT.AGGREGATE`, `FT.PROFILE`. Bỏ key `rank:bestseller` của kế hoạch cũ.
- **FT.AGGREGATE luôn kèm `TIMEOUT 0`:** Redis đang đặt `TIMEOUT 500` với `ON_TIMEOUT return`, nghĩa là quá 500 ms thì trả **một phần** kết quả mà không báo lỗi.
- **Chế độ `CLIENT`** đọc bằng `FT.AGGREGATE … WITHCURSOR COUNT 10000` + `FT.CURSOR READ`, vì `FT.SEARCH` không phân trang quá 10.000 kết quả (`MAXSEARCHRESULTS`). Gom nhóm, cộng và sắp xếp làm trong Java.
- **Giới hạn số của RediSearch:** giá trị tính ra (SUM, AVG) được in với 12 chữ số có nghĩa, nên tổng từ 10^12 trở lên có thể mất hàng đơn vị. Dữ liệu hiện tại (VND tròn, tổng mỗi nhóm dưới 10^12) vẫn khớp Oracle tuyệt đối; chế độ `CLIENT` đọc giá trị gốc nên luôn chính xác.
- **Cache-aside (`source=cache`):** tra `report:cache:*` trên Redis; có thì trả luôn (`variant=HIT`, 1 round-trip), không có thì chạy Oracle (hint theo `joinHint`), `SET … PX` với TTL `shopflash.query.cache-ttl` (mặc định 5 phút) rồi trả (`variant=MISS`). Không xóa cache khi đơn đổi: kết quả có thể cũ tới hết TTL. Redis lỗi thì vẫn trả kết quả Oracle, ghi chú trong `meta.note`. `plan` của nguồn này là plan Oracle.
- **Truy cập Oracle:** `NamedParameterJdbcTemplate`, luôn bind variable. Hint join được chèn từ enum cố định, không nhận chuỗi từ client.
- **Quyền:** endpoint thuộc `/api/admin/**`. Khi `common/security` (F0) có rồi thì chỉ cho `ADMIN` gọi; trước đó để mở.
- **Chống CSRF tạm thời (chưa có F0):** ba POST đổi trạng thái (`/analytics/rebuild`, `/oracle/stats`, `/{report}/benchmark`) chỉ nhận `Content-Type: application/json`. Form HTML không gửi được kiểu này, còn `fetch` khác origin phải qua preflight CORS, mà backend không cấu hình CORS nên bị chặn. Sai hoặc thiếu `Content-Type` thì `415 UNSUPPORTED_MEDIA_TYPE`, không chạy gì.

## Dữ liệu

### Oracle

Không có bảng riêng. Đọc `orders`, `order_items`, `products`, `categories`, `users`.

### Redis

| Key | Kiểu | Nội dung |
|---|---|---|
| `order:analytics:{orderId}` | JSON | Một đơn: `orderId`, `userId`, `userName`, `userEmail`, `status`, `orderType`, `totalAmount`, `createdAt` (epoch giây), `items[]` như DATABASE_DESIGN §27. Index `idx:order-analytics` có sẵn trong `init.lua` |
| `order:line:{orderItemId}` | JSON | Một dòng hàng, phẳng: `orderId`, `userId`, `status`, `createdAt`, `productId`, `productName`, `categoryId`, `categoryName`, `quantity`, `lineTotal` |
| `idx:order-line-analytics` | RediSearch | `ON JSON PREFIX 1 order:line:`, schema trong DATABASE_DESIGN §28.1, tạo bởi `redis/init.lua`. `productName`, `categoryName` không index, đọc bằng `LOAD` |
| `analytics:meta` | HASH | `lastRebuildAt`, `orders`, `lines`, `elapsedMs` |
| `report:cache:{report}:{from}:{to}:{joinHint}:limit={n}:page={n}:size={n}` | STRING (JSON) | Kết quả báo cáo Oracle đã tính: `cachedAt`, `rows`, `page`, `summary`. Hết hạn theo TTL |

Lý do có `order:line:*`: gom nhóm theo trường nhiều giá trị (`$.items[*]`) trong `FT.AGGREGATE` không cho kết quả theo từng dòng hàng, nên báo cáo theo danh mục/sản phẩm dùng bản phẳng.

## API (contract, đã chốt)

Base: `/api/admin/reports`. JSON, tiền là số (VND), thời gian đo là `ms` kiểu số thực.

### Tên báo cáo `{report}`

| `{report}` | F | Loại query | Oracle | Redis |
|---|---|---|---|---|
| `revenue-by-month` | F5 | f, h | `GROUP BY` tháng | `FT.AGGREGATE` trên `idx:order-analytics`, `APPLY timefmt(@createdAt+25200,"%Y-%m")` |
| `revenue-by-category` | F5 | f, h | join 4 bảng, `GROUP BY` | `FT.AGGREGATE` trên `idx:order-line-analytics` |
| `top-products` | F6 | f, h | join 4 bảng (cần `categories` cho `categoryName`), `GROUP BY`, `FETCH FIRST :limit` | như trên, `SORTBY ... MAX :limit` |
| `vip-customers` | F6 | f, g, h | `HAVING SUM(...) > (SELECT AVG(...) ...)` trong một câu | 2 lượt: tính trung bình, rồi lọc. Không có subquery |
| `unsold-products` | F6 | g | `NOT EXISTS` | Không hỗ trợ: trả `meta.supported=false` |

### Tham số chung

| Tham số | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `source` | `oracle` \| `redis` \| `cache` | `oracle` | `cache`: kết quả Oracle giữ trong Redis (cache-aside) |
| `from`, `to` | `YYYY-MM-DD`, năm 2000–2100 | 12 tháng gần nhất đến hôm nay | `unsold-products` bỏ qua |
| `joinHint` | `AUTO` \| `NL` \| `HASH` \| `MERGE` | `AUTO` | Chỉ Oracle; có tác dụng với `revenue-by-category`, `top-products`, `vip-customers` |
| `redisMode` | `AGGREGATE` \| `CLIENT` | `AGGREGATE` | Chỉ Redis. `CLIENT` = kéo dữ liệu về Java rồi tự tính, để thấy số round-trip |
| `limit` | 1–100 | 10 | `top-products` |
| `page`, `size` | `page` 1–100000, `size` 1–100 | 1, 20 | `vip-customers`, `unsold-products` |

### `meta` (có trong mọi response báo cáo)

```json
{
  "source": "oracle",
  "report": "top-products",
  "variant": "HASH",
  "supported": true,
  "elapsedMs": 12.84,
  "roundTrips": 1,
  "statement": "SELECT /*+ USE_HASH(oi p) */ ...",
  "rowCount": 10,
  "generatedAt": "2026-10-18T15:04:05+07:00",
  "note": null
}
```

`variant` = `joinHint` với Oracle, `redisMode` với Redis, `HIT`/`MISS` với cache. `statement` = câu SQL (giữ tên bind, không thay giá trị) hoặc các lệnh Redis, mỗi lệnh một dòng. Báo cáo cần 2 câu SQL (`vip-customers`: danh sách + tổng/trung bình; `unsold-products`: danh sách + đếm) thì nối 2 câu bằng `;` và một dòng trống, `roundTrips` = 2. `elapsedMs` chỉ tính thời gian gọi DB và xử lý kết quả, không tính serialize HTTP. Khi `supported=false` thì `data` là `null` và `note` ghi lý do.

### 1. `GET /api/admin/reports/{report}`

Response `200`:

```json
{ "data": { "rows": [ ... ], "page": null, "summary": null }, "meta": { ... } }
```

Dạng `rows` theo báo cáo:

| `{report}` | Một dòng | Sắp xếp |
|---|---|---|
| `revenue-by-month` | `{ "month": "2026-10", "orderCount": 812, "revenue": 912345000 }` | `month` tăng dần |
| `revenue-by-category` | `{ "categoryId": 10003, "categoryName": "Điện thoại", "quantity": 2210, "revenue": 1234500000 }` | `revenue` giảm dần, rồi `categoryId` |
| `top-products` | `{ "rank": 1, "productId": 10456, "productName": "...", "categoryName": "...", "quantity": 340, "revenue": 98000000 }` | `quantity` giảm dần, rồi `productId` |
| `vip-customers` | `{ "userId": 10021, "fullName": "...", "email": "...", "orderCount": 14, "totalSpent": 45600000 }` | `totalSpent` giảm dần, rồi `userId` |
| `unsold-products` | `{ "productId": 10999, "productName": "...", "categoryName": "...", "basePrice": 250000 }` | `productId` tăng dần |

`page` (chỉ `vip-customers`, `unsold-products`): `{ "page": 1, "size": 20, "total": 1534 }`.
`summary` (chỉ `vip-customers`): `{ "averageSpent": 3120000, "customerCount": 18234 }`.

### 2. `GET /api/admin/reports/{report}/compare`

Chạy cùng tham số trên cả hai nguồn (bỏ `source`). Response `200`:

```json
{
  "report": "top-products",
  "oracle": { "data": { ... }, "meta": { ... } },
  "redis":  { "data": { ... }, "meta": { ... } },
  "match": true,
  "mismatches": []
}
```

So từng dòng theo khóa (`month`, `categoryId`, `productId`, `userId`), tiền làm tròn 2 chữ số. `mismatches` tối đa 5 phần tử `{ "key": "10456", "field": "revenue", "oracle": 98000000, "redis": 97000000 }`. Nếu một bên `supported=false` thì `match` là `null`.

### 3. `GET /api/admin/reports/{report}/plan`

Tham số: `source`, `joinHint`, `redisMode` và các tham số của báo cáo. Response `200`:

```json
{ "source": "oracle", "variant": "HASH", "statement": "...", "lines": ["Plan hash value: ...", "| Id | Operation | ..."] }
```

Oracle: chạy thật câu SQL với hint `GATHER_PLAN_STATISTICS` rồi `DBMS_XPLAN.DISPLAY_CURSOR(NULL, NULL, 'ALLSTATS LAST')` trên cùng connection, nên kế hoạch có cả E-Rows và A-Rows (số dòng ước lượng và thực tế); lỗi thì quay về `EXPLAIN PLAN` + `DBMS_XPLAN.DISPLAY`. Vì chạy thật nên tốn thời gian như chạy báo cáo. Với `vip-customers` chỉ lấy kế hoạch của câu chính (có `HAVING`). Redis `AGGREGATE`: kết quả `FT.PROFILE ... AGGREGATE`, làm phẳng thành từng dòng. Redis `CLIENT`: mô tả các lệnh sẽ gửi và số round-trip.

### 4. `POST /api/admin/reports/{report}/benchmark`

Request (mọi trường đều không bắt buộc; body có thể rỗng nhưng vẫn phải gửi `Content-Type: application/json`):

```json
{ "from": "2025-11-01", "to": "2026-10-31", "limit": 10, "warmup": 1, "runs": 5,
  "variants": [ { "source": "oracle", "variant": "AUTO" }, { "source": "redis", "variant": "AGGREGATE" } ] }
```

Mặc định `warmup=1`, `runs=5` (tối đa 30); `variants` mặc định gồm 4 hint Oracle và 2 chế độ Redis; đo cache thì gửi `{ "source": "cache", "variant": "HIT" }` (luôn chạy ít nhất 1 lần khởi động để có kết quả trong cache). `variants` tối đa 12 phần tử; cặp `(source, variant)` trùng nhau chỉ chạy một lần, giữ thứ tự gửi lên. `from`/`to` cùng quy tắc năm 2000–2100 như tham số chung. Response `200`:

```json
{
  "report": "top-products",
  "results": [
    { "source": "oracle", "variant": "HASH", "runs": 5, "meanMs": 21.4, "stdDevMs": 1.2, "p50Ms": 21.0, "p95Ms": 23.1, "roundTrips": 1, "samplesMs": [20.1, 21.0, 22.9, 21.0, 23.1] }
  ]
}
```

Gửi `Accept: text/csv` thì trả CSV, mỗi mẫu một dòng: `report,source,variant,run,elapsedMs,roundTrips`.

### 5. `GET /api/admin/reports/analytics/status`

```json
{
  "oracle": { "countedOrders": 98231, "orderItems": 245102 },
  "redis":  { "orderDocs": 98231, "lineDocs": 245102, "indexing": false, "lastRebuildAt": "2026-10-18T14:00:00+07:00", "lastRebuildMs": 41230 },
  "inSync": true
}
```

`inSync` = số đơn được tính và số dòng hàng hai bên bằng nhau.

### 6. `POST /api/admin/reports/analytics/rebuild`

Request: `Content-Type: application/json`, body `{}` (hoặc rỗng); không có trường nào. Ghi đè mọi document từ Oracle theo lô (pipeline, khoảng 1000 đơn một lô), sau đó dùng `SCAN` + `UNLINK` xóa các key không còn trong Oracle. Kết quả cuối giống "xóa hết rồi nạp lại", nhưng báo cáo không bao giờ thấy read model trống trong lúc rebuild. Mỗi lúc chỉ một lần rebuild: gọi khi đang có lần khác chạy thì trả ngay `409 REBUILD_IN_PROGRESS`, không chờ. Đồng bộ, trả khi xong:

```json
{ "orders": 98231, "lines": 245102, "elapsedMs": 41230.5 }
```

### 7. `POST /api/admin/reports/oracle/stats`

Request (`Content-Type: application/json`) `{ "action": "GATHER" | "DELETE" }`. Gọi `DBMS_STATS.GATHER_TABLE_STATS` / `DELETE_TABLE_STATS` trên `ORDERS`, `ORDER_ITEMS`, `PRODUCTS`, `CATEGORIES`, `USERS` của schema `SHOP`. Response:

```json
{ "action": "GATHER", "tables": [ { "table": "ORDERS", "numRows": 100000, "lastAnalyzed": "2026-10-18T14:05:00+07:00" } ], "elapsedMs": 3120.2 }
```

### Lỗi

```json
{ "error": { "code": "VALIDATION_ERROR", "message": "size phải từ 1 đến 100", "details": { "field": "size" } } }
```

| HTTP | `code` |
|---|---|
| 400 | `VALIDATION_ERROR` (tham số sai, `from` > `to`, `{report}` không tồn tại) |
| 409 | `REBUILD_IN_PROGRESS` (đang có một lần rebuild khác chạy) |
| 415 | `UNSUPPORTED_MEDIA_TYPE` (POST đổi trạng thái không gửi `Content-Type: application/json`; `details.supported = ["application/json"]`) |
| 503 | `REDIS_UNAVAILABLE`, `REDIS_NOT_BUILT` (chưa rebuild) |
| 500 | `ORACLE_ERROR`, `INTERNAL_ERROR` |

`message` luôn là câu tiếng Việt cố định, không chép thông báo gốc của driver Oracle/Redis (host, user, tên bảng chỉ nằm trong log). Với `ORACLE_ERROR`, nếu lỗi gốc có mã Oracle thì `details.oraCode` mang mã đó, ví dụ `{ "oraCode": "ORA-00942" }`; không có mã thì `details` là `null`.

## Phụ thuộc

- `backend/pom.xml`: thêm `redis.clients:jedis` (đã báo nhóm).
- Frontend: khung `src/app`, `shared/api`, `shared/layouts` chưa ai dựng; module này dựng bản tối thiểu và báo nhóm.
- Dữ liệu demo: `backend/src/main/resources/db/history-seed/` sinh danh mục, sản phẩm, khách, đơn. Chỉ chạy tay ở máy local, không phải migration.
