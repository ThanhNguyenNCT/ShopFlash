import { formatVND, discountPercent } from '../format';
import './PriceTag.css';

// Giá bán (brand), giá gốc gạch ngang và badge % giảm nếu có giá gốc.
export default function PriceTag({ price, originalPrice, size = 'md', showBadge = true }) {
  const pct = discountPercent(price, originalPrice);
  return (
    <span className={`sf-price sf-price--${size} sf-num`}>
      <strong className="sf-price__now">{formatVND(price)}</strong>
      {pct > 0 && <s className="sf-price__was">{formatVND(originalPrice)}</s>}
      {pct > 0 && showBadge && <span className="sf-price__badge">-{pct}%</span>}
    </span>
  );
}
