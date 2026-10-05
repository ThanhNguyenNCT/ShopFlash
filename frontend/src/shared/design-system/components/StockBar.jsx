import './StockBar.css';

// Thanh suất flash sale: đã bán / tổng suất.
export default function StockBar({ sold, quota }) {
  const safeQuota = Math.max(1, quota);
  const pct = Math.min(100, Math.round((sold / safeQuota) * 100));
  const remaining = Math.max(0, quota - sold);
  return (
    <div className="sf-stock">
      <div className="sf-stock__bar" role="progressbar" aria-valuemin={0} aria-valuemax={quota} aria-valuenow={sold}>
        <i style={{ width: `${pct}%` }} />
      </div>
      <small className="sf-stock__text sf-num">
        {remaining > 0 ? `Đã bán ${sold}/${quota} suất · còn ${remaining}` : 'Hết suất'}
      </small>
    </div>
  );
}
