import { Typography } from 'antd';
import { useParams } from 'react-router-dom';
import ComingSoon from '@/shared/components/ComingSoon';
import { tokens } from '@/shared/design-system';

// F8 Chi tiết đơn hàng (TV4). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function OrderDetailPage() {
  const value = useParams().orderId;
  return (
    <div style={{ display: 'grid', gap: tokens.space[4] }}>
      {value && (
        <Typography.Text type="secondary">
          Mã đơn trên URL: <span className="sf-code">{value}</span>
        </Typography.Text>
      )}
      <ComingSoon
        feature="F8"
        title="Chi tiết đơn hàng"
        owner="TV4"
        module="transaction"
        file="frontend/src/modules/transaction/pages/OrderDetailPage.jsx"
        description="Snapshot của đơn đã đặt và thao tác hủy đơn khi được phép."
        todo={[
          'Sản phẩm, đơn giá lúc đặt, số lượng, thành tiền (ORDER_ITEMS); thông tin nhận hàng; OrderStatusTag.',
          'Nút "Hủy đơn" chỉ hiện khi trạng thái cho phép; Popconfirm hỏi xác nhận; hủy trong transaction (hoàn tồn kho, đổi trạng thái).',
          'Đơn không tồn tại hoặc không thuộc người dùng hiện tại: báo lỗi rõ ràng.',
        ]}
        api={[
          'GET /api/orders/{orderId}',
          'POST /api/orders/{orderId}/cancel',
        ]}
      />
    </div>
  );
}
