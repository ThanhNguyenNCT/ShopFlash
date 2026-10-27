# Spec Delta

## Purpose

Cho phép khách hàng xem lại đơn hàng và hủy đơn hàng thông thường khi trạng thái còn cho phép, đồng thời bảo toàn thông tin lúc mua và số lượng tồn kho.

## ADDED Requirements

### Requirement: Lịch sử đơn hàng của từng khách hàng
Hệ thống SHALL trả danh sách đơn hàng của khách hàng đã được backend xác thực, có sắp xếp mới trước, phân trang và lọc theo trạng thái hợp lệ. Danh sách SHALL không chứa đơn hàng của người khác và không nhận `userId` tự khai từ trình duyệt.

#### Scenario: Xem danh sách
- **WHEN** khách hàng yêu cầu danh sách với `page`, `size` và trạng thái hợp lệ
- **THEN** API trả thông tin phân trang, tổng số đơn và các đơn của khách hàng đó, sắp theo thời điểm tạo mới nhất trước rồi đến `orderId` giảm dần

#### Scenario: Trang rỗng
- **WHEN** khách hàng chưa có đơn hoặc yêu cầu trang nằm sau trang cuối
- **THEN** API trả danh sách rỗng cùng thông tin phân trang hợp lệ

#### Scenario: Tham số không hợp lệ
- **WHEN** `page`, `size` hoặc trạng thái không hợp lệ
- **THEN** API báo lỗi dữ liệu đầu vào và không chạy truy vấn không giới hạn

#### Scenario: Kích thước trang quá lớn
- **WHEN** khách hàng yêu cầu số đơn mỗi trang vượt giới hạn công bố
- **THEN** API từ chối với lỗi dữ liệu đầu vào khi `size` lớn hơn 100, không trả toàn bộ lịch sử trong một lần

### Requirement: Chi tiết đơn hàng giữ nguyên thông tin lúc mua
Hệ thống SHALL trả thông tin người nhận, trạng thái, tổng tiền và tên, mã, giá, số lượng từng sản phẩm tại thời điểm đặt hàng. Việc sửa thông tin sản phẩm sau đó SHALL không làm thay đổi nội dung đơn đã lưu.

#### Scenario: Xem đơn của mình
- **WHEN** khách hàng xem đơn hàng thuộc về mình
- **THEN** API trả `orderId`, `orderCode`, loại đơn, trạng thái, thông tin nhận hàng và dữ liệu sản phẩm đã lưu khi đặt hàng

#### Scenario: Đơn không tồn tại hoặc không thuộc mình
- **WHEN** khách hàng yêu cầu đơn hàng không tồn tại hoặc thuộc về người khác
- **THEN** API trả cùng một lỗi không tìm thấy, không tiết lộ ai là chủ đơn hàng

### Requirement: Hủy đơn hàng thông thường khi còn chờ xử lý
Hệ thống SHALL chỉ cho chủ đơn hủy đơn `NORMAL` đang ở trạng thái `PENDING`. Việc chuyển sang `CANCELLED`, ghi thời điểm hủy và hoàn tồn kho SHALL cùng hoàn tất hoặc cùng bị hoàn tác trong một giao dịch Oracle.

#### Scenario: Hủy hợp lệ
- **WHEN** khách hàng hủy đơn `NORMAL` của mình đang `PENDING`
- **THEN** đơn chuyển thành `CANCELLED`, có `cancelledAt`, tồn kho tăng đúng số lượng sản phẩm đã mua và thông tin các mặt hàng trong đơn không đổi

#### Scenario: Hủy thất bại giữa chừng
- **WHEN** việc đổi trạng thái đơn hoặc hoàn tồn kho gặp lỗi trước khi giao dịch hoàn tất
- **THEN** đơn vẫn `PENDING` và tồn kho của mọi sản phẩm trong đơn giữ nguyên

#### Scenario: Hủy lặp hoặc sai trạng thái
- **WHEN** đơn đã ở trạng thái `CANCELLED`, `CONFIRMED` hoặc `COMPLETED`
- **THEN** API báo không thể hủy và không hoàn tồn kho lần nữa

#### Scenario: Hai yêu cầu hủy đồng thời
- **WHEN** hai yêu cầu hủy cùng đơn `PENDING` đến đồng thời
- **THEN** chỉ một yêu cầu chuyển đơn sang `CANCELLED` và tồn kho chỉ được hoàn một lần

#### Scenario: Hủy đơn Flash Sale
- **WHEN** khách hàng yêu cầu hủy đơn `FLASH_SALE` qua API này
- **THEN** API báo không thể hủy; việc hoàn suất Flash Sale thuộc phạm vi F9/TV5

#### Scenario: Hủy đơn người khác
- **WHEN** khách hàng yêu cầu hủy đơn thuộc về người khác
- **THEN** API báo không tìm thấy và không thay đổi đơn hay tồn kho

### Requirement: Giao diện hiển thị đúng trạng thái đơn hàng
Các trang `/orders` và `/orders/:orderId` SHALL hiển thị đúng bốn trạng thái `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, cùng trạng thái đang tải, lỗi và danh sách rỗng. Giao diện SHALL chỉ cho phép yêu cầu hủy khi đơn đáp ứng điều kiện đã biết.

#### Scenario: Hủy từ trang chi tiết
- **WHEN** khách hàng xác nhận hủy đơn hợp lệ trên giao diện
- **THEN** giao diện hiển thị kết quả mới nhất từ server và không cho hủy lại

#### Scenario: Trạng thái thay đổi giữa lúc mở trang và bấm hủy
- **WHEN** server từ chối vì trạng thái đơn đã thay đổi
- **THEN** giao diện báo lý do và tải lại chi tiết đơn, không hiển thị hủy thành công

#### Scenario: Xem và hủy đơn từ giao diện đến Oracle
- **WHEN** khách hàng đã đăng nhập mở lịch sử, xem đơn của mình rồi hủy một đơn `NORMAL/PENDING`
- **THEN** giao diện phản ánh trạng thái `CANCELLED`, còn `ORDERS` và `PRODUCT_INVENTORY` trên kết nối Oracle độc lập xác nhận tồn kho được hoàn đúng một lần
