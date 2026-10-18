import { Alert } from 'antd';

// Báo kết quả đang hiện ứng với tham số của lần chạy trước (khoảng thời gian hoặc lựa chọn trong tab đã đổi).
export default function StaleAlert({ stale, action = 'chạy lại' }) {
  if (!stale) return null;
  return (
    <Alert
      type="warning"
      showIcon
      title={`Khoảng thời gian hoặc tham số đã đổi sau lần chạy này. Kết quả bên dưới là của tham số cũ, bấm ${action} để cập nhật.`}
    />
  );
}
