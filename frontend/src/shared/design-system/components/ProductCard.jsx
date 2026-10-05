import { Button } from 'antd';
import PriceTag from './PriceTag';
import StockBar from './StockBar';
import './ProductCard.css';

// Thẻ sản phẩm cho lưới sản phẩm và flash sale.
// Truyền sold + quota để hiện thanh suất (chỉ dùng cho flash sale).
export default function ProductCard({
  name, imageUrl, price, originalPrice, sold, quota,
  onBuy, onAddToCart, onOpen, buyLabel = 'Mua ngay', loading = false,
}) {
  const isFlash = quota != null;
  const soldOut = isFlash && sold >= quota;
  return (
    <article className="sf-card">
      <button type="button" className="sf-card__thumb" onClick={onOpen} aria-label={`Xem ${name}`}>
        {imageUrl ? <img src={imageUrl} alt="" loading="lazy" /> : <span>Chưa có ảnh</span>}
      </button>
      <div className="sf-card__body">
        <h3 className="sf-card__name" title={name}>{name}</h3>
        <PriceTag price={price} originalPrice={originalPrice} />
        {isFlash && <StockBar sold={sold} quota={quota} />}
        {(onBuy || onAddToCart) && (
          <div className="sf-card__actions">
            {onBuy && (
              <Button type="primary" onClick={onBuy} disabled={soldOut} loading={loading} block>
                {soldOut ? 'Hết suất' : buyLabel}
              </Button>
            )}
            {onAddToCart && <Button onClick={onAddToCart} block>Thêm vào giỏ</Button>}
          </div>
        )}
      </div>
    </article>
  );
}
