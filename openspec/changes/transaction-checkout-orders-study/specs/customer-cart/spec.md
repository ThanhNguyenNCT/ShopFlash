# Spec Delta

## Purpose

Cho phép khách hàng quản lý giỏ hàng lưu trong Oracle trước khi đặt hàng, đồng thời phát hiện thay đổi từ nhiều tab hoặc từ luồng xác nhận đơn hàng.

## ADDED Requirements

### Requirement: Giỏ hàng thuộc về khách hàng đã được xác thực
Hệ thống SHALL chỉ đọc và thay đổi giỏ của khách hàng có danh tính được backend xác thực; SHALL không lấy `userId` do trình duyệt tự khai làm căn cứ phân quyền.

#### Scenario: Chưa có danh tính hợp lệ
- **WHEN** yêu cầu xem hoặc sửa giỏ không có danh tính hợp lệ
- **THEN** API từ chối và không đọc hoặc thay đổi giỏ hàng

#### Scenario: Hai khách hàng khác nhau
- **WHEN** hai khách hàng lần lượt dùng các API giỏ hàng
- **THEN** mỗi người chỉ thấy và sửa giỏ của mình

#### Scenario: Danh tính giao diện được khôi phục từ JWT
- **WHEN** giao diện tải lại với JWT hợp lệ của khách hàng
- **THEN** giao diện lấy tài khoản từ `/api/auth/me` trước khi cho sửa giỏ; backend lấy chủ giỏ từ tài khoản Oracle tương ứng với JWT, không dùng `userId` trong request

### Requirement: Xem giỏ kể cả khi chưa có bản ghi
API `GET /api/cart` SHALL trả các mặt hàng, số lượng, giá tham khảo hiện tại, tổng tiền tham khảo và phiên bản giỏ. Khi khách hàng chưa có giỏ, API SHALL trả giỏ rỗng mà không phải tạo bản ghi Oracle.

#### Scenario: Giỏ chưa được tạo
- **WHEN** khách hàng mới gọi `GET /api/cart`
- **THEN** API trả danh sách rỗng, tổng tiền bằng 0 và phiên bản 0

#### Scenario: Giỏ có mặt hàng
- **WHEN** giỏ đã chứa sản phẩm
- **THEN** API trả một dòng cho mỗi sản phẩm với số lượng, giá và tổng tiền tham khảo tính từ Oracle

#### Scenario: Giỏ đổi trong lúc đọc
- **WHEN** một yêu cầu sửa giỏ hoàn tất giữa các lần đọc dữ liệu cho `GET /api/cart`
- **THEN** phản hồi không ghép phiên bản của giỏ cũ với danh sách mặt hàng của giỏ mới

### Requirement: Thêm sản phẩm và tự tạo giỏ
API `POST /api/cart/items` SHALL tạo giỏ khi cần, kiểm tra sản phẩm và số lượng, rồi thêm mặt hàng hoặc cộng số lượng vào dòng đã có. Mỗi cặp giỏ và sản phẩm SHALL chỉ có một dòng.

#### Scenario: Lần đầu thêm hàng
- **WHEN** khách hàng chưa có giỏ thêm sản phẩm hợp lệ với số lượng dương trong giới hạn cho phép
- **THEN** Oracle lưu một giỏ của khách hàng và một dòng sản phẩm với số lượng yêu cầu

#### Scenario: Thêm lại sản phẩm đã có
- **WHEN** khách hàng thêm tiếp một sản phẩm đang có trong giỏ
- **THEN** số lượng của dòng hiện có tăng đúng lượng yêu cầu, không xuất hiện dòng trùng

#### Scenario: Sản phẩm hoặc số lượng không hợp lệ
- **WHEN** `productId` không tồn tại, sản phẩm không còn bán, số lượng không dương hoặc tổng số lượng vượt giới hạn công bố
- **THEN** API trả lỗi rõ lý do và giỏ giữ nguyên

#### Scenario: Hai yêu cầu cùng tạo giỏ
- **WHEN** hai yêu cầu thêm hàng đầu tiên của cùng khách hàng diễn ra đồng thời
- **THEN** tối đa một dòng `CARTS` được lưu; yêu cầu không thể hoàn tất nhận lỗi có thể tải lại và gửi lại an toàn

### Requirement: Cập nhật và xóa mặt hàng
API `PATCH /api/cart/items/{productId}` SHALL đặt số lượng tuyệt đối cho mặt hàng đã có. API `DELETE /api/cart/items/{productId}` SHALL xóa đúng mặt hàng được chỉ định; mọi thay đổi SHALL thuộc một giao dịch Oracle.

#### Scenario: Đổi số lượng
- **WHEN** khách hàng gửi số lượng mới hợp lệ cho mặt hàng trong giỏ
- **THEN** chỉ số lượng của mặt hàng đó đổi và API trả phiên bản giỏ mới

#### Scenario: Xóa một mặt hàng
- **WHEN** khách hàng xóa sản phẩm có trong giỏ
- **THEN** sản phẩm đó biến mất khỏi giỏ, các mặt hàng khác giữ nguyên

#### Scenario: Mặt hàng không có trong giỏ
- **WHEN** khách hàng sửa hoặc xóa `productId` chưa có trong giỏ
- **THEN** API báo không tìm thấy mặt hàng và không thay đổi giỏ

### Requirement: Xóa toàn bộ mặt hàng trong giỏ
API `DELETE /api/cart/items` SHALL xóa mọi mặt hàng của khách hàng trong một giao dịch, giữ lại bản ghi `CARTS` để tiếp tục theo dõi phiên bản giỏ.

#### Scenario: Xóa giỏ có nhiều mặt hàng
- **WHEN** khách hàng yêu cầu xóa toàn bộ giỏ
- **THEN** không còn dòng `CART_ITEMS` nào của giỏ đó, phiên bản tăng một lần và giỏ của người khác giữ nguyên

#### Scenario: Giỏ đã rỗng
- **WHEN** khách hàng yêu cầu xóa một giỏ không có mặt hàng
- **THEN** API trả giỏ rỗng, không tạo lại mặt hàng hoặc đổi phiên bản vô ích

### Requirement: Phát hiện cập nhật giỏ từ dữ liệu cũ
Mọi API sửa giỏ SHALL nhận phiên bản mà khách hàng đã đọc; nếu phiên bản đó khác phiên bản Oracle hiện tại, API SHALL trả xung đột và không ghi đè thay đổi mới hơn. Việc xác nhận đặt hàng SHALL tuân theo cùng quy tắc khóa giỏ.

#### Scenario: Hai tab cùng sửa một giỏ
- **WHEN** hai tab gửi thao tác sửa dựa trên cùng phiên bản giỏ
- **THEN** tối đa một thao tác thành công; tab còn lại được báo tải lại giỏ, không làm mất cập nhật đã lưu

#### Scenario: Sửa giỏ trong lúc đặt hàng
- **WHEN** thao tác sửa giỏ và xác nhận đặt hàng diễn ra đồng thời
- **THEN** đơn hàng phản ánh giỏ đã được khách hàng xác nhận hoặc việc đặt hàng bị từ chối vì giỏ đã đổi; không có mặt hàng bị xóa nhầm

### Requirement: Thêm vào giỏ không giữ hàng
Giỏ hàng SHALL không trừ hoặc giữ tồn kho và không lưu giá bán cố định. Giá, trạng thái sản phẩm và tồn kho SHALL được kiểm tra lại khi khách hàng xác nhận đặt hàng.

#### Scenario: Giá hoặc tồn kho đổi sau khi thêm hàng
- **WHEN** giá, trạng thái hoặc tồn kho thay đổi sau lúc thêm sản phẩm vào giỏ
- **THEN** giỏ có thể hiển thị thông tin mới để khách hàng xem lại; việc đặt hàng dùng dữ liệu Oracle tại thời điểm xác nhận

#### Scenario: Thêm hàng không giảm tồn kho
- **WHEN** khách hàng thêm sản phẩm vào giỏ hoặc đổi số lượng
- **THEN** `PRODUCT_INVENTORY` và suất Flash Sale không thay đổi

### Requirement: Giao diện giỏ hàng phản ánh kết quả API
Trang `/cart` SHALL cho phép nhập ID sản phẩm Oracle và số lượng để thêm hàng, xem giỏ, sửa số lượng, xóa một hoặc toàn bộ mặt hàng và đi đến trang đặt hàng. Giao diện SHALL xử lý trạng thái đang tải, rỗng, lỗi và xung đột phiên bản. Dữ liệu mẫu dùng để thử F10 SHALL là dữ liệu Oracle và đi qua Cart API thật, không phải một giỏ chỉ có trong trình duyệt.

#### Scenario: Đổi số lượng bằng mũi tên
- **WHEN** khách hàng tăng hoặc giảm số lượng hợp lệ trên trang giỏ
- **THEN** thành tiền của dòng, tổng giỏ và tổng mặt hàng đã chọn thay đổi ngay; giao diện tự gửi `PATCH` với phiên bản giỏ mới nhất, không yêu cầu bấm nút xác nhận; các thay đổi liên tiếp được lưu tuần tự và không cho đặt hàng trước khi lưu xong

#### Scenario: Chọn mặt hàng để đặt đơn
- **WHEN** khách hàng mở giỏ lần đầu hoặc tải lại trang rồi chọn một số mặt hàng
- **THEN** mặc định không có mặt hàng nào được chọn; lựa chọn của cùng khách hàng được giữ trong tab sau khi tải lại, tổng đã chọn chỉ gồm các mặt hàng đó và nút đặt hàng chỉ hoạt động khi đã chọn ít nhất một mặt hàng

#### Scenario: Thử thêm sản phẩm mẫu trên trang giỏ
- **WHEN** khách hàng nhập ID sản phẩm đang bán trong Oracle và số lượng hợp lệ trên `/cart`
- **THEN** giao diện gọi `POST /api/cart/items` với phiên bản giỏ hiện tại và hiển thị số lượng từ phản hồi đã lưu; tồn kho Oracle không thay đổi

#### Scenario: Thao tác thành công
- **WHEN** khách hàng thêm, sửa hoặc xóa hàng thành công
- **THEN** giao diện hiển thị giỏ và tổng tiền tham khảo theo phản hồi mới nhất của API

#### Scenario: Dữ liệu giỏ đã đổi ở tab khác
- **WHEN** API trả lỗi xung đột phiên bản
- **THEN** giao diện tải lại giỏ và thông báo để khách hàng xem trước khi thao tác tiếp

#### Scenario: Luồng giỏ hàng từ giao diện đến Oracle
- **WHEN** khách hàng đăng nhập và lần lượt thêm, sửa số lượng, xóa mặt hàng trên `/cart`
- **THEN** các yêu cầu đi qua proxy `/api` bằng JWT, phản hồi giao diện khớp `CART_ITEMS` trên kết nối Oracle độc lập sau mỗi bước và tồn kho không đổi
