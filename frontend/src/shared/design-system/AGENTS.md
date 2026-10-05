# Design system ShopFlash

Phong cách lấy cảm hứng từ TikTok Shop: nền trắng sạch, đỏ hồng cho mọi hành động mua và giá bán, cyan chỉ xuất hiện ở đồng hồ flash sale, chữ gần đen. Không dùng logo, tên hay hình ảnh thương hiệu TikTok.

## Cách dùng

- Bọc ứng dụng một lần trong `src/app`: `<ThemeProvider>…</ThemeProvider>`. Provider này theme Ant Design, đặt tiếng Việt và gắn biến CSS `--sf-*`.
- Import từ `shared/design-system`: `import { ProductCard, PriceTag, formatVND } from '@/shared/design-system'` (hoặc đường dẫn tương đối).
- Xem toàn bộ: mở `frontend/design-system.html` bằng trình duyệt, hoặc `Showcase.jsx` khi gắn vào route `/design-system`.
- `frontend/design-system.html` chép tay giá trị từ `tokens.js`. Đổi token thì sửa cả file HTML.

## Quy tắc

- IMPORTANT: Không viết mã màu, cỡ chữ, khoảng cách cố định trong code. Trong JSX dùng `tokens`; trong CSS dùng `var(--sf-...)`.
- Muốn đổi giá trị thì chỉ sửa `tokens.js`. `theme.js` tự sinh theme Ant Design và biến CSS từ đó.
- Control thông thường (Button, Input, Select, Table, Modal, Form, Pagination…) dùng thẳng Ant Design, không tự viết lại. Thư mục này chỉ chứa component riêng của ShopFlash mà Ant Design không có.
- Component dùng chung cho nhiều module mới được đặt ở đây. Component chỉ một module dùng thì để trong module đó.
- Tiền luôn hiển thị qua `formatVND` hoặc `PriceTag` ("549.000₫"). Số xếp cột dùng class `sf-num`. Mã đơn/mã sản phẩm dùng class `sf-code`.

## Màu (`tokens.color`)

| Token | Dùng cho |
|---|---|
| `brand` | Nút chính (`Button type="primary"`), giá bán, badge giảm giá, link. Mỗi màn hình chỉ một nút primary. |
| `brandHover`, `brandTint` | Hover của brand; nền nhạt cho vùng được chọn, badge % giảm |
| `cyan` | CHỈ làm nền ô số trong `FlashCountdown`, chữ `ink` trên nó. Không dùng cho chữ hay nút. |
| `ink`, `inkMuted` | Chữ chính; chữ phụ, giá gốc, chú thích. `ink` cũng là nền dải flash sale. |
| `surface`, `surfaceSunken` | Nền thẻ; nền trang và ô ảnh |
| `line`, `lineStrong` | Đường kẻ trang trí; viền input và nút phụ |
| `success`, `warning`, `info`, `danger` | Trạng thái. Luôn đi kèm chữ, không để màu tự nói. `danger` khác `brand`: brand là "mua", danger là "lỗi/xóa". |

Mọi cặp chữ/nền ở trên đạt tối thiểu 4.5:1, viền điều khiển đạt 3:1.

## Chữ

Phông Be Vietnam Pro (hỗ trợ đầy đủ tiếng Việt), mã dùng JetBrains Mono. Thang chữ trong `tokens.text`: `display` 32, `title` 22, `heading` 17, `body` 15, `caption` 13, `label` 12, `price` 20 (đậm 800).

## Khoảng cách, bo góc

- Khoảng cách theo bước 4px: `space[1..8]` = 4, 8, 12, 16, 24, 32.
- Bo góc: `sm` 4 (badge), `md` 8 (nút, input), `lg` 10 (thẻ), `pill` (tag trạng thái, thanh tiến độ).
- Thẻ chỉ có viền `line`. Bóng `shadow.card` chỉ xuất hiện khi hover.

## Component

| Component | Dùng ở | Props chính |
|---|---|---|
| `ProductCard` | Lưới sản phẩm (`indexing`), flash sale (`concurrency`) | `name, imageUrl, price, originalPrice, sold?, quota?, onBuy?, onAddToCart?, onOpen?` |
| `PriceTag` | Mọi chỗ hiện giá | `price, originalPrice?, size: 'sm' \| 'md' \| 'lg'` |
| `FlashCountdown` | Trang flash sale | `endsAt, title?, onEnd?` |
| `StockBar` | Suất flash sale | `sold, quota` |
| `OrderStatusTag` | Đơn hàng (`transaction`), báo cáo (`query`) | `status: 'PENDING' \| 'PAID' \| 'SHIPPED' \| 'CANCELLED'` (khớp CHECK của `ORDERS.status`) |

## Giọng văn

- Tiếng Việt, viết hoa chữ đầu câu, ngắn gọn: "Mua ngay", "Thêm vào giỏ", "Còn 12 suất".
- Nút ghi rõ hành động. Thông báo lỗi nói sai gì và cách sửa: "Số lượng vượt tồn kho, còn 3 sản phẩm."
- Không dùng emoji. Icon lấy từ `@ant-design/icons`.

## Phụ thuộc

Cần `react`, `antd` (v5), `@ant-design/icons`. Phông tải từ Google Fonts trong `global.css`.
