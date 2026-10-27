import { Tag } from 'antd';

const LABELS = {
  PENDING: 'Chờ xử lý',
  CONFIRMED: 'Đã xác nhận',
  COMPLETED: 'Đã hoàn tất',
  CANCELLED: 'Đã hủy',
};

export default function OracleOrderStatus({ status }) {
  return <Tag className={`sf-tx-status sf-tx-status--${String(status ?? 'unknown').toLowerCase()}`}>
    {LABELS[status] ?? status}
  </Tag>;
}
