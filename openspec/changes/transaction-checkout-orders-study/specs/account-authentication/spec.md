# Spec Delta

## Purpose

Cho phép khách hàng đăng ký, đăng nhập và dùng các nghiệp vụ ShopFlash bằng danh tính do backend xác thực, đồng thời giới hạn API quản trị theo vai trò từ Oracle.

## ADDED Requirements

### Requirement: Đăng ký tài khoản khách hàng
API đăng ký SHALL kiểm tra tên (1–150 ký tự), email (tối đa 255 ký tự) và mật khẩu (ít nhất 10 ký tự, tối đa 72 byte UTF-8); SHALL chuẩn hóa email để kiểm tra trùng; SHALL tạo tài khoản `CUSTOMER/ACTIVE` và không cho người gửi tự chọn vai trò hoặc ID. Mật khẩu SHALL được lưu dưới dạng băm một chiều, không xuất hiện trong phản hồi.

#### Scenario: Đăng ký hợp lệ
- **WHEN** người dùng gửi thông tin đăng ký hợp lệ với email chưa có trong Oracle
- **THEN** hệ thống tạo một tài khoản khách hàng và trả danh tính dùng được để đăng nhập

#### Scenario: Email trùng hoặc dữ liệu sai
- **WHEN** email đã được dùng hoặc trường bắt buộc không hợp lệ
- **THEN** API báo lỗi phù hợp, không tạo thêm tài khoản và không trả mật khẩu hoặc mã băm

#### Scenario: Hai yêu cầu cùng đăng ký một email
- **WHEN** hai yêu cầu đăng ký dùng cùng email chuẩn hóa đến đồng thời
- **THEN** tối đa một tài khoản được tạo; yêu cầu còn lại nhận lỗi trùng email mà không để lại bản ghi dở

#### Scenario: Tự khai quyền quản trị
- **WHEN** request đăng ký chứa `role=ADMIN` hoặc `userId` tự khai
- **THEN** API từ chối trường không thuộc hợp đồng và không cấp quyền quản trị

### Requirement: Đăng nhập bằng tài khoản Oracle
API đăng nhập SHALL xác minh email, mật khẩu và trạng thái tài khoản trong Oracle; SHALL chỉ cấp JWT cho tài khoản `ACTIVE`. Lỗi thông tin đăng nhập SHALL không tiết lộ email có tồn tại hay không.

#### Scenario: Đăng nhập thành công
- **WHEN** khách hàng hoặc quản trị viên nhập email và mật khẩu đúng
- **THEN** API trả JWT có hạn dùng cùng hồ sơ công khai của chính tài khoản đó

#### Scenario: Sai mật khẩu hoặc tài khoản bị vô hiệu hóa
- **WHEN** mật khẩu sai, email không tồn tại hoặc tài khoản không còn `ACTIVE`
- **THEN** API từ chối, không phát hành JWT và không tiết lộ trường hợp nào đã xảy ra

### Requirement: JWT xác định danh tính cho API
Các API được bảo vệ SHALL chỉ chấp nhận JWT còn hạn, chữ ký và thông tin phát hành hợp lệ; SHALL lấy ID, vai trò và trạng thái hiện tại từ tài khoản Oracle thay vì tin dữ liệu tự khai của client. Thiếu hoặc sai token SHALL bị từ chối trước khi chạy nghiệp vụ.

#### Scenario: Đọc tài khoản hiện tại
- **WHEN** client gọi `GET /api/auth/me` với JWT hợp lệ của tài khoản đang hoạt động
- **THEN** API trả ID, email, họ tên và vai trò của tài khoản đó, không trả mật khẩu hoặc mã băm

#### Scenario: Token giả mạo, hết hạn hoặc sai mục đích
- **WHEN** JWT có chữ ký sai, hết hạn, sai issuer/audience hoặc không có tài khoản hợp lệ tương ứng
- **THEN** API trả `401` và không đọc hoặc thay đổi dữ liệu nghiệp vụ

#### Scenario: Tài khoản đổi trạng thái sau khi cấp token
- **WHEN** tài khoản bị vô hiệu hóa sau lúc JWT được cấp
- **THEN** yêu cầu tiếp theo bằng token cũ bị từ chối

### Requirement: Phân quyền khách hàng và quản trị viên
Backend SHALL yêu cầu vai trò `CUSTOMER` cho Cart/Checkout/Order và `ADMIN` cho `/api/admin/**`. Danh tính trong JWT SHALL không cho phép truy cập giỏ/đơn của khách khác hoặc tự nâng quyền bằng cách sửa request.

#### Scenario: Khách gọi API quản trị
- **WHEN** JWT của khách hàng được dùng cho `/api/admin/**`
- **THEN** API trả `403` và không thực hiện thao tác quản trị

#### Scenario: Quản trị viên gọi API khách hàng
- **WHEN** JWT của quản trị viên được dùng cho Cart/Checkout/Order
- **THEN** API trả `403` và không lấy danh tính của một khách khác làm mặc định

#### Scenario: Chưa đăng nhập
- **WHEN** client gọi API được bảo vệ mà không có JWT
- **THEN** API trả `401` và không chạy nghiệp vụ

### Requirement: Giao diện xác thực dùng API thật
Trang đăng ký/đăng nhập SHALL gọi API backend, hiển thị lỗi và trạng thái chờ, khôi phục người dùng từ JWT còn hợp lệ, gắn token cho API được bảo vệ và xóa phiên khi đăng xuất hoặc token hết hạn. Giao diện SHALL không cho chọn vai trò giả.

#### Scenario: Đăng nhập và trở lại trang định đến
- **WHEN** người dùng đăng nhập thành công từ `/login?next=...`
- **THEN** giao diện lưu phiên, chỉ điều hướng tới đường dẫn nội bộ hợp lệ và hiển thị đúng tài khoản trả về từ backend

#### Scenario: Tải lại trang hoặc token hết hạn
- **WHEN** trang tải lại với JWT còn hợp lệ hoặc token đã hết hạn
- **THEN** giao diện lần lượt khôi phục tài khoản qua `/api/auth/me` hoặc xóa phiên và yêu cầu đăng nhập lại

#### Scenario: Đăng xuất
- **WHEN** người dùng chọn đăng xuất
- **THEN** giao diện xóa phiên hiện tại, không tiếp tục gửi JWT trong các yêu cầu sau và chuyển trang được bảo vệ về đăng nhập

#### Scenario: JWT đã cấp trước khi đăng xuất
- **WHEN** một JWT được giữ ở nơi khác trước khi người dùng đăng xuất trên tab hiện tại
- **THEN** JWT đó chỉ hết hiệu lực khi tới `exp` hoặc tài khoản Oracle bị vô hiệu hóa; hệ thống không tuyên bố thu hồi tức thời trên server

#### Scenario: Đăng nhập từ frontend đến Oracle
- **WHEN** khách hàng đăng nhập trên giao diện local, tải lại trang rồi đăng xuất
- **THEN** lời gọi đi qua proxy `/api` đến backend; `/api/auth/me` phản ánh đúng tài khoản Oracle khi còn JWT, và sau đăng xuất frontend không gửi JWT cho yêu cầu mới
