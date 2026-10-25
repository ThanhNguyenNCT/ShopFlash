# Kiểm tra thủ công F10 trên màn 375px

Điều kiện: Oracle có schema Flyway V2, Redis chạy Docker; backend chạy profile `dev,tx-local` trên host, Vite chạy trên host. Token của khách `customer@shopflash.local` nằm trong tệp ngoài repo theo [README Transaction](../../../../backend/src/main/java/com/shopflash/modules/transaction/README.md). Schema `SHOP` local thiếu `flyway_schema_history` phải được xử lý đúng trước khi dùng; không tự xóa hoặc baseline dữ liệu. Bài kiểm tra chỉ dùng dữ liệu thử nghiệm riêng.

1. Mở DevTools, đặt viewport **375 × 812**. Chọn tài khoản Khách trong `/login`, vào `/cart`. Không có cuộn ngang; tiêu đề, giá, ô số lượng và các nút không bị cắt.
2. Khi chưa có token, trang yêu cầu nhập token và không gọi `/api/cart`. Dán token hợp lệ vào ô mật khẩu, bấm **Dùng token**. Network phải gọi `/api/transaction/local-identity` trước `/api/cart`; token chỉ nằm trong request header, không ở URL.
3. Chọn tài khoản giả lập khác hoặc dùng token ánh xạ email khác. Trang báo không khớp danh tính; Network không có yêu cầu `/api/cart/items` sau khi kiểm tra local identity thất bại. Token không được in trên màn hình hoặc console.
4. Với giỏ có một mặt hàng, sửa số lượng trong khoảng 1–999 rồi bấm **Cập nhật**. Tổng và số lượng khớp phản hồi API; `version` tăng một lần. Nhập 0 hoặc 1000 không gửi lệnh thành công. Tải lại trang vẫn thấy dữ liệu Oracle đã lưu.
5. Bấm **Xóa** và **Xóa toàn bộ**: cả hai có xác nhận. Sau khi xác nhận, giỏ rỗng có lối **Xem sản phẩm**; không xác nhận thì giỏ giữ nguyên. Bấm **Thanh toán** từ giỏ có hàng dẫn tới `/checkout` (trang Checkout được triển khai ở milestone 3).
6. Mở hai tab cùng khách/token. Tab A sửa giỏ; tab B dùng phiên bản cũ để sửa. Tab B nhận `409 CART_CHANGED`, tự tải lại và báo người dùng xem giỏ trước khi tiếp tục; không làm mất thay đổi của tab A.
7. Trong DevTools kiểm tra token được giữ ở `sessionStorage` của tab và không có trong `localStorage`. Sau khi bấm **Bỏ token**, giỏ không còn hiển thị. Gọi API trực tiếp khác origin từ trang Vite không thuộc luồng hỗ trợ; các lời gọi của trang dùng proxy `/api`.

Nút `AddToCartButton` và hàm `addProductToCart` của TV4 đã sẵn sàng. Trang Listing/Detail của TV2 hiện dùng ID minh họa, nên bước thêm từ sản phẩm thật sẽ kiểm tra sau khi TV2 cung cấp dữ liệu và API sản phẩm công khai. Khi thử lệnh cộng mà mất phản hồi, tải lại `/cart` trước khi bấm lại để tránh cộng đôi.
