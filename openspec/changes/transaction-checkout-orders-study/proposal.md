# Proposal

## Why

ShopFlash đã có các bảng Oracle cho giỏ hàng, tồn kho và đơn hàng, nhưng luồng F10 → F7 → F8 chưa hoàn chỉnh. TV5 đã bàn giao F0 cho TV4 để các nghiệp vụ dùng danh tính JWT được backend xác thực. Change này hoàn thiện Cart, Checkout và Order, kiểm chứng giao dịch từ lúc sửa giỏ đến khi đặt và hủy đơn, rồi so sánh Oracle với Redis bằng thí nghiệm có thể chạy lại.

## What Changes

- **Bổ sung F0 làm điều kiện triển khai:** TV4 hoàn thiện đăng ký, đăng nhập, đọc tài khoản hiện tại và đăng xuất; backend kiểm tra mật khẩu bcrypt, phát hành/kiểm tra JWT và phân quyền CUSTOMER/ADMIN. Frontend dùng form thật, tự gắn JWT cho API và xử lý phiên hết hạn. F0/JWT đã thay thế cơ chế token thử nghiệm trong Cart.
- **Thay đổi phạm vi:** TV4 hoàn thiện F10 gồm xem giỏ, thêm hàng, đổi số lượng, xóa một mặt hàng và xóa toàn bộ mặt hàng ngay trên giao diện `/cart`. Giai đoạn thử Transaction dùng sản phẩm mẫu đã có trong Oracle để kiểm tra số lượng giỏ; không phụ thuộc trang danh sách/chi tiết sản phẩm của TV2. Giỏ tiếp tục lưu trong Oracle, không giữ hàng khi thêm vào giỏ.
- **Giữ và hoàn thiện F7:** xem trước, kiểm tra lại giỏ, giá, trạng thái sản phẩm và tồn kho khi xác nhận; lưu đơn hàng cùng thông tin tại thời điểm mua, giảm tồn kho và dọn giỏ trong một giao dịch Oracle. Xử lý yêu cầu gửi lại và trường hợp không rõ `COMMIT` đã thành công hay chưa.
- **Giữ và hoàn thiện F8:** khách hàng xem danh sách/chi tiết đơn của mình; hủy đơn `NORMAL` còn `PENDING` và hoàn tồn kho đúng một lần trong cùng giao dịch.
- **Bổ sung nghiên cứu:** kiểm tra `COMMIT`, `ROLLBACK`, lỗi sau khi ghi một phần, tính cô lập và tranh chấp trên Oracle; đối chiếu với `MULTI/EXEC`, `DISCARD`, `WATCH`, Lua trên Redis. Tách phép đo cơ chế cơ sở dữ liệu khỏi phép đo HTTP của ứng dụng; công bố dữ liệu thô và trạng thái sau mỗi thí nghiệm.
- Thêm migration Flyway mới cho phiên bản giỏ và mã băm nội dung yêu cầu đặt hàng; giữ nguyên `V1__schema.sql` và dữ liệu mẫu nền.

## Capabilities

### New Capabilities

- `account-authentication`: Đăng ký/đăng nhập F0, xác thực JWT và phân quyền backend để Cart/Checkout/Order cùng các API quản trị dùng danh tính Oracle đáng tin.
- `customer-cart`: Quản lý giỏ Oracle, kiểm tra dữ liệu và phát hiện thay đổi đồng thời với thao tác đặt hàng.
- `checkout-orders`: Đặt đơn hàng thông thường từ giỏ Oracle; kiểm tra dữ liệu, bảo đảm các thay đổi cùng thành công hoặc cùng được hoàn tác, và xử lý yêu cầu gửi lại.
- `customer-orders`: Xem lịch sử, chi tiết và hủy đơn hàng thuộc về khách hàng khi trạng thái cho phép.
- `transaction-study`: Thí nghiệm có thể chạy lại để so sánh ngữ nghĩa giao dịch, khả năng hoàn tác và hiệu năng của Oracle và Redis.

### Modified Capabilities

Không có. Các đặc tả nền về cấu trúc dữ liệu Oracle, Redis và dữ liệu mẫu vẫn giữ nguyên.

## Impact

- Phạm vi triển khai dự kiến: `backend/src/main/java/com/shopflash/common/security/`, `frontend/src/shared/auth/`, `frontend/src/shared/api/client.js`, `backend/src/main/java/com/shopflash/modules/transaction/`, `frontend/src/modules/transaction/`, migration Flyway mới và `report/transaction/`. F0 là phần dùng chung đã được TV5 đồng ý bàn giao cho TV4; không thay đổi nghiệp vụ của module khác.
- Thành phần dùng lại: bảng, ràng buộc và chỉ mục V1; `NamedParameterJdbcTemplate`, `@Transactional`, `ApiException`, `apiClient`, `PriceTag`. `OrderStatusTag` cần hiển thị đúng trạng thái Oracle. Redis chỉ phục vụ thí nghiệm riêng và các chức năng vốn có của module khác.
- Việc bàn giao F10 với TV5 đã được xác nhận: route `/cart` thuộc Transaction và không khai báo trùng. Điểm nối từ trang TV2 có thể dùng về sau khi có ID sản phẩm Oracle thật; không phải điều kiện để kiểm thử F10 → F7 → F8 trên `/cart`. Change này không cần sửa module TV2.
- JWT thay thế hoàn toàn token thử nghiệm được cấp bằng tệp CSV. Backend tra tài khoản Oracle còn hiệu lực cho mỗi yêu cầu được bảo vệ và không dùng `userId` do trình duyệt tự khai. Bài test/benchmark tạo khách fixture rồi đăng nhập để lấy JWT; secret ký token chỉ nằm trong biến môi trường, không commit hoặc ghi vào kết quả đo.
- Nghiệm thu chức năng theo từng mốc F0/F10, F7 và F8: kiểm thử tự động qua giao diện chạy trên host, Vite proxy, backend và Oracle thật; đối chiếu trạng thái dữ liệu bằng kết nối độc lập. Người yêu cầu thử luồng giao diện sau mỗi mốc lớn trước khi chuyển mốc. Rà soát hình thức, bố cục và màn 375px được gom vào cuối change; lỗi làm luồng không dùng được vẫn được sửa ngay.
- Ngoài phạm vi: F9 giữ suất và xử lý đơn Flash Sale; cổng thanh toán, vận chuyển, quản trị trạng thái đơn; giao dịch phân tán Oracle–Redis; tự động cập nhật mô hình báo cáo Redis của TV3; khôi phục mật khẩu qua email, đăng nhập mạng xã hội và refresh token. Đăng xuất xóa JWT ở trình duyệt, token đã cấp hết hiệu lực theo hạn tối đa 30 phút hoặc khi tài khoản bị vô hiệu hóa.
