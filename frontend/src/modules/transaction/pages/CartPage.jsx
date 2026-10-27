import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, Button, Card, Checkbox, Empty, InputNumber, Popconfirm, Select, Spin, Typography } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { PriceTag, formatVND, tokens } from '@/shared/design-system';
import { addCartItem, clearCart, getCart, removeCartItem, setCartItemQuantity } from '../api';
import { readCheckoutSelection, saveCheckoutSelection } from '../checkoutSelection';

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
  const [saving, setSaving] = useState(false);
  const [selectedIds, setSelectedIds] = useState([]);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const requestSeq = useRef(0);
  const cartRef = useRef(null);
  const desiredRef = useRef({});
  const flushingRef = useRef(null);
  const timerRef = useRef(null);

  const reconcileSelection = (nextCart, customerId) => {
    const available = new Set(nextCart.items.map((item) => item.productId));
    const selected = readCheckoutSelection(customerId).filter((id) => available.has(id));
    saveCheckoutSelection(customerId, selected);
    setSelectedIds(selected);
  };

  const refresh = useCallback(async () => {
    const requestId = ++requestSeq.current;
    clearTimeout(timerRef.current);
    desiredRef.current = {};
    cartRef.current = null;
    setCart(null);
    setDraft({});
    setSelectedIds([]);
    setLoading(false);
    setBusy(false);
    setSaving(false);
    setError('');
    setNotice('');
    if (!user || user.role !== 'CUSTOMER') return;
    setLoading(true);
    try {
      const result = await getCart(user);
      if (requestSeq.current !== requestId) return;
      cartRef.current = result;
      setCart(result);
      setDraft({});
      reconcileSelection(result, user.userId);
      setError('');
    } catch (problem) {
      if (requestSeq.current === requestId) setError(problem.message ?? 'Không tải được giỏ hàng.');
    } finally {
      if (requestSeq.current === requestId) setLoading(false);
    }
  }, [user]);

  useEffect(() => { refresh(); }, [refresh]);

  const flushQuantity = async () => {
    const requestId = requestSeq.current;
    if (flushingRef.current === requestId) return;
    flushingRef.current = requestId;
    try {
      while (requestSeq.current === requestId) {
        const current = cartRef.current;
        const next = Object.entries(desiredRef.current).find(([id, quantity]) =>
          current?.items.some((item) => item.productId === Number(id) && item.quantity !== quantity));
        if (!next) break;
        const [id, quantity] = next;
        const result = await setCartItemQuantity(user, current.version, Number(id), quantity);
        if (requestSeq.current !== requestId) return;
        cartRef.current = result;
        setCart(result);
        if (desiredRef.current[id] === quantity) {
          delete desiredRef.current[id];
          setDraft((previous) => {
            if (previous[id] !== quantity) return previous;
            const updated = { ...previous };
            delete updated[id];
            return updated;
          });
        }
      }
      // Người dùng có thể đã quay lại đúng số lượng vừa lưu trong lúc request đang chạy.
      for (const [id, quantity] of Object.entries(desiredRef.current)) {
        if (cartRef.current?.items.some((item) => item.productId === Number(id) && item.quantity === quantity)) {
          delete desiredRef.current[id];
          setDraft((previous) => {
            if (previous[id] !== quantity) return previous;
            const updated = { ...previous };
            delete updated[id];
            return updated;
          });
        }
      }
    } catch (problem) {
      if (requestSeq.current !== requestId) return;
      desiredRef.current = {};
      setDraft({});
      try {
        const latest = await getCart(user);
        if (requestSeq.current !== requestId) return;
        cartRef.current = latest;
        setCart(latest);
        reconcileSelection(latest, user.userId);
      } catch { /* Giữ lỗi gốc; lần tải lại sau sẽ lấy trạng thái thực tế. */ }
      setError(['CART_CHANGED', 'LOCK_TIMEOUT', 'DEADLOCK', 'TIMEOUT', 'NETWORK_ERROR'].includes(problem.code)
        ? 'Chưa xác định được số lượng đã lưu hoặc giỏ đã đổi. Hãy kiểm tra giỏ vừa tải lại trước khi thao tác tiếp.'
        : (problem.message ?? 'Không cập nhật được số lượng.'));
    } finally {
      if (flushingRef.current === requestId) flushingRef.current = null;
      if (requestSeq.current === requestId) {
        setSaving(false);
        // Một thay đổi mới có thể đến ngay sau lần kiểm tra cuối của vòng lặp.
        if (Object.keys(desiredRef.current).length > 0) {
          timerRef.current = setTimeout(flushQuantity, 180);
          setSaving(true);
        }
      }
    }
  };

  const changeQuantity = (productId, quantity) => {
    if (!Number.isInteger(quantity) || quantity < 1 || quantity > 999) return;
    setError('');
    setNotice('');
    desiredRef.current[productId] = quantity;
    setDraft((previous) => ({ ...previous, [productId]: quantity }));
    setSaving(true);
    clearTimeout(timerRef.current);
    timerRef.current = setTimeout(flushQuantity, 180);
  };

  const changeSelection = (productId, checked) => {
    const next = checked ? [...selectedIds, productId] : selectedIds.filter((id) => id !== productId);
    try {
      saveCheckoutSelection(user.userId, next);
      setSelectedIds(next);
      setError('');
    } catch {
      setError('Không lưu được lựa chọn sản phẩm trong tab này. Hãy kiểm tra bộ nhớ trình duyệt.');
    }
  };

  const mutate = async (request, successMessage) => {
    const requestId = requestSeq.current;
    setBusy(true);
    setError('');
    setNotice('');
    try {
      const result = await request();
      if (requestSeq.current !== requestId) return;
      cartRef.current = result;
      setCart(result);
      setDraft({});
      reconcileSelection(result, user.userId);
      setNotice(successMessage);
    } catch (problem) {
      if (requestSeq.current !== requestId) return;
      if (['CART_CHANGED', 'LOCK_TIMEOUT', 'DEADLOCK', 'TIMEOUT', 'NETWORK_ERROR'].includes(problem.code)) {
        try {
          const latest = await getCart(user);
          if (requestSeq.current !== requestId) return;
          cartRef.current = latest;
          setCart(latest);
          setDraft({});
          reconcileSelection(latest, user.userId);
        } catch { /* giữ lỗi gốc */ }
        setError('Trạng thái thao tác chưa chắc chắn hoặc giỏ đã đổi. Đã tải lại giỏ nếu có thể; hãy kiểm tra trước khi thao tác tiếp.');
      } else {
        setError(problem.message ?? 'Không sửa được giỏ hàng.');
      }
    } finally {
      if (requestSeq.current === requestId) setBusy(false);
    }
  };

  if (authLoading) return <Spin description="Đang kiểm tra phiên đăng nhập"><div style={{ minHeight: tokens.space[8] }} /></Spin>;
  if (!user || user.role !== 'CUSTOMER') {
    return <Alert type="info" showIcon title="Cần tài khoản Khách" description={<Link to="/login?next=%2Fcart">Đăng nhập bằng tài khoản Khách để xem giỏ hàng</Link>} />;
  }

  const cartTotal = cart?.items.reduce((sum, item) =>
    sum + Number(item.unitPrice) * (draft[item.productId] ?? item.quantity), 0) ?? 0;
  const selectedTotal = cart?.items.reduce((sum, item) =>
    sum + (selectedIds.includes(item.productId)
      ? Number(item.unitPrice) * (draft[item.productId] ?? item.quantity) : 0), 0) ?? 0;

  return (
    <div className="sf-tx-page">
      <header className="sf-tx-page__heading">
        <Typography.Title level={1}>Giỏ hàng</Typography.Title>
        <p className="sf-tx-page__lead">Chọn sản phẩm muốn đặt. Giá và tồn kho sẽ được kiểm tra lại khi xác nhận đơn.</p>
      </header>
      {error && <Alert type="error" showIcon title={error} />}
      {notice && <Alert type="success" showIcon title={notice} />}
      {loading && <Spin description="Đang tải giỏ hàng"><div style={{ minHeight: tokens.space[8] }} /></Spin>}
      {!loading && !cart && <Button onClick={refresh}>Thử tải lại</Button>}
      {cart && <div className="sf-tx-grid">
        <div className="sf-tx-grid__main">
          {cart.items.length === 0 ? <Card className="sf-tx-card"><Empty description="Giỏ hàng đang trống. Thêm sản phẩm để bắt đầu đặt hàng." /></Card>
            : <section className="sf-tx-stack" aria-label="Sản phẩm trong giỏ">
              {cart.items.map((item) => {
                const quantity = Object.prototype.hasOwnProperty.call(draft, item.productId)
                  ? draft[item.productId] : item.quantity;
                const selected = selectedIds.includes(item.productId);
                return <Card key={item.productId}
                  className={`sf-tx-cart-item${selected ? ' sf-tx-cart-item--selected' : ''}`}>
                  <div className="sf-tx-cart-item__body">
                    <div className="sf-tx-cart-item__top">
                      <div className="sf-tx-cart-item__identity">
                        <Checkbox checked={selected}
                          onChange={(event) => changeSelection(item.productId, event.target.checked)}>
                          <Typography.Text strong>{item.name}</Typography.Text>
                        </Checkbox>
                        <span className="sf-code">{item.sku} · ID {item.productId}</span>
                      </div>
                      <span className="sf-tx-unit-price sf-num">{formatVND(item.unitPrice)} / sản phẩm</span>
                    </div>
                    <div className="sf-tx-cart-item__bottom">
                      <div className="sf-tx-cart-item__controls">
                        <div className="sf-tx-stepper">
                          <Button aria-label={`Giảm số lượng ${item.name}`} disabled={busy || quantity <= 1}
                            onClick={() => changeQuantity(item.productId, quantity - 1)}>−</Button>
                          <InputNumber aria-label={`Số lượng ${item.name}`} min={1} max={999} precision={0}
                            controls={false} value={quantity} disabled={busy}
                            onChange={(value) => changeQuantity(item.productId, value)} />
                          <Button aria-label={`Tăng số lượng ${item.name}`} disabled={busy || quantity >= 999}
                            onClick={() => changeQuantity(item.productId, quantity + 1)}>+</Button>
                        </div>
                        <Popconfirm title="Xóa sản phẩm khỏi giỏ?" okText="Xóa" cancelText="Giữ lại"
                          onConfirm={() => mutate(() => removeCartItem(user, cart.version, item.productId), 'Đã xóa sản phẩm.')}>
                          <Button danger disabled={busy || saving}>Xóa</Button>
                        </Popconfirm>
                      </div>
                      <div className="sf-tx-cart-item__line-total">
                        <div className="sf-tx-muted">Thành tiền tham khảo</div>
                        <PriceTag price={Number(item.unitPrice) * quantity} size="sm" />
                      </div>
                    </div>
                  </div>
                </Card>;
              })}
            </section>}
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Thêm sản phẩm</h2>}>
            <div className="sf-tx-add-product">
              <span className="sf-tx-muted">Nhập ID sản phẩm hiện có để thêm vào giỏ. Thêm hàng không giữ tồn kho.</span>
              {import.meta.env.DEV && <div className="sf-tx-add-product__field">
                <span className="sf-tx-muted">Chọn nhanh sản phẩm mẫu</span>
                <Select aria-label="Chọn sản phẩm mẫu" placeholder="Chọn sản phẩm mẫu" showSearch optionFilterProp="label"
                  options={DEV_PRODUCTS} value={newProductId ?? undefined} disabled={busy || saving}
                  onChange={setNewProductId} />
              </div>}
              <div className="sf-tx-add-product__controls">
                <div className="sf-tx-add-product__field">
                  <label className="sf-tx-muted" htmlFor="tx-add-product-id">ID sản phẩm</label>
                  <InputNumber id="tx-add-product-id" aria-label="ID sản phẩm cần thêm" placeholder="ID sản phẩm" min={1} precision={0}
                    value={newProductId} disabled={busy || saving} onChange={setNewProductId} />
                </div>
                <div className="sf-tx-add-product__field">
                  <label className="sf-tx-muted" htmlFor="tx-add-quantity">Số lượng</label>
                  <InputNumber id="tx-add-quantity" aria-label="Số lượng cần thêm" min={1} max={999} precision={0}
                    value={newQuantity} disabled={busy || saving} onChange={setNewQuantity} />
                </div>
                <Button loading={busy}
                  disabled={saving || !Number.isSafeInteger(newProductId) || newProductId <= 0 || !Number.isInteger(newQuantity) || newQuantity < 1 || newQuantity > 999}
                  onClick={() => mutate(() => addCartItem(user, cart.version, newProductId, newQuantity), 'Đã thêm sản phẩm vào giỏ.')}>
                  Thêm vào giỏ
                </Button>
              </div>
            </div>
          </Card>
        </div>
        <aside className="sf-tx-grid__aside" aria-label="Tóm tắt giỏ hàng">
          <Card className="sf-tx-card" title={<h2 className="sf-tx-card__heading">Tóm tắt giỏ hàng</h2>}>
            <div className="sf-tx-summary">
              <div className="sf-tx-summary__figures">
                <div className="sf-tx-summary__row"><span>Sản phẩm đã chọn</span><strong className="sf-num">{selectedIds.length}</strong></div>
                <div className="sf-tx-summary__row"><span className="sf-tx-muted">Tổng cả giỏ</span><span className="sf-num">{formatVND(cartTotal)}</span></div>
                <div className="sf-tx-summary__total"><strong>Tổng đã chọn</strong><PriceTag price={selectedTotal} /></div>
              </div>
              <div className="sf-tx-summary__actions">
                {selectedIds.length > 0 && !busy && !saving
                  ? <Link to="/checkout" className="sf-tx-checkout-link">Đặt hàng ({selectedIds.length})</Link>
                  : <Button type="primary" disabled block>Đặt hàng ({selectedIds.length})</Button>}
                {cart.items.length > 0 && <Popconfirm title="Xóa toàn bộ sản phẩm trong giỏ?" okText="Xóa giỏ" cancelText="Giữ lại"
                  onConfirm={() => mutate(() => clearCart(user, cart.version), 'Đã xóa toàn bộ giỏ.')}>
                  <Button danger disabled={busy || saving} block>Xóa toàn bộ giỏ</Button>
                </Popconfirm>}
              </div>
              <span className="sf-tx-muted" role="status" aria-live="polite">
                {saving ? 'Đang lưu số lượng…' : selectedIds.length === 0 ? 'Chọn ít nhất một sản phẩm để đặt hàng.' : 'Giá cuối cùng được xác nhận ở bước tiếp theo.'}
              </span>
              {import.meta.env.DEV && <span className="sf-tx-muted">Phiên bản giỏ: {cart.version}</span>}
            </div>
          </Card>
        </aside>
      </div>}
    </div>
  );
}
