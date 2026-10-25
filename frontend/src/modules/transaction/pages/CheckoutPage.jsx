import ComingSoon from '@/shared/components/ComingSoon';

// F7 Thanh toán (TV4). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function CheckoutPage() {
  return (
    <ComingSoon
      feature="F7"
      title="Thanh toán"
      owner="TV4"
      module="transaction"
      file="frontend/src/modules/transaction/pages/CheckoutPage.jsx"
      description="Kiểm tra lại giỏ hàng, giá, tồn kho và thông tin nhận hàng rồi tạo đơn trong một transaction."
      todo={[
        'Kiểm tra lại sản phẩm, số lượng, giá hiện tại (từ giỏ hoặc suất flash sale vừa giữ); báo rõ khi giá đổi hoặc hết hàng.',
        'Form thông tin nhận hàng (họ tên, số điện thoại, địa chỉ) có kiểm tra dữ liệu.',
        'Tổng tiền bằng PriceTag; một nút primary "Đặt hàng" có loading, chống bấm hai lần.',
        'Đặt hàng trong một transaction Oracle: trừ tồn kho, tạo ORDERS và ORDER_ITEMS, xóa giỏ; lỗi thì rollback và báo lý do.',
        'Thành công thì chuyển sang /orders/:orderId.',
      ]}
      api={[
        'POST /api/orders { shippingName, shippingPhone, shippingAddress }',
        'GET /api/cart (module transaction)',
      ]}
    />
  );
}
