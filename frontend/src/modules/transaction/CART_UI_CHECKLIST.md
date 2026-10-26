# Kiểm tra chức năng F0/F10 và giao diện cuối

Điều kiện: Oracle có schema Flyway V2, Redis chạy Docker; backend chạy profile `dev` trên host với `JWT_SECRET_BASE64` trong `.env`, Vite chạy trên host bằng `npm run dev` (không dùng `dev:mock` cho bài kiểm tra giao dịch). Đăng nhập `customer@shopflash.local` bằng mật khẩu dev seed ghi trong [README Transaction](../../../../backend/src/main/java/com/shopflash/modules/transaction/README.md). Schema `SHOP` local thiếu `flyway_schema_history` phải được xử lý đúng trước khi dùng; không tự xóa hoặc baseline dữ liệu. Dữ liệu `CORE-001` (product ID 1) và giỏ mẫu của khách này chỉ dùng để thử local; bài kiểm thử tự động tiếp tục dùng fixture độc lập.

1. Mở `/login`, nhập email/mật khẩu khách rồi vào `/cart`. Giao diện phải cho xem và thao tác giỏ. Thử `/register` với email mới, sau đó đăng nhập tài khoản vừa tạo; kiểm tra tài khoản mới chỉ thấy giỏ của mình.
2. Khi chưa đăng nhập, `/cart` hiện đường dẫn tới `/login?next=/cart` và không gọi `/api/cart`. Sau đăng nhập, Network phải gọi `/api/auth/login` rồi `/api/cart`; JWT chỉ nằm trong request header của Cart, không ở URL. Tải lại trang: `/api/auth/me` khôi phục đúng khách, rồi mới tải giỏ.
3. Đăng xuất, sau đó đăng nhập Admin: `/cart` không hiển thị giỏ khách; gọi trực tiếp Cart API bằng JWT Admin nhận `403`. Khách khác đăng nhập không xem hoặc sửa được giỏ trước đó. JWT không in trên màn hình hoặc console.
4. Trong ô **Thêm sản phẩm vào giỏ**, nhập ID `1` và số lượng `2`, bấm **Thêm vào giỏ**. Nếu giỏ mẫu ban đầu có một `CORE-001`, Oracle phải có một dòng `CART_ITEMS` với `quantity=3`; `PRODUCT_INVENTORY.on_hand_quantity` vẫn bằng 20. Nếu giỏ đã được sửa trước đó, lấy số lượng và tồn kho ban đầu bằng truy vấn bên dưới rồi đối chiếu phép cộng tương ứng.
5. Sửa số lượng `CORE-001` thành `1` rồi bấm **Cập nhật**. Đây là đặt số lượng tuyệt đối: `CART_ITEMS.quantity=1`, không phải giảm tồn kho hai đơn vị. Tổng và số lượng khớp phản hồi API; `version` tăng một lần. Nhập 0 hoặc 1000 không gửi lệnh thành công. Tải lại trang vẫn thấy dữ liệu Oracle đã lưu.
6. Bấm **Xóa** và **Xóa toàn bộ**: cả hai có xác nhận. Sau khi xác nhận, giỏ rỗng và ô thêm sản phẩm vẫn dùng được; không xác nhận thì giỏ giữ nguyên. Bấm **Thanh toán** từ giỏ có hàng dẫn tới `/checkout` (trang Checkout được triển khai ở milestone 3).
7. Mở hai tab cùng khách (đăng nhập ở từng tab). Tab A sửa giỏ; tab B dùng phiên bản cũ để sửa. Tab B nhận `409 CART_CHANGED`, tự tải lại và báo người dùng xem giỏ trước khi tiếp tục; không làm mất thay đổi của tab A.
8. Trong DevTools kiểm tra JWT được giữ ở `sessionStorage` của tab và không có trong `localStorage`, URL hoặc log. Sau khi bấm **Đăng xuất**, Cart không còn gửi JWT. JWT đã sao chép trước đó vẫn hợp lệ đến `exp` hoặc khi tài khoản bị vô hiệu hóa. Gọi API trực tiếp khác origin từ trang Vite không thuộc luồng hỗ trợ; các lời gọi của trang dùng proxy `/api`.

Đối chiếu bằng **kết nối Oracle khác** sau mỗi thao tác. Dùng bind variable `:email` = `customer@shopflash.local`, `:productId` = `1`:

```sql
SELECT c.version, ci.quantity
FROM users u JOIN carts c ON c.user_id = u.user_id
LEFT JOIN cart_items ci ON ci.cart_id = c.cart_id AND ci.product_id = :productId
WHERE u.email = :email;

SELECT on_hand_quantity, reserved_flash_quantity, version
FROM product_inventory WHERE product_id = :productId;
```

Cart chỉ cộng/trừ hoặc đặt `CART_ITEMS.quantity`; tồn kho không đổi trong F10. Khi F7 xác nhận đơn `NORMAL`, phép tính cần kiểm chứng là tồn kho mới = tồn kho cũ − số lượng đặt; khi F8 hủy đơn hợp lệ, tồn kho mới = tồn kho trước khi hủy + số lượng hoàn. Những thay đổi tồn kho đó phải nằm trong giao dịch đặt/hủy đơn, không sửa tay `PRODUCT_INVENTORY` để giả lập một lần Checkout thành công. Khi lệnh cộng bị mất phản hồi, tải lại `/cart` trước khi bấm lại để tránh cộng đôi.

## Rà soát hình thức cuối change

Sau khi F0/F10/F7/F8 đã hoạt động đúng, mở DevTools với viewport **375 × 812** và rà soát `/login`, `/register`, `/cart`, `/checkout`, `/orders`, `/orders/:orderId`: không cuộn ngang, không cắt tiêu đề, giá, ô nhập hoặc nút thao tác. Ghi các góp ý về bố cục, màu sắc và khoảng cách để chỉnh trong một lượt cuối. Nếu lỗi hiển thị làm không thể thao tác chức năng, xử lý ngay ở mốc đang kiểm thử.
