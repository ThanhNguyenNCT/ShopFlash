import { formatVND, discountPercent } from '../format';
import './PriceTag.css';

// Giá bán (brand), giá gốc gạch ngang và badge % giảm nếu có giá gốc.
// stacked: giá gốc và badge xuống dòng riêng dưới giá bán (dùng trong thẻ sản phẩm để các thẻ cùng chiều cao).
export default function PriceTag({ price, originalPrice, size = 'md', showBadge = true, stacked = false }) {
  const pct = discountPercent(price, originalPrice);
  const extra = pct > 0 && (
    <>
      <s className="sf-price__was">{formatVND(originalPrice)}</s>
      {showBadge && <span className="sf-price__badge">-{pct}%</span>}
    </>
  );
  return (
    <span className={`sf-price sf-price--${size} sf-num${stacked ? ' sf-price--stacked' : ''}`}>
      <strong className="sf-price__now">{formatVND(price)}</strong>
      {stacked ? extra && <span className="sf-price__extra">{extra}</span> : extra}
    </span>
  );
}
