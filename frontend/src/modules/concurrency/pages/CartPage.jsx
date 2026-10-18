import ComingSoon from '@/shared/components/ComingSoon';

// F10 Giỏ hàng (TV5). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function CartPage() {
  return (
    <ComingSoon
      feature="F10"
      title="Giỏ hàng"
      owner="TV5"
      module="concurrency"
      file="frontend/src/modules/concurrency/pages/CartPage.jsx"
      description="Xem, sửa số lượng, xóa sản phẩm trước khi thanh toán. Giỏ lưu trong Oracle (CARTS, CART_ITEMS)."
      todo={[
        'Bảng sản phẩm trong giỏ: tên, PriceTag, InputNumber số lượng (không vượt tồn kho), thành tiền; xóa có Popconfirm.',
        'Tổng tiền và một nút primary "Thanh toán" sang /checkout.',
        'Giỏ rỗng: Empty kèm link về trang chủ. Chưa đăng nhập: nhắc đăng nhập.',
        'Export nút/component "Thêm vào giỏ" để trang chi tiết sản phẩm (indexing) dùng lại.',
        'Xử lý hai tab cùng sửa một giỏ (khóa dòng hoặc version) và báo lỗi dễ hiểu.',
      ]}
      api={[
        'GET /api/cart',
        'POST /api/cart/items { productId, quantity }',
        'PATCH /api/cart/items/{productId} { quantity }',
        'DELETE /api/cart/items/{productId}',
      ]}
    />
  );
}
