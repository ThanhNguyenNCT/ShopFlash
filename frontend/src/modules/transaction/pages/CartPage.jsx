import ComingSoon from '@/shared/components/ComingSoon';

// F10 Giỏ hàng (TV4). Placeholder sau bàn giao route; giao diện thật thuộc task 2.5.
export default function CartPage() {
  return (
    <ComingSoon
      feature="F10"
      title="Giỏ hàng"
      owner="TV4"
      module="transaction"
      file="frontend/src/modules/transaction/pages/CartPage.jsx"
      description="Xem và sửa giỏ Oracle trước khi thanh toán; giá và tồn kho sẽ được kiểm tra lại tại Checkout."
      todo={[
        'Xem giỏ, thêm sản phẩm, sửa số lượng, xóa một sản phẩm hoặc xóa toàn bộ giỏ.',
        'Dùng phiên bản giỏ để báo xung đột khi hai yêu cầu cùng sửa.',
        'Giỏ rỗng có lối về danh sách sản phẩm; giỏ có hàng có lối sang /checkout.',
      ]}
      api={[
        'GET /api/cart',
        'POST /api/cart/items',
        'PATCH /api/cart/items/{productId}',
        'DELETE /api/cart/items/{productId}',
        'DELETE /api/cart/items',
      ]}
    />
  );
}
