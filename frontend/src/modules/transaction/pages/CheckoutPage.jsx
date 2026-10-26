import { useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Flex, Form, Input, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, tokens } from '@/shared/design-system';
import { getCheckoutPreview, placeOrder } from '../api';
import { clearPendingCheckout, readPendingCheckout, savePendingCheckout } from '../checkoutPending';

const DEFINITELY_NOT_COMMITTED = new Set([
  'CART_EMPTY', 'CART_CHANGED', 'PRODUCT_UNAVAILABLE', 'PRICE_CHANGED', 'OUT_OF_STOCK',
  'VALIDATION_ERROR',
]);

const WARNING_TEXT = {
  CART_EMPTY: 'Giỏ hàng đang trống.',
  PRODUCT_UNAVAILABLE: 'Có sản phẩm đã ngừng bán. Hãy sửa giỏ trước khi đặt hàng.',
  OUT_OF_STOCK: 'Có sản phẩm không đủ tồn kho có thể bán. Hãy sửa giỏ trước khi đặt hàng.',
};

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
      .then((data) => { if (active) setPreview(data); })
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

  if (authLoading) return <Spin tip="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user || user.role !== 'CUSTOMER') {
    return <div style={{ display: 'grid', gap: tokens.space[3] }}>
      <Alert type="info" showIcon title="Cần tài khoản Khách"
        description={<Link to="/login?next=%2Fcheckout">Đăng nhập bằng tài khoản Khách để đặt hàng</Link>} />
      {pending && <Alert type="warning" showIcon title="Có yêu cầu đặt hàng đang chờ"
        description="Hãy đăng nhập đúng tài khoản đã đặt hàng để xác định kết quả. Yêu cầu này vẫn được giữ trong tab." />}
    </div>;
  }

  const wrongCustomer = pending && pending.principalUserId !== user.userId;
  return <div style={{ display: 'grid', gap: tokens.space[4], minWidth: 0, overflowWrap: 'anywhere' }}>
    <Typography.Title level={2} style={{ margin: 0 }}>Thanh toán</Typography.Title>
    <Typography.Text type="secondary">Giá và tồn kho được kiểm tra lại khi bạn xác nhận đơn.</Typography.Text>
    {error && <Alert type="error" showIcon title={error} />}
    {receipt && <Card title="Đặt hàng thành công">
      <Flex vertical gap={tokens.space[2]}>
        <Typography.Text>Mã đơn: <span className="sf-code">{receipt.orderCode}</span></Typography.Text>
        <Typography.Text>Tổng tiền: <PriceTag price={receipt.totalAmount} /></Typography.Text>
        <Typography.Text type="secondary">Đơn đang ở trạng thái chờ xử lý.</Typography.Text>
        <Link to={`/orders/${receipt.orderId}`}>Xem chi tiết đơn hàng</Link>
      </Flex>
    </Card>}
    {wrongCustomer && <Alert type="warning" showIcon title="Yêu cầu thuộc tài khoản khác"
      description="Đăng nhập đúng tài khoản đã xác nhận đơn để gửi lại. Dữ liệu đang chờ vẫn được giữ trong tab." />}
    {pending && !wrongCustomer && !receipt && <Card title="Yêu cầu đặt hàng đang chờ">
      <Flex vertical gap={tokens.space[3]}>
        <Typography.Text>Khóa yêu cầu: <span className="sf-code">{pending.idempotencyKey}</span></Typography.Text>
        <Typography.Text>Người nhận: {pending.body.recipientName}</Typography.Text>
        <Typography.Text>Địa chỉ: {pending.body.shippingAddress}</Typography.Text>
        <Typography.Text type="secondary">Khi gửi lại, trang dùng nguyên khóa và nội dung đã lưu; không lấy giá mới từ giỏ.</Typography.Text>
        <Button type="primary" loading={busy} onClick={() => send(pending)}>Kiểm tra và gửi lại yêu cầu</Button>
      </Flex>
    </Card>}
    {!pending && !receipt && loading && <Spin tip="Đang tải thông tin đặt hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
    {!pending && !receipt && !loading && !preview && <Button onClick={() => window.location.reload()}>Thử tải lại</Button>}
    {!pending && !receipt && preview && preview.lines.length === 0 &&
      <Card><Empty description="Giỏ hàng đang trống"><Link to="/cart">Quay lại giỏ hàng</Link></Empty></Card>}
    {!pending && !receipt && preview && preview.lines.length > 0 && <>
      {preview.warnings.map((warning) => <Alert key={warning} type="warning" showIcon title={WARNING_TEXT[warning] ?? warning} />)}
      <Card title="Sản phẩm đã chọn">
        <Flex vertical gap={tokens.space[3]}>
          {preview.lines.map((line) => <Flex key={line.productId} justify="space-between" wrap gap={tokens.space[2]}>
            <div>
              <Typography.Text strong>{line.name}</Typography.Text>
              <Typography.Text type="secondary"> · {line.sku} · Số lượng {line.quantity}</Typography.Text>
              <div><Typography.Text type="secondary">Có thể bán: {line.availableQuantity}</Typography.Text></div>
            </div>
            <PriceTag price={line.lineTotal} size="sm" />
          </Flex>)}
          <Typography.Text strong>Tổng tiền: <PriceTag price={preview.totalAmount} /></Typography.Text>
        </Flex>
      </Card>
      <Card title="Thông tin nhận hàng">
        <Form layout="vertical" requiredMark={false} onFinish={confirm} initialValues={{ recipientName: user.fullName }}>
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
            Đặt hàng
          </Button>
        </Form>
      </Card>
    </>}
  </div>;
}
