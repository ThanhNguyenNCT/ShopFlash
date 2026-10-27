# Spec Delta

## Purpose

Xây dựng các thí nghiệm có thể chạy lại để so sánh phạm vi của giao dịch, trạng thái dữ liệu sau lỗi và hiệu năng trên Oracle và Redis trong bối cảnh ShopFlash.

## ADDED Requirements

### Requirement: Thí nghiệm hoàn tác và kiểm tra trạng thái dữ liệu
Bộ thí nghiệm SHALL mô phỏng các bước cập nhật giỏ hàng, đơn hàng và tồn kho bằng dữ liệu riêng trên từng hệ quản trị. Mỗi trường hợp SHALL ghi trạng thái trước, trạng thái mong đợi, trạng thái thực tế sau và kết quả kiểm chứng có thể đối chiếu với lệnh/truy vấn hoặc log; SHALL phân biệt việc hủy lệnh chưa chạy với việc hoàn tác thay đổi đã ghi.

#### Scenario: Oracle `COMMIT` thành công
- **WHEN** các bước ghi giỏ, đơn và tồn kho hợp lệ được `COMMIT`
- **THEN** kết nối Oracle độc lập nhìn thấy tất cả thay đổi mong đợi; kết quả kiểm chứng ghi rõ không có trạng thái chỉ được ghi một phần

#### Scenario: Oracle lỗi giữa các lệnh ghi
- **WHEN** lỗi được tạo có chủ đích sau một hoặc nhiều lệnh ghi nhưng trước `COMMIT`
- **THEN** kết quả kiểm tra cho thấy `ROLLBACK` hoàn tác mọi thay đổi trong giao dịch Oracle

#### Scenario: Redis `MULTI/EXEC` thành công
- **WHEN** các lệnh hợp lệ được xếp hàng bằng `MULTI` rồi chạy bằng `EXEC`
- **THEN** client Redis độc lập nhìn thấy các thay đổi đã thực thi, và kết quả được đối chiếu với trạng thái mong đợi

#### Scenario: Redis `DISCARD` trước `EXEC`
- **WHEN** các lệnh được xếp hàng bằng `MULTI` rồi gọi `DISCARD` trước `EXEC`
- **THEN** không lệnh nào trong hàng đợi được thực thi; báo cáo nêu rõ đây là hủy các lệnh chưa chạy

#### Scenario: Redis lỗi lúc `EXEC`
- **WHEN** một lệnh trong hàng đợi gặp lỗi lúc `EXEC` thực thi
- **THEN** kết quả cho thấy các lệnh hợp lệ khác vẫn có thể đã chạy và Redis không tự hoàn tác chúng

#### Scenario: Redis `WATCH` phát hiện thay đổi
- **WHEN** khóa đang được `WATCH` bị thay đổi trước `EXEC`
- **THEN** `EXEC` không chạy các lệnh đã xếp hàng; báo cáo phân biệt kết quả này với hoàn tác sau khi ghi

#### Scenario: Redis Lua lỗi sau lệnh ghi
- **WHEN** script Lua ghi một khóa rồi gặp lỗi lúc chạy
- **THEN** thí nghiệm kiểm tra thay đổi đã ghi vẫn còn, đồng thời phân biệt việc script chạy không bị chen ngang với khả năng hoàn tác

#### Scenario: Lưu bằng chứng cho từng cơ chế
- **WHEN** một thí nghiệm `COMMIT`, `ROLLBACK`, isolation, locking, `MULTI/EXEC`, `DISCARD`, `WATCH` hoặc Lua kết thúc
- **THEN** tệp bằng chứng ghi mã kịch bản, bước/lệnh thực thi, trạng thái trước, kết quả mong đợi, kết quả thực tế sau, assertion đạt/trượt và tham chiếu truy vấn/lệnh hoặc log; không chỉ ghi mỗi trạng thái HTTP hay thời gian chạy

### Requirement: Thí nghiệm tính cô lập và tranh chấp
Bộ thí nghiệm SHALL dùng ít nhất hai phiên hoặc hai khách đồng thời để quan sát dữ liệu trước/sau `COMMIT`, xung đột ghi và kết quả cuối của từng hệ quản trị; SHALL không suy ra khả năng hoàn tác từ tính cô lập.

#### Scenario: Oracle đọc khi giao dịch khác chưa commit
- **WHEN** phiên A sửa dữ liệu nhưng chưa `COMMIT` và phiên B đọc cùng dữ liệu
- **THEN** kết quả ghi rõ phiên B nhìn thấy giá trị nào trước và sau khi phiên A hoàn tất, theo mức cô lập thực tế đã cấu hình

#### Scenario: Hai giao dịch Oracle tranh cùng giỏ hoặc tồn kho
- **WHEN** hai giao dịch cùng sửa giỏ hoặc mua lượng hàng còn lại
- **THEN** thí nghiệm ghi nhận giao dịch nào thành công, giao dịch nào chờ hoặc bị từ chối, và kiểm tra không mất cập nhật hoặc bán vượt tồn

#### Scenario: Redis dùng `WATCH` khi khóa bị thay đổi
- **WHEN** một khách sửa khóa đã được khách khác `WATCH` trước `EXEC`
- **THEN** `EXEC` của khách theo dõi không ghi thay đổi và kết quả được đối chiếu với trạng thái khóa cuối

#### Scenario: Tái hiện thứ tự tranh chấp có kiểm soát
- **WHEN** kiểm thử hai phiên sửa giỏ, đặt/hủy đơn, cập nhật tồn kho hoặc xung đột `WATCH`
- **THEN** bài test dùng điểm đồng bộ để xác nhận phiên thứ nhất đã tới mốc khóa/`WATCH` trước khi cho phiên thứ hai tiếp tục, có giới hạn thời gian chờ và kiểm tra trạng thái cuối; không phụ thuộc vào thời gian ngủ ngẫu nhiên

### Requirement: Phép đo hiệu năng có thể chạy lại
Phép đo SHALL công bố khối lượng công việc, kích thước dữ liệu, môi trường, cấu hình và cách đo. Hiệu năng SHALL bổ sung cho kiểm chứng tính đúng đắn. Kết quả SHALL tách phép đo cơ chế tại cơ sở dữ liệu khỏi phép đo HTTP của ứng dụng, có nhiều lượt chạy và thể hiện mức dao động; SHALL không so sánh trực tiếp độ trễ hoặc thông lượng giữa hai lớp.

#### Scenario: Chạy cấu hình mặc định
- **WHEN** người nghiên cứu chạy kịch bản mặc định với giỏ 1 hoặc 5 sản phẩm và mức đồng thời 1 hoặc 10
- **THEN** mỗi tổ hợp có 50 thao tác làm nóng, tiếp theo năm lượt đo với 200 thao tác mỗi lượt; mọi điều chỉnh số mẫu đều được ghi rõ lý do

#### Scenario: Công bố chỉ số và cách tính
- **WHEN** một kịch bản đo hoàn tất
- **THEN** báo cáo có p50/p95 tính theo mili giây từ toàn bộ mẫu thành công có thời gian đo hợp lệ, thông lượng thành công trên thời gian thực của toàn lượt đo, error rate = `error`/mẫu hoàn tất, rejection rate = `rejected`/mẫu hoàn tất, số vi phạm bất biến và trung bình/độ lệch chuẩn giữa năm lượt khi có đủ mẫu; lỗi tiêm chủ đích và mẫu không hợp lệ được thống kê riêng

#### Scenario: Chỉ số HTTP theo API và toàn luồng
- **WHEN** k6 đo Cart, Checkout, Order hoặc chuỗi API
- **THEN** kết quả tách độ trễ/thông lượng thành công của từng API và của toàn luồng, ghi số yêu cầu trong mỗi luồng, không thay độ trễ Checkout bằng `http_req_duration` gộp mọi API

#### Scenario: So sánh cùng khối lượng công việc
- **WHEN** so sánh luồng đặt hàng trên Oracle với một luồng mô phỏng trên Redis
- **THEN** hai runner dùng cùng thao tác logic kiểm tra giỏ/tồn, ghi đầu đơn và dòng, giảm tồn, dọn giỏ; báo cáo nêu quy mô dữ liệu, payload, mức đồng thời, warm-up, năm lượt đo, cách reset và phạm vi thời gian đo, đồng thời chỉ rõ khác biệt không chuẩn hóa hoàn toàn về ràng buộc, transaction semantics, durability và chi phí client/network; kết quả Redis mô phỏng không được trình bày như tốc độ của ứng dụng ShopFlash hoàn chỉnh

#### Scenario: Đo chi phí cơ chế
- **WHEN** chạy trường hợp thành công, bị từ chối và gặp lỗi giữa quá trình ghi
- **THEN** báo cáo nêu cả thời gian xử lý và trạng thái dữ liệu cuối trên từng hệ quản trị, không dùng tốc độ để thay cho đánh giá tính nhất quán

#### Scenario: Phân biệt phép đo cơ sở dữ liệu và HTTP
- **WHEN** người đọc xem kết quả Oracle–Redis và kết quả Cart/Checkout/Order qua HTTP
- **THEN** hai nhóm có bảng và ranh giới đo riêng; kết quả mô phỏng Redis không được trình bày như hiệu năng ứng dụng ShopFlash

### Requirement: Kiểm tra tính đúng đắn sau phép đo
Mỗi kịch bản SHALL kiểm tra số giao dịch thành công, trạng thái sau `COMMIT` hoặc `ROLLBACK` và số lần vi phạm các bất biến giỏ, đơn, tồn kho; lỗi và yêu cầu bị từ chối SHALL được ghi riêng.

#### Scenario: Sau lượt đo thành công hoặc có lỗi
- **WHEN** một lượt đo kết thúc
- **THEN** kết quả nêu số đơn được tạo, lượng tồn đã giảm/hoàn, số yêu cầu thành công, bị từ chối và lỗi ngoài dự kiến; mọi vi phạm bất biến được đếm thay vì bỏ qua

### Requirement: Dữ liệu thí nghiệm tách khỏi dữ liệu nghiệp vụ
Thí nghiệm SHALL dùng dữ liệu Oracle và tiền tố khóa Redis riêng, có lệnh chuẩn bị, đặt lại và dọn dữ liệu an toàn. Thí nghiệm SHALL không sửa dữ liệu mẫu nền, trạng thái Flash Sale hoặc mô hình đọc của module khác.

#### Scenario: Chạy trên môi trường local đã chốt
- **WHEN** người nghiên cứu làm theo hướng dẫn trên máy local
- **THEN** Oracle/Redis chạy trong Docker, backend/frontend chạy trên host; k6 đăng nhập tài khoản fixture để lấy JWT, frontend gọi API qua proxy `/api` mà không cần bật CORS

#### Scenario: Chạy lại thí nghiệm
- **WHEN** bộ thí nghiệm được chạy lần thứ hai
- **THEN** dữ liệu đầu vào giống lần đầu; đơn hàng và tồn kho thử nghiệm không bị cộng dồn

#### Scenario: Kết thúc hoặc lỗi thí nghiệm
- **WHEN** chương trình thí nghiệm hoàn tất hoặc gặp lỗi
- **THEN** hướng dẫn dọn dữ liệu chỉ tác động đến dữ liệu thử nghiệm, không xóa dữ liệu nghiệp vụ khác

#### Scenario: Bàn giao kết quả thô
- **WHEN** thí nghiệm đã chạy
- **THEN** có tệp CSV/JSON chứa mẫu đo, trạng thái từng yêu cầu và cấu hình đã dùng, cùng script setup/reset/cleanup, hướng dẫn tái chạy và bằng chứng trước/mong đợi/sau của các thí nghiệm; không điền số liệu ước đoán thay cho kết quả thực tế

### Requirement: Kết luận theo nhu cầu của ShopFlash
Báo cáo SHALL so sánh tính nguyên tử, tính cô lập, độ bền dữ liệu theo cấu hình thử và khả năng hoàn tác. Phần Transaction SHALL theo thứ tự của `report/AGENTS.md`: Lý thuyết → Oracle (demo và số đo) → Redis (demo và số đo) → Bảng so sánh → Kết luận theo ShopFlash. Kết luận SHALL giải thích hệ quản trị phù hợp cho đơn hàng, giỏ hàng, tồn kho và trạng thái Flash Sale dựa trên kết quả thí nghiệm.

#### Scenario: Hoàn tất báo cáo
- **WHEN** người đọc xem phần báo cáo Transaction và phụ lục lệnh chạy
- **THEN** mỗi kết luận có thể đối chiếu với kịch bản, số đo, trạng thái dữ liệu sau lỗi và tài liệu của hệ quản trị tương ứng
