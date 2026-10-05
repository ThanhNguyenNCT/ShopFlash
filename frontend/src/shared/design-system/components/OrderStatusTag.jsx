import './OrderStatusTag.css';

// Khớp CHECK constraint của ORDERS.status trong Oracle.
const STATUS = {
  PENDING: 'Chờ thanh toán',
  PAID: 'Đã thanh toán',
  SHIPPED: 'Đang giao',
  CANCELLED: 'Đã hủy',
};

export default function OrderStatusTag({ status }) {
  const key = STATUS[status] ? status : 'PENDING';
  return <span className={`sf-status sf-status--${key.toLowerCase()}`}>{STATUS[key]}</span>;
}
