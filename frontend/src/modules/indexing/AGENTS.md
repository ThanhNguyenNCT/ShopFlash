# Module indexing (frontend)

Nghiệp vụ, dữ liệu, API và lý do lựa chọn: `backend/src/main/java/com/shopflash/modules/indexing/AGENTS.md` và `report/indexing/ket-luan.md`.

## Màn hình

| Route | File | Nội dung |
|---|---|---|
| `/` | `pages/HomePage.jsx` | Banner, danh mục (bấm sang `/search?categoryId=`), 8 sản phẩm còn hàng giá thấp nhất, lối vào Flash sale. Dải flash sale có dữ liệu chờ API của module concurrency (TV5) |
| `/search` | `pages/SearchPage.jsx` | F3/F4. Mọi điều kiện nằm trên URL (`?q=&categoryId=&minPrice=&maxPrice=&sort=&page=`); ô tìm kiếm ở header gửi sẵn `?q=`. Cột lọc (dính khi cuộn trên desktop): danh mục dạng danh sách bấm chọn có số lượng, khoảng giá có mốc chọn nhanh và ô nhập (Enter để áp dụng, kiểm tra Từ ≤ Đến). Thanh trên lưới: tag điều kiện đang lọc (bấm × để bỏ) và sắp xếp. Đã bỏ bộ lọc "còn hàng" khỏi giao diện (sản phẩm hết hàng có nhãn trên ảnh); phân trang 24 sản phẩm, tối đa 10.000 kết quả. Nguồn (RediSearch/Oracle), thời gian gọi DB, số lượt gọi và câu lệnh không hiện cho khách hàng mà in ra console trình duyệt (F12 > Console, nhóm `[ShopFlash search]`, do `logSearchMeta` trong `api.js`) để demo |
| `/products/:productId` | `pages/ProductDetailPage.jsx` | Chi tiết đọc Oracle; `AddToCartButton` của module transaction khi còn hàng; 404 khi không có hoặc ngừng bán |
| `/admin/products/indexing-lab` | `pages/IndexingLabPage.jsx` | Admin, menu Catalog. 5 thẻ: định tuyến, so sánh hai nguồn, có/không index, kế hoạch thực thi, dung lượng index |

## Thành phần

- `components/ProductGrid.jsx`: lưới `ProductCard` có Skeleton, Empty, lỗi + Thử lại. "Thêm vào giỏ" dùng `addProductToCart(user, productId)` của module transaction, chỉ với sản phẩm còn hàng; chưa đăng nhập thì sang `/login?next=`.
- `components/lab/`: `LabQueryForm` (điều kiện dùng chung, có nút mẫu E2/E3), `RunResult` (một lần chạy).
- `components/indexing.css`: khối `pre` cho câu lệnh và kế hoạch, ô ảnh trang chi tiết, bộ lọc trang tìm kiếm, và phần chỉnh `ProductCard` trong lưới (`.sf-idx-grid`: viền và bóng rõ cả khi chưa hover, đường kẻ ngăn ảnh và thông tin, ô ảnh nền trắng, nút "Thêm vào giỏ" viền brand và hover nền brand) để card không chìm vào nền trang; chỉ dùng biến `--sf-*`, không sửa file của design system.

## Quy ước

- Gọi API qua `api.js` (`apiClient`); lỗi đã được chuẩn hóa `{ status, code, message, details }`.
- Trang Lab chạy các biến thể **lần lượt**, không song song: Redis xử lý từng lệnh một, chạy cùng lúc làm các lần đo chờ nhau.
- Thời gian hiện trên giao diện đo từ backend (gồm driver, mạng tới Docker), chỉ để minh họa. Số liệu so sánh chính thức đo trong DB: `report/indexing/ket-qua.md`.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `ProductCard` (lưới sản phẩm), `PriceTag` (trang chi tiết), `formatVND`.
