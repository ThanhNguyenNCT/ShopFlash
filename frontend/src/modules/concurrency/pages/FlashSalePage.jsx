import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Alert, Button, Empty, Spin, Typography } from 'antd';
import { Link, useNavigate } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { FlashCountdown, ProductCard, tokens } from '@/shared/design-system';
import { getActiveSales, getStock, reserveItem } from '../api';
import { formatTime, nextOffset, toClientTime, useServerNow } from '../format';
import '../concurrency.css';

// F9 Flash sale (TV5): chương trình đang chạy và sắp mở trong 7 ngày tới.
// Số suất còn lại hỏi lại /stock mỗi 2 s (chỉ đọc Redis). Đếm ngược theo giờ server.

const STOCK_POLL_MS = 2000;
const RELOAD_THROTTLE_MS = 10000;
const LOGIN_PATH = `/login?next=${encodeURIComponent('/flash-sale')}`;

// Lỗi làm danh sách chương trình đã cũ: tải lại để cập nhật trạng thái.
const RELOAD_CODES = new Set(['SALE_NOT_STARTED', 'SALE_ENDED', 'FLASH_SALE_NOT_FOUND', 'ITEM_UNAVAILABLE']);

function buyErrorText(problem, sale) {
  switch (problem.code) {
    case 'SOLD_OUT':
      return 'Đã hết suất. Suất có thể mở lại khi người khác hủy hoặc để quá hạn giữ suất.';
    case 'USER_LIMIT_REACHED':
      return `Bạn đã mua đủ số lượng cho phép của sản phẩm này (tối đa ${sale.maxQuantityPerUser} mỗi người).`;
    case 'REDIS_UNAVAILABLE':
      return 'Hệ thống giữ suất đang tạm gián đoạn. Thử lại sau ít giây.';
    case 'NETWORK_ERROR':
    case 'TIMEOUT':
      return 'Không nhận được phản hồi từ máy chủ. Bấm "Mua ngay" lần nữa: nếu bạn đã giữ được suất, hệ thống mở lại đúng suất đó.';
    default:
      return problem.message ?? 'Không giữ được suất. Thử lại sau.';
  }
}

export default function FlashSalePage() {
  const navigate = useNavigate();
  const { user } = useAuth();
  const [sales, setSales] = useState(null);
  const [offset, setOffset] = useState(0);
  const [remaining, setRemaining] = useState({});
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);
  const [buyingItemId, setBuyingItemId] = useState(null);
  const buyingRef = useRef(false);
  const lastReloadRef = useRef(0);
  const now = useServerNow(offset);

  const load = useCallback(async () => {
    setError(null);
    try {
      const data = await getActiveSales();
      setOffset((current) => nextOffset(current, data.serverTime));
      setSales(data.sales);
      setRemaining(Object.fromEntries(data.sales.flatMap((s) => s.items.map((i) => [i.itemId, i.remaining]))));
    } catch (problem) {
      setError(problem);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  // Pha theo giờ server: mở bán và kết thúc đúng lúc mà không cần tải lại cả trang.
  const phaseOf = useCallback((sale) => {
    if (now < Date.parse(sale.startAt)) return 'UPCOMING';
    return now < Date.parse(sale.endAt) ? 'LIVE' : 'ENDED';
  }, [now]);

  const liveIds = useMemo(
    () => (sales ?? []).filter((s) => phaseOf(s) === 'LIVE').map((s) => s.flashSaleId).join(','),
    [sales, phaseOf],
  );

  // Hỏi lại số suất mỗi 2 s cho chương trình đang mở bán. Lượt sau chỉ bắt đầu khi lượt trước xong;
  // tab bị ẩn thì bỏ qua để không tạo tải thừa.
  useEffect(() => {
    if (!liveIds) return undefined;
    const ids = liveIds.split(',');
    let stopped = false;
    let timer;
    const tick = async () => {
      if (document.visibilityState === 'visible') {
        const results = await Promise.allSettled(ids.map((saleId) => getStock(saleId)));
        if (stopped) return;
        const next = {};
        let needReload = false;
        for (const result of results) {
          if (result.status === 'fulfilled') {
            setOffset((current) => nextOffset(current, result.value.serverTime));
            for (const item of result.value.items) next[item.itemId] = item.remaining;
          } else if (result.reason?.status === 404) {
            needReload = true; // runtime đã gỡ: chương trình vừa kết thúc hoặc bị hủy
          }
        }
        setRemaining((current) => ({ ...current, ...next }));
        // Tải lại danh sách tối đa mỗi 10 s: Redis thiếu key (chưa nạp lại) thì /stock trả 404 ở mọi lượt.
        if (needReload && Date.now() - lastReloadRef.current > RELOAD_THROTTLE_MS) {
          lastReloadRef.current = Date.now();
          load();
        }
      }
      if (!stopped) timer = setTimeout(tick, STOCK_POLL_MS);
    };
    timer = setTimeout(tick, STOCK_POLL_MS);
    return () => { stopped = true; clearTimeout(timer); };
  }, [liveIds, load]);

  const buy = async (sale, item) => {
    if (!user) {
      navigate(LOGIN_PATH);
      return;
    }
    if (user.role !== 'CUSTOMER') {
      setNotice({ type: 'warning', title: 'Tài khoản quản trị không mua flash sale được. Đăng nhập bằng tài khoản Khách để mua.' });
      return;
    }
    if (buyingRef.current) return;
    buyingRef.current = true;
    setBuyingItemId(item.itemId);
    setNotice(null);
    try {
      const { reservation, created } = await reserveItem(sale.flashSaleId, item.itemId, 1);
      navigate(`/flash-sale/checkout/${reservation.reservationId}`, { state: { reservation, resumed: !created } });
    } catch (problem) {
      if (problem.status === 401) {
        navigate(LOGIN_PATH);
        return;
      }
      if (problem.code === 'SOLD_OUT') setRemaining((current) => ({ ...current, [item.itemId]: 0 }));
      setNotice({ type: 'error', title: buyErrorText(problem, sale) });
      if (RELOAD_CODES.has(problem.code)) load();
    } finally {
      buyingRef.current = false;
      setBuyingItemId(null);
    }
  };

  const reloadSoon = () => setTimeout(load, 1500);

  if (loading) {
    return <Spin description="Đang tải chương trình flash sale"><div style={{ minHeight: tokens.space[8] * 4 }} /></Spin>;
  }

  return (
    <div className="sf-cc-page">
      <header className="sf-cc-page__heading">
        <Typography.Title level={1}>Flash sale</Typography.Title>
        <p className="sf-cc-muted">
          Bấm "Mua ngay" để giữ suất, rồi đặt đơn trước khi hết thời gian giữ. Quá hạn, suất được trả lại cho người khác.
        </p>
      </header>

      {notice && <Alert type={notice.type} showIcon closable title={notice.title} onClose={() => setNotice(null)} />}

      {error && (
        <Alert type="error" showIcon title="Không tải được chương trình flash sale" description={error.message}
          action={<Button onClick={load}>Thử lại</Button>} />
      )}

      {!error && sales?.length === 0 && (
        <Empty description="Chưa có chương trình flash sale nào đang chạy hoặc sắp mở trong 7 ngày tới.">
          <Link to="/">Xem sản phẩm khác</Link>
        </Empty>
      )}

      {sales?.map((sale) => {
        const phase = phaseOf(sale);
        const live = phase === 'LIVE';
        return (
          <section key={sale.flashSaleId} className="sf-cc-sale" aria-labelledby={`sale-${sale.flashSaleId}`}>
            <FlashCountdown
              title={live ? 'FLASH SALE · KẾT THÚC SAU' : 'SẮP MỞ BÁN'}
              endsAt={toClientTime(phase === 'UPCOMING' ? sale.startAt : sale.endAt, offset)}
              onEnd={reloadSoon}
            />
            <div className="sf-cc-sale__meta">
              <h2 id={`sale-${sale.flashSaleId}`}>{sale.name}</h2>
              <p className="sf-cc-muted">
                {phase === 'UPCOMING' && `Mở bán lúc ${formatTime(sale.startAt)} · `}
                {phase === 'ENDED' ? 'Chương trình đã kết thúc · ' : `Kết thúc lúc ${formatTime(sale.endAt)} · `}
                Tối đa {sale.maxQuantityPerUser} sản phẩm mỗi người · Giữ suất {sale.reservationTimeoutSeconds} giây
              </p>
            </div>
            <div className="sf-cc-grid">
              {sale.items.map((item) => {
                const left = remaining[item.itemId] ?? item.remaining;
                return (
                  <ProductCard
                    key={item.itemId}
                    name={item.productName}
                    imageUrl={item.thumbnailUrl}
                    price={item.salePrice}
                    originalPrice={item.basePrice}
                    sold={Math.max(0, item.allocated - left)}
                    quota={item.allocated}
                    loading={buyingItemId === item.itemId}
                    onOpen={() => navigate(`/products/${item.productId}`)}
                    onBuy={live ? () => buy(sale, item) : undefined}
                  />
                );
              })}
            </div>
          </section>
        );
      })}
    </div>
  );
}
