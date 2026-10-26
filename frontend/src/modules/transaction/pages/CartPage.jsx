import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Empty, Flex, InputNumber, Popconfirm, Select, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, tokens } from '@/shared/design-system';
import { addCartItem, clearCart, getCart, removeCartItem, setCartItemQuantity } from '../api';

const DEV_PRODUCTS = [
  { value: 1, label: 'ID 1 · Core Product' },
  { value: 101, label: 'ID 101 · Tai nghe Bluetooth' },
  { value: 102, label: 'ID 102 · Bàn phím cơ' },
  { value: 103, label: 'ID 103 · Chuột không dây' },
  { value: 104, label: 'ID 104 · Màn hình 24 inch' },
  { value: 105, label: 'ID 105 · Sạc dự phòng (hết hàng)' },
  { value: 106, label: 'ID 106 · Loa Bluetooth (ngừng bán)' },
];

export default function CartPage() {
  const { user, loading: authLoading } = useAuth();
  const [cart, setCart] = useState(null);
  const [draft, setDraft] = useState({});
  const [newProductId, setNewProductId] = useState(null);
  const [newQuantity, setNewQuantity] = useState(1);
  const [loading, setLoading] = useState(false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestSeq = useRef(0);

  const refresh = useCallback(async () => {
    const requestId = ++requestSeq.current;
    setCart(null);
    setLoading(false);
    setBusy(false);
    setError('');
    setNotice('');
    if (!user || user.role !== 'CUSTOMER') return;
    setLoading(true);
    try {
      const result = await getCart(user);
      if (requestSeq.current !== requestId) return;
      setCart(result);
      setDraft({});
      setError('');
    } catch (problem) {
      if (requestSeq.current === requestId) setError(problem.message ?? 'Không tải được giỏ hàng.');
    } finally {
      if (requestSeq.current === requestId) setLoading(false);
    }
  }, [user]);

  useEffect(() => { refresh(); }, [refresh]);

  const mutate = async (request, successMessage) => {
    const requestId = requestSeq.current;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await request();
      if (requestSeq.current !== requestId) return;
      setCart(result);
      setDraft({});
      setNotice(successMessage);
    } catch (problem) {
      if (requestSeq.current !== requestId) return;
      if (['CART_CHANGED', 'LOCK_TIMEOUT', 'DEADLOCK', 'TIMEOUT', 'NETWORK_ERROR'].includes(problem.code)) {
        try {
          const latest = await getCart(user);
          if (requestSeq.current !== requestId) return;
          setCart(latest);
          setDraft({});
        } catch { /* giữ lỗi gốc */ }
        setError('Trạng thái thao tác chưa chắc chắn hoặc giỏ đã đổi. Đã tải lại giỏ nếu có thể; hãy kiểm tra trước khi thao tác tiếp.');
      } else {
        setError(problem.message ?? 'Không sửa được giỏ hàng.');
      }
    } finally {
      if (requestSeq.current === requestId) setBusy(false);
    }
  };

  if (authLoading) return <Spin tip="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user || user.role !== 'CUSTOMER') {
    return <Alert type="info" showIcon title="Cần tài khoản Khách" description={<Link to="/login?next=%2Fcart">Đăng nhập bằng tài khoản Khách để xem giỏ hàng</Link>} />;
  }

  return (
    <div style={{ display: 'grid', gap: tokens.space[4], minWidth: 0, overflowWrap: 'anywhere' }}>
      <Typography.Title level={2} style={{ margin: 0 }}>Giỏ hàng</Typography.Title>
      <Typography.Text type="secondary">Giá và tồn kho sẽ được kiểm tra lại khi đặt hàng.</Typography.Text>
      <Typography.Text type="secondary">Đang dùng giỏ của {user.email}.</Typography.Text>
      {error && <Alert type="error" showIcon title={error} />}
      {notice && <Alert type="success" showIcon title={notice} />}
      {loading && <Spin tip="Đang tải giỏ hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
      {!loading && !cart && <Button onClick={refresh}>Thử tải lại</Button>}
      {cart && <Card title="Thêm sản phẩm vào giỏ">
        <Flex vertical gap={tokens.space[2]}>
          <Typography.Text type="secondary">Nhập ID sản phẩm đã có trong Oracle. Thao tác này chỉ đổi giỏ; tồn kho được kiểm tra và trừ khi đặt hàng.</Typography.Text>
          {import.meta.env.DEV && <Flex wrap gap={tokens.space[2]} align="center">
            <Typography.Text type="secondary">Chọn nhanh sản phẩm mẫu khi backend chạy profile dev:</Typography.Text>
            <Select aria-label="Chọn sản phẩm mẫu" placeholder="Chọn sản phẩm mẫu" showSearch optionFilterProp="label"
              options={DEV_PRODUCTS} value={newProductId ?? undefined} disabled={busy}
              onChange={setNewProductId} style={{ flex: '1 1 16rem', minWidth: 0 }} />
          </Flex>}
          {import.meta.env.DEV && <Typography.Text type="secondary">Phiên bản giỏ hiện tại: {cart.version}</Typography.Text>}
          <Flex wrap gap={tokens.space[2]} align="center">
            <InputNumber aria-label="ID sản phẩm cần thêm" placeholder="ID sản phẩm" min={1} precision={0}
              value={newProductId} disabled={busy} onChange={setNewProductId}
              style={{ flex: '1 1 10rem', minWidth: 0 }} />
            <InputNumber aria-label="Số lượng cần thêm" min={1} max={999} precision={0}
              value={newQuantity} disabled={busy} onChange={setNewQuantity}
              style={{ flex: '1 1 8rem', minWidth: 0 }} />
            <Button type="primary" loading={busy}
              disabled={!Number.isSafeInteger(newProductId) || newProductId <= 0 || !Number.isInteger(newQuantity) || newQuantity < 1 || newQuantity > 999}
              onClick={() => mutate(() => addCartItem(user, cart.version, newProductId, newQuantity), 'Đã thêm sản phẩm vào giỏ.')}>
              Thêm vào giỏ
            </Button>
          </Flex>
        </Flex>
      </Card>}
      {cart && cart.items.length === 0 && <Card><Empty description="Giỏ hàng đang trống. Nhập ID sản phẩm ở trên để thêm hàng." /></Card>}
      {cart && cart.items.length > 0 && (
        <>
          <Flex vertical gap={tokens.space[3]}>
            {cart.items.map((item) => {
              const quantity = Object.prototype.hasOwnProperty.call(draft, item.productId)
                ? draft[item.productId] : item.quantity;
              const valid = Number.isInteger(quantity) && quantity >= 1 && quantity <= 999;
              return (
                <Card key={item.productId}>
                  <Flex vertical gap={tokens.space[2]}>
                    <Typography.Text strong>{item.name}</Typography.Text>
                    <Typography.Text type="secondary" className="sf-code">{item.sku} · ID {item.productId}</Typography.Text>
                    <Flex wrap gap={tokens.space[3]} align="center" justify="space-between">
                      <PriceTag price={item.unitPrice} size="sm" />
                      <Flex wrap gap={tokens.space[2]} align="center">
                        <InputNumber aria-label={`Số lượng ${item.name}`} min={1} max={999} precision={0}
                          value={quantity} disabled={busy}
                          onChange={(value) => setDraft((previous) => ({ ...previous, [item.productId]: value }))} />
                        <Button disabled={busy || !valid || quantity === item.quantity}
                          onClick={() => mutate(() => setCartItemQuantity(user, cart.version, item.productId, quantity), 'Đã cập nhật số lượng.')}>
                          Cập nhật
                        </Button>
                        <Popconfirm title="Xóa sản phẩm khỏi giỏ?" okText="Xóa" cancelText="Giữ lại"
                          onConfirm={() => mutate(() => removeCartItem(user, cart.version, item.productId), 'Đã xóa sản phẩm.')}>
                          <Button danger disabled={busy}>Xóa</Button>
                        </Popconfirm>
                      </Flex>
                    </Flex>
                    <Typography.Text>Thành tiền tham khảo: <PriceTag price={item.lineTotal} size="sm" /></Typography.Text>
                  </Flex>
                </Card>
              );
            })}
          </Flex>
          <Card>
            <Flex wrap gap={tokens.space[3]} align="center" justify="space-between">
              <Typography.Text strong>Tổng tham khảo: <PriceTag price={cart.totalAmount} /></Typography.Text>
              <Flex wrap gap={tokens.space[2]}>
                <Popconfirm title="Xóa toàn bộ sản phẩm trong giỏ?" okText="Xóa giỏ" cancelText="Giữ lại"
                  onConfirm={() => mutate(() => clearCart(user, cart.version), 'Đã xóa toàn bộ giỏ.')}>
                  <Button danger disabled={busy}>Xóa toàn bộ</Button>
                </Popconfirm>
                <Link to="/checkout"><Button type="primary" disabled={busy}>Thanh toán</Button></Link>
              </Flex>
            </Flex>
          </Card>
        </>
      )}
    </div>
  );
}
