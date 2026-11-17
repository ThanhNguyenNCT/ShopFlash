import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Popconfirm, Spin, Typography } from 'antd';
import { Link, Navigate, useLocation, useParams } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, formatVND, tokens } from '@/shared/design-system';
import { cancelOrder, getOrderDetail } from '../api';
import OracleOrderStatus from '../components/OracleOrderStatus';
import ProductThumbnail from '../components/ProductThumbnail';

function orderTime(value) {
  return new Intl.DateTimeFormat('vi-VN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'Asia/Ho_Chi_Minh' })
    .format(new Date(value));
}

export default function OrderDetailPage() {
  const { orderId } = useParams();
  const { pathname, search, hash } = useLocation();
  const { user, loading: authLoading } = useAuth();
  const [loaded, setLoaded] = useState(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestSeq = useRef(0);
  const order = loaded && user && loaded.userId === user.userId ? loaded.data : null;

  const refresh = useCallback(async () => {
    const requestId = ++requestSeq.current;
    setLoaded(null);
    setError('');
    if (!user || user.role !== 'CUSTOMER') return;
    setLoading(true);
    try {
      const data = await getOrderDetail(user, orderId);
      if (requestSeq.current === requestId) setLoaded({ userId: user.userId, data });
    } catch (problem) {
      if (requestSeq.current === requestId) setError(problem.message ?? 'Không tải được đơn hàng.');
    } finally {
      if (requestSeq.current === requestId) setLoading(false);
    }
  }, [user, orderId]);

  useEffect(() => { refresh(); }, [refresh]);

  const cancel = async () => {
    if (busy || !order || order.orderType !== 'NORMAL' || order.status !== 'PENDING') return;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const updated = await cancelOrder(user, orderId);
      setLoaded({ userId: user.userId, data: updated });
      setNotice('Đã hủy đơn và hoàn tồn kho.');
    } catch (problem) {
      const message = problem.code === 'ORDER_NOT_CANCELLABLE'
        ? 'Đơn không còn đủ điều kiện hủy. Đã tải lại trạng thái mới nhất.'
        : problem.code === 'TIMEOUT' || problem.code === 'NETWORK_ERROR'
          ? 'Chưa rõ yêu cầu hủy đã hoàn tất hay chưa. Đã tải lại đơn để kiểm tra.'
          : problem.message ?? 'Không hủy được đơn hàng.';
      try {
        const latest = await getOrderDetail(user, orderId);
        setLoaded({ userId: user.userId, data: latest });
        if (latest.status === 'CANCELLED' && (problem.code === 'TIMEOUT' || problem.code === 'NETWORK_ERROR')) {
          setNotice('Đơn đã được hủy và tồn kho đã được hoàn.');
        } else {
          setError(message);
        }
      } catch {
        setError(`${message} Hãy tải lại trang để kiểm tra trước khi thao tác tiếp.`);
      }
    } finally {
      setBusy(false);
    }
  };

  if (authLoading) return <Spin description="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user) return <Navigate to={`/login?next=${encodeURIComponent(pathname + search + hash)}`} replace />;
  if (user.role !== 'CUSTOMER') {
    return <Alert type="info" showIcon title="Cần tài khoản Khách"
      description={<Link to={`/login?next=${encodeURIComponent(`/orders/${orderId}`)}`}>Đăng nhập bằng tài khoản Khách để xem đơn hàng</Link>} />;
  }

  return <div className="sf-tx-page">
    <header className="sf-tx-page__heading">
      <Link to="/orders">← Đơn hàng của tôi</Link>
      <Typography.Title level={1}>Chi tiết đơn hàng</Typography.Title>
    </header>
    {error && <Alert type="error" showIcon title={error} action={<Button onClick={refresh}>Tải lại</Button>} />}
    {notice && <Alert type="success" showIcon title={notice} />}
    {loading && <Spin description="Đang tải đơn hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
    {!loading && !order && !error && <Empty description="Không có đơn hàng" />}
    {order && <>
      <Card className="sf-tx-card">
        <div className="sf-tx-order-overview">
          <div className="sf-tx-stack">
            <span className="sf-tx-muted">Mã đơn hàng</span>
            <strong className="sf-code">{order.orderCode}</strong>
            <span className="sf-tx-muted">Đặt lúc {orderTime(order.createdAt)}</span>
          </div>
          <OracleOrderStatus status={order.status} />
        </div>
      </Card>
      <div className="sf-tx-grid sf-tx-order-detail-grid">
        <div className="sf-tx-grid__main">
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Sản phẩm lúc đặt</h2>}>
            <div className="sf-tx-summary">
              <div className="sf-tx-receipt-lines">
                {order.items.map((item) => <div key={item.orderItemId} className="sf-tx-receipt-line">
                  <div className="sf-tx-product-identity">
                    <ProductThumbnail thumbnailUrl={item.thumbnailUrl} name={item.productName} />
                    <div className="sf-tx-receipt-line__identity">
                      <strong>{item.productName}</strong>
                      <span className="sf-code">{item.productSku}</span>
                      <span className="sf-tx-muted">{item.quantity} × {formatVND(item.unitPrice)}</span>
                    </div>
                  </div>
                  <PriceTag price={item.lineTotal} size="sm" />
                </div>)}
              </div>
              <div className="sf-tx-summary__total"><strong>Tổng tiền</strong><PriceTag price={order.totalAmount} /></div>
            </div>
          </Card>
        </div>
        <aside className="sf-tx-grid__aside" aria-label="Thông tin đơn hàng">
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Thông tin nhận hàng</h2>}>
            <div className="sf-tx-stack">
              <dl className="sf-tx-order-meta">
                <div><dt>Người nhận</dt><dd>{order.recipientName}</dd></div>
                <div><dt>Số điện thoại</dt><dd>{order.recipientPhone}</dd></div>
                <div className="sf-tx-order-meta__wide"><dt>Địa chỉ</dt><dd>{order.shippingAddress}</dd></div>
                <div><dt>Loại đơn</dt><dd>{order.orderType === 'NORMAL' ? 'Đơn thông thường' : 'Flash Sale'}</dd></div>
                {order.cancelledAt && <div><dt>Ngày hủy</dt><dd>{orderTime(order.cancelledAt)}</dd></div>}
              </dl>
              {order.orderType === 'NORMAL' && order.status === 'PENDING' && <Popconfirm
                title="Hủy đơn hàng này?" description="Tồn kho sẽ được hoàn sau khi hủy thành công."
                okText="Hủy đơn" cancelText="Giữ đơn" onConfirm={cancel}>
                <Button danger loading={busy}>Hủy đơn</Button>
              </Popconfirm>}
            </div>
          </Card>
        </aside>
      </div>
    </>}
  </div>;
}
