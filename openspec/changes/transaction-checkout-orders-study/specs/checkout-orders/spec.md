# Spec Delta

## Purpose

Cho phép khách hàng đặt đơn hàng thông thường từ giỏ Oracle, kiểm tra lại giá và tồn kho, đồng thời gửi lại yêu cầu an toàn khi mất kết nối hoặc chưa rõ kết quả giao dịch.

## ADDED Requirements

### Requirement: Đặt hàng bằng danh tính đã xác thực
Hệ thống SHALL xác định khách hàng từ JWT đã được backend xác thực khi xem trước hoặc đặt hàng. Hệ thống SHALL từ chối yêu cầu chưa xác thực và không dùng `userId` do phía trình duyệt tự gửi.

#### Scenario: Thiếu JWT
- **WHEN** người dùng chưa có JWT hợp lệ yêu cầu xem trước hoặc đặt hàng
- **THEN** API trả lỗi xác thực và không đọc hay thay đổi giỏ hàng

#### Scenario: Khách hàng đã đăng nhập
- **WHEN** khách hàng có JWT hợp lệ yêu cầu đặt hàng
- **THEN** hệ thống chỉ sử dụng giỏ hàng thuộc về khách hàng đó

### Requirement: Xem thông tin trước khi đặt hàng
Hệ thống SHALL hiển thị các mặt hàng trong giỏ, giá hiện tại từ Oracle, lượng hàng có thể bán theo giá thông thường và tổng tiền. Bước xem trước SHALL không giữ hàng hoặc thay đổi giỏ.

#### Scenario: Xem trước giỏ có hàng
- **WHEN** giỏ có sản phẩm đang bán và còn hàng
- **THEN** API trả `cartVersion`, `productId`, `quantity`, `unitPrice`, thành tiền của từng mặt hàng và tổng tiền tính từ Oracle

#### Scenario: Giỏ trống
- **WHEN** giỏ không có dòng hàng
- **THEN** API báo giỏ trống và giao diện không cho xác nhận đặt hàng

#### Scenario: Khách hàng chưa có giỏ
- **WHEN** khách hàng chưa từng thêm sản phẩm gọi API xem trước
- **THEN** API trả trạng thái giỏ trống mà không tạo đơn hoặc thay đổi tồn kho

#### Scenario: Giá hoặc trạng thái thay đổi sau lần xem trước
- **WHEN** giá, trạng thái hoặc tồn kho đổi trước khi xác nhận
- **THEN** hệ thống kiểm tra lại khi khách hàng xác nhận và báo lý do nếu không thể đặt hàng theo thông tin đã xem

### Requirement: Tạo đơn hàng thông thường trong một giao dịch
Hệ thống SHALL đối chiếu phiên bản giỏ, các mặt hàng và giá với thông tin khách hàng đã xác nhận. Nếu hợp lệ, hệ thống SHALL tạo một đơn `NORMAL`, lưu thông tin sản phẩm tại thời điểm mua, giảm tồn kho và xóa mặt hàng đã mua khỏi giỏ trong cùng một giao dịch Oracle.

#### Scenario: Chỉ đặt các mặt hàng được chọn
- **WHEN** giỏ có nhiều mặt hàng nhưng khách hàng chỉ chọn một tập con không rỗng để đặt đơn
- **THEN** `expectedLines`, `ORDER_ITEMS`, tổng tiền và lượng tồn kho giảm chỉ gồm tập đã chọn; các mặt hàng không chọn vẫn ở `CART_ITEMS` với số lượng không đổi; phiên bản giỏ tăng một lần

#### Scenario: Mặt hàng không chọn không còn bán
- **WHEN** một mặt hàng không được chọn đã hết hàng hoặc ngừng bán, còn mọi mặt hàng được chọn đều hợp lệ
- **THEN** đơn của các mặt hàng đã chọn vẫn có thể được tạo; cảnh báo của mặt hàng không chọn không chặn xác nhận

#### Scenario: Chưa chọn mặt hàng
- **WHEN** khách hàng mở `/checkout` khi giỏ có hàng nhưng chưa chọn mặt hàng nào
- **THEN** giao diện không tạo yêu cầu đặt hàng và hướng dẫn quay lại giỏ để chọn

#### Scenario: Đặt đơn thành công
- **WHEN** khách hàng xác nhận giỏ không rỗng, giá và số lượng khớp, sản phẩm còn bán và đủ `on_hand_quantity - reserved_flash_quantity`
- **THEN** đúng một đơn `PENDING` với mã đơn duy nhất được lưu cùng thông tin từng mặt hàng tại thời điểm mua; tồn kho giảm đúng số lượng, các mặt hàng đã mua được xóa khỏi giỏ và tổng tiền bằng tổng các dòng hàng

#### Scenario: Một dòng hết hàng hoặc ngừng bán
- **WHEN** ít nhất một dòng không còn hợp lệ tại thời điểm xác nhận
- **THEN** API báo lỗi nghiệp vụ; không tạo đơn hàng, không thay đổi tồn kho hoặc giỏ hàng

#### Scenario: Giỏ hoặc giá đổi sau xem trước
- **WHEN** phiên bản giỏ, danh sách `productId`, `quantity` hoặc giá trong Oracle khác thông tin khách hàng xác nhận
- **THEN** API báo dữ liệu đã thay đổi và yêu cầu xem lại giỏ; không tạo đơn hàng

#### Scenario: Tồn kho được cập nhật đồng thời
- **WHEN** thao tác cập nhật tồn kho khác diễn ra trong lúc khách hàng xác nhận đặt hàng
- **THEN** kết quả đặt hàng dùng lượng tồn kho đã được khóa và kiểm tra lại; đơn chỉ được tạo nếu vẫn đủ `on_hand_quantity - reserved_flash_quantity`

#### Scenario: Lỗi sau một phần thao tác ghi
- **WHEN** một bước tạo đơn, ghi mặt hàng, giảm tồn kho hoặc xóa giỏ thất bại trước khi giao dịch hoàn tất
- **THEN** Oracle hoàn tác toàn bộ thay đổi của yêu cầu; giỏ và tồn kho giữ nguyên trạng thái trước khi đặt hàng

#### Scenario: Checkout từ giao diện đến Oracle
- **WHEN** khách hàng đã đăng nhập xác nhận đơn từ `/checkout`
- **THEN** giao diện nhận đúng đơn từ API và trạng thái `ORDERS`, `ORDER_ITEMS`, `CART_ITEMS`, `PRODUCT_INVENTORY` sau `COMMIT` khớp phản hồi trên kết nối Oracle độc lập

### Requirement: Nhận diện yêu cầu đặt hàng gửi lại
Mỗi yêu cầu tạo đơn SHALL có `Idempotency-Key` và nội dung xác nhận cố định. Cùng khách hàng, cùng khóa và cùng nội dung SHALL nhận lại đơn hàng đã tạo; cùng khóa nhưng khác nội dung SHALL bị từ chối.

#### Scenario: Gửi lại sau khi mất phản hồi
- **WHEN** khách hàng gửi lại cùng khóa và cùng nội dung vì chưa nhận được phản hồi của lần đặt hàng trước
- **THEN** nếu đơn đã được lưu, API trả lại cùng `orderId` và `orderCode` mà không giảm tồn kho hoặc xóa giỏ thêm lần nữa

#### Scenario: Tải lại trang khi kết quả đặt hàng chưa rõ
- **WHEN** giao diện đã gửi yêu cầu đặt hàng rồi bị hết thời gian chờ, mất phản hồi hoặc tải lại trong cùng tab
- **THEN** giao diện khôi phục `Idempotency-Key` và body đã lưu trước khi gửi, cho phép gửi lại đúng yêu cầu cũ, không tự tạo khóa hay nội dung mới cho đến khi xác định kết quả

#### Scenario: Giải quyết yêu cầu đang chờ
- **WHEN** lần gửi lại trả đơn đã lưu hoặc lỗi nghiệp vụ xác định yêu cầu không được commit
- **THEN** giao diện xóa bản ghi đang chờ; chỉ sau đó khách mới có thể xác nhận nội dung mới với khóa mới

#### Scenario: Dùng lại khóa với nội dung khác
- **WHEN** khóa đã dùng được gửi bởi người khác hoặc đi kèm thông tin nhận hàng hay danh sách hàng khác
- **THEN** API trả lỗi xung đột và không tiết lộ đơn hàng của người khác

#### Scenario: Hai request đồng thời cùng khóa
- **WHEN** hai yêu cầu dùng cùng một khóa được gửi đồng thời
- **THEN** nhiều nhất một đơn hàng được tạo; yêu cầu còn lại nhận đơn đó hoặc một lỗi cho phép gửi lại an toàn, không để lại dữ liệu ghi dở

#### Scenario: Không rõ giao dịch đã hoàn tất hay chưa
- **WHEN** kết nối mất khi server đang hoàn tất giao dịch
- **THEN** gửi lại cùng khóa và cùng nội dung sẽ trả đơn đã lưu hoặc thực hiện lại nếu giao dịch trước đã bị hoàn tác, không tạo hai đơn

#### Scenario: Va chạm mã đơn hoặc khóa đã thuộc người khác
- **WHEN** mã đơn mới trùng một mã đã có hoặc `Idempotency-Key` thuộc yêu cầu khác
- **THEN** hệ thống không trả nhầm đơn; mọi thay đổi của yêu cầu lỗi bị hoàn tác và client nhận kết quả hoặc lỗi có thể xử lý an toàn

### Requirement: Đặt hàng trong khi giỏ có thể thay đổi
Luồng đặt hàng SHALL đọc giỏ Oracle do F10 của module Transaction quản lý và phối hợp với các thao tác sửa giỏ để không dùng dữ liệu bị thay đổi giữa chừng.

#### Scenario: Hai tab sửa giỏ và checkout
- **WHEN** một tab sửa giỏ trong lúc tab khác xác nhận đặt hàng cho cùng khách hàng
- **THEN** đơn hàng khớp với giỏ mà khách hàng đã xác nhận hoặc yêu cầu đặt hàng bị từ chối vì dữ liệu thay đổi; thao tác sửa giỏ không bị mất

#### Scenario: Hai lần checkout khác khóa cùng giỏ
- **WHEN** hai yêu cầu dùng hai khóa khác nhau để đặt hàng từ cùng một giỏ
- **THEN** nhiều nhất một yêu cầu mua được những mặt hàng đó; yêu cầu còn lại được báo giỏ trống hoặc dữ liệu đã thay đổi

#### Scenario: Hết thời gian chờ khóa hoặc deadlock
- **WHEN** giao dịch không lấy được khóa trong thời gian cho phép hoặc Oracle chọn giao dịch này làm bên phải hoàn tác do deadlock
- **THEN** API trả lỗi cho phép gửi lại cùng khóa và nội dung; không để lại đơn, giỏ hoặc tồn kho bị ghi dở
