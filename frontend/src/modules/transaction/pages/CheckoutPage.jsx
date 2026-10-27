import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Form, Input, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, tokens } from '@/shared/design-system';
import { getCheckoutPreview, placeOrder } from '../api';
import { clearPendingCheckout, readPendingCheckout, savePendingCheckout } from '../checkoutPending';
import { readCheckoutSelection, removeCheckoutSelection } from '../checkoutSelection';

const DEFINITELY_NOT_COMMITTED = new Set([
  'CART_EMPTY', 'CART_CHANGED', 'PRODUCT_UNAVAILABLE', 'PRICE_CHANGED', 'OUT_OF_STOCK',
  'VALIDATION_ERROR',
]);

const WARNING_TEXT = {
  CART_EMPTY: 'Giỏ hàng đang trống.',
  PRODUCT_UNAVAILABLE: 'Có sản phẩm đã ngừng bán. Hãy sửa giỏ trước khi đặt hàng.',
  OUT_OF_STOCK: 'Có sản phẩm không đủ tồn kho có thể bán. Hãy sửa giỏ trước khi đặt hàng.',
  CART_CHANGED: 'Sản phẩm đã chọn không còn trong giỏ. Hãy chọn lại trên trang giỏ.',
};

function selectedPreview(data, userId) {
  const selected = readCheckoutSelection(userId);
  const selectedSet = new Set(selected);
  const lines = data.lines.filter((line) => selectedSet.has(line.productId));
  const warnings = [];
  if (selected.some((id) => !lines.some((line) => line.productId === id))) warnings.push('CART_CHANGED');
  if (lines.some((line) => line.status !== 'ACTIVE')) warnings.push('PRODUCT_UNAVAILABLE');
  if (lines.some((line) => line.availableQuantity < line.quantity)) warnings.push('OUT_OF_STOCK');
  return {
    ...data,
    lines,
    warnings,
    cartItemCount: data.lines.length,
    totalAmount: lines.reduce((sum, line) => sum + Number(line.lineTotal), 0),
  };
}

export default function CheckoutPage() {
  const { user, loading: authLoading } = useAuth();
  const [pending, setPending] = useState(readPendingCheckout);
  const pendingRef = useRef(pending);
  const busyRef = useRef(false);
  const [preview, setPreview] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');

  useEffect(() => {
    if (authLoading || !user || user.role !== 'CUSTOMER' || pending || receipt) {
      setPreview(null);
      return undefined;
    }
    let active = true;
    setLoading(true);
    getCheckoutPreview(user)
      .then((data) => { if (active) setPreview(selectedPreview(data, user.userId)); })
      .catch((problem) => { if (active) setError(problem.message ?? 'Không tải được thông tin đặt hàng.'); })
      .finally(() => { if (active) setLoading(false); });
    return () => { active = false; };
  }, [authLoading, user, pending, receipt]);

  const send = async (saved) => {
    if (!user || user.role !== 'CUSTOMER' || user.userId !== saved.principalUserId || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    setError('');
    try {
      const order = await placeOrder(user, saved.idempotencyKey, saved.body);
      try {
        removeCheckoutSelection(user.userId, saved.body.expectedLines.map((line) => line.productId));
      } catch { /* Đơn đã commit; lần mở Cart tiếp theo sẽ đối chiếu lại lựa chọn. */ }
      clearPendingCheckout(saved.idempotencyKey);
      pendingRef.current = null;
      setPending(null);
      setReceipt(order);
    } catch (problem) {
      if (DEFINITELY_NOT_COMMITTED.has(problem.code)) {
        clearPendingCheckout(saved.idempotencyKey);
        pendingRef.current = null;
        setPending(null);
        setPreview(null);
        setError(`${problem.message ?? 'Không thể đặt hàng.'} Thông tin giỏ sẽ được tải lại.`);
      } else {
        setError('Chưa xác định được yêu cầu đã hoàn tất hay chưa. Hãy gửi lại đúng yêu cầu đang chờ; hệ thống sẽ không tạo đơn trùng.');
      }
    } finally {
      busyRef.current = false;
      setBusy(false);
    }
  };

  const confirm = async (values) => {
    if (!preview || preview.lines.length === 0 || preview.warnings.length > 0
        || pendingRef.current || busyRef.current) return;
    const body = {
      cartVersion: preview.cartVersion,
      recipientName: values.recipientName,
      recipientPhone: values.recipientPhone,
      shippingAddress: values.shippingAddress,
      expectedLines: preview.lines.map((line) => ({
        productId: line.productId,
        quantity: line.quantity,
        unitPrice: String(line.unitPrice),
      })),
    };
    const saved = { principalUserId: user.userId, idempotencyKey: crypto.randomUUID(), body };
    try {
      savePendingCheckout(saved); // Ghi trước khi gửi, kể cả khi yêu cầu chưa nhận được phản hồi.
      pendingRef.current = saved;
      setPending(saved);
      await send(saved);
    } catch (problem) {
      setError(problem.message ?? 'Không lưu được yêu cầu đặt hàng.');
    }
  };

  if (authLoading) return <Spin description="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user || user.role !== 'CUSTOMER') {
    return <div className="sf-tx-page">
      <Alert type="info" showIcon title="Cần tài khoản Khách"
        description={<Link to="/login?next=%2Fcheckout">Đăng nhập bằng tài khoản Khách để đặt hàng</Link>} />
      {pending && <Alert type="warning" showIcon title="Có yêu cầu đặt hàng đang chờ"
        description="Hãy đăng nhập đúng tài khoản đã đặt hàng để xác định kết quả. Yêu cầu này vẫn được giữ trong tab." />}
    </div>;
  }

  const wrongCustomer = pending && pending.principalUserId !== user.userId;
  return <div className="sf-tx-page">
    <header className="sf-tx-page__heading">
      <Typography.Title level={1}>Xác nhận đơn hàng</Typography.Title>
      <p className="sf-tx-page__lead">Kiểm tra sản phẩm và thông tin nhận hàng trước khi đặt đơn.</p>
    </header>
    {error && <Alert type="error" showIcon title={error} />}
    {receipt && <Card className="sf-tx-card">
      <div className="sf-tx-stack">
        <h2 className="sf-tx-card__heading">Đặt hàng thành công</h2>
        <span className="sf-tx-muted">Mã đơn <span className="sf-code">{receipt.orderCode}</span> · Đang chờ xử lý</span>
        <div className="sf-tx-summary__total"><strong>Tổng tiền</strong><PriceTag price={receipt.totalAmount} /></div>
        <Link to={`/orders/${receipt.orderId}`}>Xem chi tiết đơn hàng</Link>
      </div>
    </Card>}
    {wrongCustomer && <Alert type="warning" showIcon title="Yêu cầu thuộc tài khoản khác"
      description="Đăng nhập đúng tài khoản đã xác nhận đơn để gửi lại. Dữ liệu đang chờ vẫn được giữ trong tab." />}
    {pending && !wrongCustomer && !receipt && <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Yêu cầu đặt hàng đang chờ</h2>}>
      <div className="sf-tx-stack">
        <Alert type="warning" showIcon title="Chưa rõ đơn đã được tạo hay chưa"
          description="Gửi lại yêu cầu đã lưu để kiểm tra kết quả. Hệ thống sẽ không tạo đơn trùng." />
        <div><strong>Người nhận:</strong> {pending.body.recipientName}</div>
        <div><strong>Địa chỉ:</strong> {pending.body.shippingAddress}</div>
        <details className="sf-tx-request-details">
          <summary>Xem mã yêu cầu</summary>
          <span className="sf-code">{pending.idempotencyKey}</span>
        </details>
        <Button type="primary" loading={busy} onClick={() => send(pending)}>Kiểm tra và gửi lại yêu cầu</Button>
      </div>
    </Card>}
    {!pending && !receipt && loading && <Spin description="Đang tải thông tin đặt hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
    {!pending && !receipt && !loading && !preview && <Button onClick={() => window.location.reload()}>Thử tải lại</Button>}
    {!pending && !receipt && preview && preview.lines.length === 0 &&
      <Card className="sf-tx-card"><Empty description={preview.cartItemCount === 0 ? 'Giỏ hàng đang trống' : 'Chưa chọn sản phẩm để đặt hàng'}>
        <Link to="/cart">Quay lại giỏ hàng để chọn sản phẩm</Link>
      </Empty></Card>}
    {!pending && !receipt && preview && preview.lines.length > 0 && <>
      {preview.warnings.map((warning) => <Alert key={warning} type="warning" showIcon title={WARNING_TEXT[warning] ?? warning} />)}
      <div className="sf-tx-grid sf-tx-checkout-grid">
        <aside className="sf-tx-grid__aside" aria-label="Tóm tắt đơn hàng">
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Sản phẩm đã chọn ({preview.lines.length})</h2>}>
            <div className="sf-tx-summary">
              <div className="sf-tx-receipt-lines">
                {preview.lines.map((line) => <div className="sf-tx-receipt-line" key={line.productId}>
                  <div className="sf-tx-receipt-line__identity">
                    <strong>{line.name}</strong>
                    <span className="sf-code">{line.sku} · Số lượng {line.quantity}</span>
                    <span className="sf-tx-muted">Có thể bán: {line.availableQuantity}</span>
                  </div>
                  <PriceTag price={line.lineTotal} size="sm" />
                </div>)}
              </div>
              <div className="sf-tx-summary__total"><strong>Tổng tiền</strong><PriceTag price={preview.totalAmount} /></div>
              <span className="sf-tx-muted">Giá và tồn kho được kiểm tra lại khi xác nhận.</span>
              <Link to="/cart">Sửa lựa chọn trong giỏ</Link>
            </div>
          </Card>
        </aside>
        <div className="sf-tx-grid__main">
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Thông tin nhận hàng</h2>}>
            <Form className="sf-tx-checkout-form" layout="vertical" requiredMark={false}
              scrollToFirstError={{ focus: true }}
              onFinish={confirm} initialValues={{ recipientName: user.fullName }}>
              <Form.Item name="recipientName" label="Họ tên người nhận"
                rules={[{ required: true, whitespace: true, message: 'Nhập họ tên người nhận.' }]}>
                <Input maxLength={150} autoComplete="name" />
              </Form.Item>
              <Form.Item name="recipientPhone" label="Số điện thoại"
                rules={[{ required: true, whitespace: true, message: 'Nhập số điện thoại.' }]}>
                <Input maxLength={30} autoComplete="tel" />
              </Form.Item>
              <Form.Item name="shippingAddress" label="Địa chỉ giao hàng"
                rules={[{ required: true, whitespace: true, message: 'Nhập địa chỉ giao hàng.' }]}>
                <Input.TextArea maxLength={500} rows={3} />
              </Form.Item>
              <Button type="primary" htmlType="submit" loading={busy} disabled={preview.warnings.length > 0}>
                Xác nhận đặt hàng
              </Button>
            </Form>
          </Card>
        </div>
      </div>
    </>}
  </div>;
}
