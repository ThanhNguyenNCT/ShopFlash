// Nguồn duy nhất của design system ShopFlash (phương án A: Hồng đỏ + Cyan).
// Theme Ant Design và biến CSS (--sf-*) đều sinh ra từ file này. Đổi màu, cỡ chữ... chỉ sửa ở đây.

export const color = {
  surface: '#ffffff',        // nền trang, nền thẻ
  surfaceSunken: '#f4f4f6',  // nền khu vực phụ, ô ảnh sản phẩm, nền layout admin
  ink: '#161823',            // chữ chính; nền dải flash sale
  inkMuted: '#656875',       // chữ phụ, giá gốc, chú thích (5.5:1 trên surface)
  line: '#e6e6ea',           // đường kẻ, viền thẻ (trang trí, không mang nghĩa)
  lineStrong: '#868893',     // viền input, nút ghost (3.5:1, đủ cho viền điều khiển)
  brand: '#d80e3c',          // nút mua, giá bán, badge giảm giá (chữ trắng trên nó 5.2:1)
  brandHover: '#b80b33',     // hover / active của brand
  brandTint: '#fff0f3',      // nền nhạt cho vùng được chọn, badge phụ
  onBrand: '#ffffff',        // chữ trên nền brand
  cyan: '#25f4ee',           // CHỈ dùng làm nền ô số đồng hồ flash sale, chữ ink trên nó
  track: '#ffe1e7',          // rãnh thanh tiến độ suất flash sale
  success: '#0a7d4f',
  warning: '#9a5200',
  info: '#0a6f78',
  danger: '#c0261f',         // lỗi form, thao tác xóa. Luôn kèm chữ, không dùng màu đơn lẻ
  focus: '#161823',          // vòng focus bàn phím
};

export const font = {
  sans: "'Be Vietnam Pro', system-ui, -apple-system, 'Segoe UI', sans-serif",
  mono: "'JetBrains Mono', ui-monospace, Consolas, monospace",
};

// [fontSize, lineHeight, fontWeight]
export const text = {
  display: [32, 40, 800],  // tiêu đề trang lớn, banner
  title: [22, 30, 700],    // tiêu đề trang
  heading: [17, 24, 600],  // tiêu đề khối, tên sản phẩm ở trang chi tiết
  body: [15, 22, 400],     // nội dung mặc định
  caption: [13, 18, 400],  // chú thích, giá gốc, số suất
  label: [12, 16, 600],    // nhãn viết hoa, badge
  price: [20, 24, 800],    // giá bán
};

export const space = { 1: 4, 2: 8, 3: 12, 4: 16, 6: 24, 8: 32 };

export const radius = { sm: 4, md: 8, lg: 10, pill: 999 };

export const shadow = {
  card: '0 1px 2px rgba(22, 24, 35, 0.06), 0 4px 12px rgba(22, 24, 35, 0.06)',
  popup: '0 8px 24px rgba(22, 24, 35, 0.14)',
};
