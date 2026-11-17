import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Pagination, Select, Spin, Typography } from 'antd';
import { Link, Navigate, useLocation } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, tokens } from '@/shared/design-system';
import { getOrders } from '../api';
import OracleOrderStatus from '../components/OracleOrderStatus';

const PAGE_SIZE = 20;
const STATUS_OPTIONS = [
  { value: '', label: 'Tất cả trạng thái' },
  { value: 'PENDING', label: 'Chờ xử lý' },
  { value: 'CONFIRMED', label: 'Đã xác nhận' },
  { value: 'COMPLETED', label: 'Đã hoàn tất' },
  { value: 'CANCELLED', label: 'Đã hủy' },
];

function placedAt(value) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })
    .format(new Date(value));
}

export default function OrdersPage() {
  const { user, loading: authLoading } = useAuth();
  const { pathname, search, hash } = useLocation();
  const [page, setPage] = useState(1);
  const [status, setStatus] = useState('');
  const [loaded, setLoaded] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [reload, setReload] = useState(0);
  const requestSeq = useRef(0);
  const orders = loaded && user && loaded.userId === user.userId ? loaded.data : null;

  useEffect(() => {
    const requestId = ++requestSeq.current;
    setLoaded(null);
    setError('');
    if (!user || user.role !== 'CUSTOMER') return;
    setLoading(true);
    getOrders(user, page, PAGE_SIZE, status)
      .then((data) => { if (requestSeq.current === requestId) setLoaded({ userId: user.userId, data }); })
      .catch((problem) => { if (requestSeq.current === requestId) setError(problem.message ?? 'Không tải được lịch sử đơn hàng.'); })
      .finally(() => { if (requestSeq.current === requestId) setLoading(false); });
  }, [user, page, status, reload]);

  if (authLoading) return <Spin description="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(pathname + search + hash)}`} replace />;
  if (user.role !== 'CUSTOMER') {
    return <Alert type="info" showIcon title="Cần tài khoản Khách"
      description={<Link to="/login?next=%2Forders">Đăng nhập bằng tài khoản Khách để xem đơn hàng</Link>} />;
  }

  return <div className="sf-tx-page">
    <header className="sf-tx-page__heading">
      <Typography.Title level={1}>Đơn hàng của tôi</Typography.Title>
      <p className="sf-tx-page__lead">Theo dõi trạng thái và xem lại những sản phẩm đã đặt.</p>
    </header>
    <div className="sf-tx-order-filter">
      <label htmlFor="tx-order-status">Trạng thái</label>
      <Select id="tx-order-status" aria-label="Lọc trạng thái đơn" className="sf-tx-order-filter__select"
        value={status} options={STATUS_OPTIONS}
        onChange={(value) => { setStatus(value); setPage(1); }} />
      {orders && <span className="sf-tx-muted" role="status">{orders.total} đơn hàng</span>}
    </div>
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={() => setReload((v) => v + 1)}>Thử lại</Button>} />}
    {loading && <Spin description="Đang tải đơn hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
    {!loading && orders?.items.length === 0 && <Card className="sf-tx-card"><Empty
      description={status ? 'Không có đơn hàng ở trạng thái này' : 'Chưa có đơn hàng'}>
      {status ? <Button onClick={() => { setStatus(''); setPage(1); }}>Xem tất cả đơn</Button>
        : <Link to="/cart">Xem giỏ hàng</Link>}
    </Empty></Card>}
    {!loading && orders?.items.length > 0 && <div className="sf-tx-order-list" aria-label="Danh sách đơn hàng">
      {orders.items.map((order) => <Card className="sf-tx-card" key={order.orderId}>
        <div className="sf-tx-order-row">
          <div className="sf-tx-order-row__top">
            <div className="sf-tx-order-row__identity">
              <strong className="sf-code">{order.orderCode}</strong>
              <span className="sf-tx-muted">{placedAt(order.createdAt)} · {order.itemCount} sản phẩm</span>
            </div>
            <OracleOrderStatus status={order.status} />
          </div>
          <div className="sf-tx-order-row__bottom">
            <Link to={`/orders/${order.orderId}`}>Xem chi tiết đơn</Link>
            <div className="sf-tx-order-row__total">
              <span className="sf-tx-muted">Tổng tiền</span>
              <PriceTag price={order.totalAmount} size="sm" />
            </div>
          </div>
        </div>
      </Card>)}
    </div>}
    {orders && orders.total > PAGE_SIZE && <Pagination current={page} pageSize={PAGE_SIZE} total={orders.total}
      onChange={(next) => setPage(next)} showSizeChanger={false} />}
  </div>;
}
