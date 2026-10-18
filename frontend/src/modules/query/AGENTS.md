# Module query (frontend)

Nghiệp vụ, dữ liệu và API của module: xem `backend/src/main/java/com/shopflash/modules/query/AGENTS.md`.

## Màn hình

- Admin: Tổng quan `/admin` (chỉ số 12 tháng, top 5 sản phẩm, thẻ lối tắt tới mọi khu quản trị lấy từ metadata menu), báo cáo `/admin/reports` (biểu đồ doanh thu, bảng top sản phẩm, bảng khách VIP), so sánh Oracle–Redis `/admin/reports/lab`.

## Giao diện

Dùng design system ở `src/shared/design-system` (không viết mã màu cố định): `PriceTag size="sm"` (cột doanh thu), `OrderStatusTag`. Biểu đồ lấy màu từ `tokens.color`.
