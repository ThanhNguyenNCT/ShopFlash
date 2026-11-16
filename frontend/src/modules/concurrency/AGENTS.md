# Module concurrency (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/concurrency/AGENTS.md`.

## Màn hình

| Path | File | Nội dung |
|---|---|---|
| `/flash-sale` | `pages/FlashSalePage.jsx` | Chương trình đang chạy và sắp mở trong 7 ngày: đồng hồ đếm ngược, số suất còn lại (hỏi lại `/stock` mỗi 2 s), nút "Mua ngay" giữ suất. Chưa đăng nhập thì chuyển tới `/login?next=/flash-sale` |
| `/flash-sale/checkout/:reservationId` | `pages/FlashCheckoutPage.jsx` | Đếm ngược tới `expiresAt`, nhập thông tin nhận hàng, đặt đơn flash sale; hủy giữ suất |
| `/admin/flash-sales` | `pages/AdminFlashSalesPage.jsx` | Danh sách chương trình; ngăn chi tiết (`components/SaleDetailDrawer.jsx`) có thao tác theo trạng thái và đối chiếu Oracle với Redis |
| `/admin/flash-sales/lab` | `pages/ConcurrencyLabPage.jsx` | Concurrency Lab, menu nhóm "Flash sale", `order: 2` |

Route `/cart` thuộc module `transaction` (TV4).

## File

- `api.js`: mọi lời gọi API của module (khách, admin, Lab). `reserveItem` và `checkoutReservation` trả thêm `created` (201 hay 200).
- `format.js`: đồng hồ theo giờ server (`offsetFrom`, `nextOffset`, `toClientTime`, `useServerNow`), hiển thị ngày giờ, nhãn trạng thái chương trình (`SALE_STATUS`), rule giới hạn byte UTF-8 cho form (`maxBytesRule`).
- `components/SaleFormModal.jsx` (tạo, sửa), `SaleItemsModal.jsx` (chọn sản phẩm, giá sale, số suất), `RuntimePanel.jsx` (đối chiếu, dựng lại runtime), `SaleDetailDrawer.jsx`.
- `concurrency.css`: class tiền tố `sf-cc-`, chỉ dùng biến `--sf-*`.

## Cách làm đã chốt

- **Đếm ngược theo giờ server.** Mỗi response có `serverTime`; `offset = serverTime - Date.now()` lúc nhận. `FlashCountdown` (design system) đếm theo `Date.now()`, nên truyền `endsAt = mốc server - offset`. Offset chỉ đổi khi lệch hơn 0,5 s để đồng hồ không khởi động lại sau mỗi lần hỏi `/stock`.
- **Pha của chương trình tính lại mỗi giây** theo giờ server (`UPCOMING` → `LIVE` → hết giờ) để nút "Mua ngay" mở và tắt đúng lúc mà không phải tải lại trang. Hết đếm ngược thì tải lại danh sách sau 1,5 s.
- **Hỏi `/stock` mỗi 2 s** chỉ cho chương trình đang `LIVE`; lượt sau bắt đầu khi lượt trước xong; tab ẩn thì bỏ qua. `/stock` trả 404 (runtime đã gỡ) thì tải lại danh sách, tối đa mỗi 10 s.
- **Giữ suất và đặt đơn an toàn khi gửi lại.** Mất phản hồi khi giữ suất: bấm lại trả đúng suất đang giữ (200). Mất phản hồi khi đặt đơn (mạng, hết giờ chờ, 5xx): trang báo "chưa rõ" và cho bấm "Đặt đơn" lại với cùng `reservationId`; backend trả lại đơn đã tạo, không tạo đơn trùng.
- **Form nhận hàng** kiểm tra độ dài theo byte UTF-8 sau NFC và trim (150/30/500), giống backend.
- **Admin:** mọi thao tác đổi trạng thái và xóa đều hỏi lại (`Popconfirm`) kèm hệ quả. `FLASH_SALE_CHANGED` thì báo và tải lại chi tiết. `INSUFFICIENT_STOCK` ghi tên sản phẩm thiếu hàng. Chương trình đã lên lịch chỉ mở ô tên và mô tả; giờ và giới hạn gửi lại đúng giá trị cũ.
- **Lab:** chạy đồng bộ (`timeout: 0`); trong lúc chạy hỏi `/state` mỗi giây để thấy bộ đếm thay đổi. Dòng bán vượt hoặc lost update có viền trái màu `danger` kèm Tag chữ; dòng bán chưa hết M dù còn người mua có Tag "Bán thiếu". Nút "Quét theo C" chạy lại cùng thông số với C = 1, 8, 32, 64, 128 (gọi API `runs` 5 lần liên tiếp) rồi vẽ throughput và p95 theo C (`components/LabSweepCharts.jsx`, `recharts`, trục dọc thang log). Chuỗi phân biệt bằng màu của design system và kiểu nét (Oracle liền, Redis đứt); số liệu đầy đủ ở bảng dưới biểu đồ.
- **Lưới thẻ flash sale** dùng `align-items: start`: `ProductCard` là grid có ô ảnh `aspect-ratio: 1/1`, bị kéo cao theo thẻ cao nhất trong hàng thì ô ảnh rộng theo và thẻ tràn khỏi cột. Lỗi gốc nằm ở `ProductCard.css` của design system (nên thêm `align-content: start` cho `.sf-card`).

## Đã kiểm tra (2026-11-15)

Chrome headless qua DevTools Protocol, backend thật (profile `dev`): khách chưa đăng nhập bấm mua, giữ suất, đếm ngược, form trống, đặt đơn, mở đơn ở trang Đơn hàng của `transaction`, mua lần 2 (vượt giới hạn), hủy giữ suất, suất đã hủy, mã suất không có; admin tạo, chọn sản phẩm (có dòng lỗi), lên lịch, đối chiếu runtime, hủy lịch, xóa, sửa tên chương trình đang chạy; Lab 10 chiến lược. Màn 375 px không cuộn ngang ở cả 4 trang. Không có lỗi console của module.
