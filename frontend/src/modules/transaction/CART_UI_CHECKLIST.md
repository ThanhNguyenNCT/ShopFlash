# Kiểm tra chức năng F0/F10 và giao diện cuối

Điều kiện: Oracle có schema Flyway hiện tại, Redis chạy Docker; backend chạy profile `dev` trên host với `JWT_SECRET_BASE64` trong `.env`, Vite chạy trên host bằng `npm run dev` (không dùng `dev:mock` cho bài kiểm tra giao dịch). Đăng nhập `customer@shopflash.local` bằng mật khẩu dev seed ghi trong [README Transaction](../../../../backend/docs/transaction/README.md). Schema `SHOP` local thiếu `flyway_schema_history` phải được xử lý đúng trước khi dùng; không tự xóa hoặc baseline dữ liệu. Trên schema dev mới, giỏ trống; thêm sản phẩm từ `/search` hoặc trang chi tiết; `TX-105` hết hàng, `TX-106` ngừng bán. Dữ liệu mẫu chỉ dùng để thử local; bài kiểm thử tự động tiếp tục dùng fixture độc lập.

1. Mở `/login`, nhập email/mật khẩu khách rồi vào `/cart`. Giao diện phải cho xem và thao tác giỏ. Thử `/register` với email mới, sau đó đăng nhập tài khoản vừa tạo; kiểm tra tài khoản mới chỉ thấy giỏ của mình.
2. Khi chưa đăng nhập, `/cart` hiện đường dẫn tới `/login?next=/cart` và không gọi `/api/cart`. Sau đăng nhập, Network phải gọi `/api/auth/login` rồi `/api/cart`; JWT chỉ nằm trong request header của Cart, không ở URL. Tải lại trang: `/api/auth/me` khôi phục đúng khách, rồi mới tải giỏ.
3. Đăng xuất, sau đó đăng nhập Admin: `/cart` không hiển thị giỏ khách; gọi trực tiếp Cart API bằng JWT Admin nhận `403`. Khách khác đăng nhập không xem hoặc sửa được giỏ trước đó. JWT không in trên màn hình hoặc console.
4. Giỏ mới hiển thị trạng thái trống và link **Tiếp tục mua sắm** tới `/search`. Mở `/products/2101`, thêm AirPods Max hai lần; Cart phải có một dòng quantity=2 với giá từ Oracle, tồn kho không đổi. Trang giỏ không còn form nhập ID/chọn nhanh sản phẩm mẫu. Nếu giỏ đã có dữ liệu, ghi nhận baseline rồi đối chiếu phép cộng. Sản phẩm hết hàng không có nút thêm ở Listing; dùng fixture/API test để kiểm tra nhánh từ chối, không sửa tồn kho của catalog để giả lập.

5. Bấm nút **+** của AirPods Max hai lần liên tiếp: số lượng, thành tiền dòng và tổng giỏ đổi ngay; trang tự lưu bằng `PATCH`, không có nút **Cập nhật**. Chờ dòng “Đang lưu số lượng…” biến mất, đối chiếu `CART_ITEMS.quantity` và `CARTS.version` trong Oracle; tải lại trang vẫn thấy số lượng cuối. Tồn kho không đổi. Nhập 0 hoặc 1000 không gửi lệnh thành công.
6. Các mặt hàng vừa thêm chưa được chọn để Checkout và nút **Đặt hàng (0)** bị khóa. Chọn hai mặt hàng; tổng đã chọn chỉ tính hai dòng, tải lại cùng tab vẫn giữ lựa chọn. Khi đang lưu số lượng, nút đặt hàng bị khóa. Bấm **Xóa** và **Xóa toàn bộ**: cả hai có xác nhận; mặt hàng đã xóa không còn được chọn. Nút **Đặt hàng** mở `/checkout` khi đã chọn ít nhất một dòng.
7. Mở hai tab cùng khách (đăng nhập ở từng tab). Tab A sửa giỏ; tab B dùng phiên bản cũ để sửa. Tab B nhận `409 CART_CHANGED`, tự tải lại và báo người dùng xem giỏ trước khi tiếp tục; không làm mất thay đổi của tab A.
8. Trong DevTools kiểm tra JWT được giữ ở `sessionStorage` của tab và không có trong `localStorage`, URL hoặc log. Sau khi bấm **Đăng xuất**, Cart không còn gửi JWT. JWT đã sao chép trước đó vẫn hợp lệ đến `exp` hoặc khi tài khoản bị vô hiệu hóa. Gọi API trực tiếp khác origin từ trang Vite không thuộc luồng hỗ trợ; các lời gọi của trang dùng proxy `/api`.

Đối chiếu bằng **kết nối Oracle khác** sau mỗi thao tác. Dùng bind variable `:email` = `customer@shopflash.local`, `:productId` = `2101`:

```sql
SELECT c.version, ci.quantity
FROM users u JOIN carts c ON c.user_id = u.user_id
LEFT JOIN cart_items ci ON ci.cart_id = c.cart_id AND ci.product_id = :productId
WHERE u.email = :email;

SELECT on_hand_quantity, reserved_flash_quantity, version
FROM product_inventory WHERE product_id = :productId;
```

Cart chỉ cộng/trừ hoặc đặt `CART_ITEMS.quantity`; tồn kho không đổi trong F10. Khi F7 xác nhận đơn `NORMAL`, phép tính cần kiểm chứng là tồn kho mới = tồn kho cũ − số lượng đặt; khi F8 hủy đơn hợp lệ, tồn kho mới = tồn kho trước khi hủy + số lượng hoàn. Những thay đổi tồn kho đó phải nằm trong giao dịch đặt/hủy đơn, không sửa tay `PRODUCT_INVENTORY` để giả lập một lần Checkout thành công. Khi lệnh cộng bị mất phản hồi, tải lại `/cart` trước khi bấm lại để tránh cộng đôi.

## Chọn tất cả sản phẩm

- Giỏ có sản phẩm: bấm **Chọn tất cả**, toàn bộ checkbox được chọn, số dòng và tổng đã chọn khớp giỏ; nút đổi thành **Bỏ chọn tất cả**.
- Bỏ chọn riêng một dòng rồi bấm **Chọn tất cả**: chọn đủ mọi dòng. Bấm **Bỏ chọn tất cả**: tổng về 0 và nút đặt hàng bị khóa.
- Tải lại cùng tab vẫn giữ lựa chọn. Thao tác chọn không gửi request sửa giỏ. Giỏ trống không hiển thị nút chọn tất cả.
- Kiểm tra nút và số lượng đã chọn ở desktop và mobile 375 px, không cuộn ngang.

## Rà soát hình thức cuối change

Sau khi F0/F10/F7/F8 đã hoạt động đúng, mở DevTools với viewport **375 × 812** và rà soát `/login`, `/register`, `/cart`, `/checkout`, `/orders`, `/orders/:orderId`: không cuộn ngang, không cắt tiêu đề, giá, ô nhập hoặc nút thao tác. Ghi các góp ý về bố cục, màu sắc và khoảng cách để chỉnh trong một lượt cuối. Nếu lỗi hiển thị làm không thể thao tác chức năng, xử lý ngay ở mốc đang kiểm thử.
