import ComingSoon from '@/shared/components/ComingSoon';

// F8 Đơn hàng của tôi (TV4). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function OrdersPage() {
  return (
    <ComingSoon
      feature="F8"
      title="Đơn hàng của tôi"
      owner="TV4"
      module="transaction"
      file="frontend/src/modules/transaction/pages/OrdersPage.jsx"
      description="Danh sách đơn của khách đang đăng nhập."
      todo={[
        'Bảng đơn: mã đơn (sf-code), ngày đặt, số sản phẩm, tổng tiền (PriceTag), trạng thái (OrderStatusTag).',
        'Phân trang, lọc theo trạng thái; bấm một đơn để mở /orders/:orderId.',
        'Không có đơn: Empty kèm link mua sắm. Chưa đăng nhập: nhắc đăng nhập. Có loading và báo lỗi.',
      ]}
      api={[
        'GET /api/orders?status=&page=&size=',
      ]}
    />
  );
}
