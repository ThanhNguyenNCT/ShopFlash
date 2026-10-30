# Module indexing

Chủ đề: Indexing. Người phụ trách: TV2.

## Chức năng

- F3 Tìm kiếm sản phẩm theo từ khóa trong tên.
- F4 Lọc nâng cao: danh mục, khoảng giá, còn hàng; sắp xếp; phân trang.
- Trang chi tiết sản phẩm, danh sách danh mục cho bộ lọc.
- Phòng thí nghiệm Indexing (admin): chạy cùng một yêu cầu trên Oracle và Redis, có và không có index, xem kế hoạch thực thi, dung lượng index.

Nghiên cứu, số đo và lý do lựa chọn: `report/indexing/` (`ket-qua.md` E1–E6, `ket-luan.md`).

## Quyết định đã chốt

- **Mỗi DBMS làm việc nó giỏi nhất; mỗi yêu cầu đi tới đúng một nguồn, không trộn kết quả** (`report/indexing/ket-luan.md` §3):

  | Yêu cầu | Nguồn | Index | Bằng chứng |
  |---|---|---|---|
  | Có từ khóa `q` (kèm hoặc không kèm bộ lọc) | RediSearch `idx:products` | `name TEXT` + `categoryId`, `price`, `status`, `inStock` | E2: nhanh hơn Oracle Text 1,3–18 lần |
  | Không có `q` (duyệt danh mục, lọc giá) | Oracle | `ix_products_cat_status_price (category_id, status, base_price, product_id)`; `ix_products_price` khi không lọc danh mục | E3: index ghép nhanh hơn RediSearch 10–43 lần |
  | Chi tiết sản phẩm, tồn kho | Oracle | Khóa chính | Dữ liệu gốc, mới nhất |
  | Có `q` nhưng Redis lỗi (`REDIS_UNAVAILABLE`) | Oracle Text | `ix_products_name_text` (CONTEXT) | E2: 1–6 ms; `meta.note` ghi lý do |

- **Sắp xếp:** `relevance` (chỉ khi có `q`), `price_asc`, `price_desc`. Mặc định: `relevance` nếu có `q`, ngược lại `price_asc`. Oracle luôn thêm `product_id` để thứ tự ổn định khi trùng giá/điểm; RediSearch `SORTBY` chỉ một trường nên khi trùng giá thứ tự giữa các trang có thể không ổn định (giới hạn ghi trong báo cáo). Chưa làm "mới nhất", "bán chạy" (cần thêm trường vào projection của storage); để sau nếu có nhu cầu.
- **Phạm vi hiển thị:** chỉ sản phẩm `status = ACTIVE`; `inStock=true` thì chỉ sản phẩm còn hàng. Không lọc theo trạng thái danh mục, vì document Redis không có trường này; hai nguồn phải cho cùng kết quả. Ghi là giới hạn trong báo cáo.
- **Oracle là nguồn gốc, Redis là projection.** Module này **chỉ đọc** `product:*` và `idx:products` (do storage ghi và `init.lua` tạo); không ghi vào chúng, không sửa `init.lua`.
- **Cùng một cách tách từ khóa ở hai nguồn** (`model/Keywords`): cắt `q` thành tối đa 5 từ, mỗi từ ≥ 2 ký tự, chỉ giữ chữ và số (kể cả chữ có dấu), chữ thường, bỏ trùng; **bỏ toán tử của Oracle Text** (`and`, `or`, `not`, `near`...) và stopword tiếng Anh mặc định của RediSearch, để hai nguồn tìm như nhau và Oracle Text không lỗi cú pháp. Các từ nối bằng AND, mỗi từ tìm theo tiền tố.
  - Redis: `@name:(tai* nghe*)`, các ký tự đặc biệt của RediSearch đã bị loại khi tách từ.
  - Oracle Text: `CONTAINS(p.name, :textQuery, 1) > 0`, `:textQuery = 'tai% AND nghe%'` là **giá trị bind**.
  - Không bao giờ nối chuỗi người dùng vào SQL hay câu lệnh Redis.
- **`FT.SEARCH` luôn kèm `TIMEOUT 0`** (Redis đặt `TIMEOUT 500` + `ON_TIMEOUT return`: quá giờ trả một phần kết quả mà không báo lỗi).
- **Phân trang:** `page * size ≤ 10.000` ở mọi nguồn (RediSearch không trả quá `MAXSEARCHRESULTS` = 10.000).
- **Truy cập Oracle:** `NamedParameterJdbcTemplate`, luôn bind variable. Hint của trang Lab (`FULL`) lấy từ enum cố định, không nhận chuỗi từ client.
- **Index thí nghiệm** (so có/không index, chi phí ghi) chỉ tạo trên bảng lab `IDXLAB_*` bằng script trong `report/indexing/`, không vào migration.

## Dữ liệu

### Oracle

Đọc `products`, `categories`, `product_inventory`. Không thêm bảng.

Migration `V3__indexing_product_search.sql` (đổi số nếu lúc mở PR đã có V3 khác trên `main`):

| Index | Định nghĩa | Ghi chú |
|---|---|---|
| `ix_products_cat_status_price` | `products (category_id, status, base_price, product_id)` | F4. `product_id` ở cuối để thứ tự ổn định khi trùng giá (`ORDER BY base_price, product_id`) mà Oracle vẫn không phải sắp xếp lại. Bắt đầu bằng `category_id` nên vẫn thỏa quy tắc khóa ngoại có index. `ix_products_category_status` (V1) thành thừa; **tạm giữ**, bỏ khi nhóm đồng ý |
| `ix_products_name_text` | `products (name) INDEXTYPE IS CTXSYS.CONTEXT` | Dự phòng khi Redis lỗi. Oracle 26ai mặc định tự bảo trì ở chế độ nền (kiểm tra mỗi 3 s, E6). User `SHOP` không có quyền `CTX_DDL`, không cần gọi đồng bộ tay |

Hai index này làm đổi số đo footprint của TV1 (B-tree mới được tính; bảng phụ `DR$...` của Oracle Text thì không). TV1 đã biết và sẽ tự đo lại khi chuẩn bị demo.

### Redis

| Key | Kiểu | Chủ sở hữu | Ghi chú |
|---|---|---|---|
| `product:{id}`, `idx:products` | JSON, RediSearch | storage (TV1), foundation | Chỉ đọc |
| `idxlab:*`, `idx:idxlab-*` | JSON, RediSearch, Sorted Set | indexing, chỉ trong script `report/indexing/redis/` | Tạo rồi xóa. Prefix `idxlab:` không trùng `product:` |

## API

JSON, tiền là số (VND), `elapsedMs` là số thực.

### `meta` (trong mọi response tìm kiếm)

```json
{
  "source": "redis",
  "route": "KEYWORD",
  "variant": "SEARCH",
  "elapsedMs": 0.91,
  "roundTrips": 1,
  "statement": "FT.SEARCH idx:products \"@name:(sony* pro*) @status:{ACTIVE}\" SORTBY ... LIMIT 0 24 TIMEOUT 0",
  "total": 1679,
  "rowCount": 24,
  "generatedAt": "2026-10-30T10:00:00+07:00",
  "note": null
}
```

`route`: `KEYWORD` (có `q`) hoặc `BROWSE` (không có `q`). `statement` giữ tên bind, không thay giá trị. Oracle cần 2 câu (trang + đếm) nên `roundTrips = 2`; RediSearch trả tổng số trong cùng lệnh nên `roundTrips = 1`.

### Công khai (khách hàng, chỉ GET)

`common/security/SecurityConfig` có `.requestMatchers(HttpMethod.GET, "/api/products", "/api/products/**").permitAll()` (TV2 thêm, cần TV4 review). Lưu ý: client frontend gắn token nếu có; token hết hạn thì Spring trả 401 kể cả với đường dẫn `permitAll`, client tự xóa token. Trang của module có nút Thử lại cho trường hợp này.

**1. `GET /api/products/search`**

| Tham số | Kiểu | Mặc định | Ghi chú |
|---|---|---|---|
| `q` | chuỗi ≤ 100 | rỗng | Có `q` → RediSearch; rỗng → Oracle |
| `categoryId` | số | | |
| `minPrice`, `maxPrice` | số ≥ 0 | | `minPrice ≤ maxPrice` |
| `inStock` | boolean | `false` | |
| `sort` | `relevance` \| `price_asc` \| `price_desc` | xem trên | `relevance` không có `q` → `400` |
| `page`, `size` | `page` ≥ 1, `size` 1–48 | 1, 24 | `page * size ≤ 10000` |

Response `200`:

```json
{
  "data": {
    "items": [
      { "productId": 10001, "sku": "TN-ANC-01", "name": "Tai nghe ...", "categoryId": 10003,
        "categoryName": "Tai nghe", "price": 549000, "inStock": true, "thumbnailUrl": null }
    ],
    "page": { "page": 1, "size": 24, "total": 128 }
  },
  "meta": { ... }
}
```

Lỗi: `400 VALIDATION_ERROR` (tham số sai, `minPrice > maxPrice`, vượt 10.000 kết quả, `relevance` không có `q`).

**2. `GET /api/products/{productId}`**: chi tiết từ Oracle (thêm `description`, `availableQuantity`, `status`). `404 PRODUCT_NOT_FOUND` nếu không có hoặc `INACTIVE`.

**3. `GET /api/products/categories`**: danh mục `ACTIVE` kèm số sản phẩm `ACTIVE` của mỗi danh mục, cho bộ lọc. Oracle `JOIN` + `GROUP BY`.

### Quản trị (phòng thí nghiệm, `/api/admin/indexing/**`, đã có quyền ADMIN)

**4. `GET /api/admin/indexing/search`**: như API 1, thêm tham số chọn nguồn và biến thể:

| Tham số | Giá trị | Ý nghĩa |
|---|---|---|
| `source` | `auto` (mặc định) \| `oracle` \| `redis` | `auto` = định tuyến như API 1 |
| `oracleVariant` | `AUTO` | Optimizer tự chọn (Oracle Text / B-tree) |
| | `LIKE` | Tìm tên bằng `UPPER(name) LIKE :pattern` (`%KW%`), không dùng Oracle Text |
| | `FULL` | Hint `/*+ FULL(p) */`: ép quét toàn bảng, giả lập "không có index" |
| `redisVariant` | `SEARCH` | `FT.SEARCH` trên `idx:products` |
| | `SCAN` | Không dùng index: `SCAN product:*` + `JSON.GET`, lọc và sắp xếp trong Java; đếm round-trip |

**5. `GET /api/admin/indexing/compare`**: chạy cùng tham số trên cả hai nguồn, so `total` và tập `productId` của trang đầu. Trả `{ oracle, redis, match, mismatches }` như module query. Dùng để kiểm tra trường hợp từ khóa kèm bộ lọc (chưa đo trong E1–E6).

**6. `GET /api/admin/indexing/explain`**: Oracle `DBMS_XPLAN.DISPLAY_CURSOR` của câu vừa chạy (`DOMAIN INDEX`, `INDEX RANGE SCAN`, `TABLE ACCESS FULL`); Redis `FT.EXPLAIN` / `FT.PROFILE`.

**7. `GET /api/admin/indexing/index-info`**: dung lượng index. Oracle `USER_SEGMENTS` của `ix_products_*` và bảng `DR$...`; Redis `FT.INFO idx:products`.

## Phụ thuộc và việc với thành viên khác

- **TV4 (common/security):** dòng `permitAll` ở trên do TV2 thêm; cần TV4 xem và review trong PR.
- **TV1 (storage):** không sửa code storage. Thêm 2 index trên `PRODUCTS` làm đổi số footprint; TV1 sẽ tự đo lại khi demo. Báo TV1: `inStock` trong `product:*` không được cập nhật sau checkout; mỗi lần cập nhật projection, RediSearch index lại cả document (E4).
- **TV4 (transaction):** nối `AddToCartButton` / `addProductToCart` khi trang của module dùng `productId` thật.

## Rủi ro đã biết (ghi trong báo cáo)

- Kết quả tìm theo từ khóa có thể hiện tình trạng còn hàng cũ (projection); chi tiết và đặt hàng luôn đọc Oracle.
- Khi `idx:products` được tạo lại hoặc Redis khởi động lại, vài giây đầu tìm theo từ khóa chỉ thấy một phần sản phẩm (E6).
- `idx:products` dùng stemming tiếng Anh mặc định (`sony` → `soni`, E2). Chưa gây sai; với tên tiếng Việt có thể cân nhắc `NOSTEM` (sửa `init.lua`, file chung).
- Tìm không dấu ("dien thoai" ra "điện thoại"): chưa hỗ trợ ở cả hai nguồn.

## Cấu trúc code

```text
modules/indexing/
├── controller/   ProductController (/api/products), IndexingLabController (/api/admin/indexing)
├── service/      SearchQueryFactory (kiểm tra tham số), ProductSearchService (định tuyến, dự phòng, so sánh),
│                 ProductCatalogService (chi tiết, danh mục, dung lượng index)
├── repository/   OracleProductSearch, RedisProductSearch (interface, để test bằng bản giả)
│   ├── oracle/   OracleSearchSql (dựng SQL, bind), OracleProductSearchRepository, OracleCatalogRepository
│   └── redis/    RedisSearchCommand (dựng FT.SEARCH, đọc reply), RedisProductSearchRepository, IndexingRedis
├── model/        SearchQuery, Keywords, SortOrder, Route, SearchSource, OracleVariant, RedisVariant, SearchResult
└── dto/response/ SearchResponse (data + meta), ProductItem, ProductDetailResponse, CategoryOption, ...
```

Unit test không cần DB: `./mvnw test '-Dtest=**/indexing/**/*CoreTest'`.
