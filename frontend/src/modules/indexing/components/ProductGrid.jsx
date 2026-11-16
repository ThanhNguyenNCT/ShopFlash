import { useState } from 'react';
import { Alert, Button, Col, Empty, Flex, Row, Skeleton } from 'antd';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import { ProductCard, tokens } from '@/shared/design-system';
import { useAuth } from '@/shared/auth';
import { addProductToCart } from '@/modules/transaction/api';
import './indexing.css';

/**
 * Lưới sản phẩm thật (productId Oracle). Nút "Thêm vào giỏ" dùng addProductToCart của module transaction (TV4),
 * chỉ hiện với sản phẩm còn hàng; chưa đăng nhập thì chuyển sang /login rồi quay lại.
 * cols: số cột theo màn hình, mặc định 2 cột trên mobile.
 */
export default function ProductGrid({
  items, loading, error, onRetry, emptyText = 'Không tìm thấy sản phẩm phù hợp', skeletonCount = 8,
  cols = { xs: 12, sm: 8, lg: 6 },
}) {
  const navigate = useNavigate();
  const location = useLocation();
  const { user } = useAuth();
  const [notice, setNotice] = useState(null);
  const gutter = [tokens.space[4], tokens.space[4]];

  const addToCart = async (product) => {
    if (!user) {
      navigate(`/login?next=${encodeURIComponent(location.pathname + location.search)}`);
      return;
    }
    setNotice(null);
    try {
      await addProductToCart(user, product.productId, 1);
      setNotice({ type: 'success', title: <>Đã thêm “{product.name}” vào giỏ. <Link to="/cart">Xem giỏ</Link></> });
    } catch (problem) {
      setNotice({ type: 'error', title: problem.message ?? 'Không thêm được sản phẩm vào giỏ.' });
    }
  };

  if (loading) {
    return (
      <Row gutter={gutter}>
        {Array.from({ length: skeletonCount }, (_, i) => (
          <Col key={i} {...cols}>
            <Flex vertical gap={tokens.space[2]}>
              <Skeleton.Image active style={{ width: '100%', height: 160 }} />
              <Skeleton active title={false} paragraph={{ rows: 2 }} />
            </Flex>
          </Col>
        ))}
      </Row>
    );
  }

  if (error) {
    return (
      <Alert
        type="error"
        showIcon
        title="Không tải được sản phẩm"
        description={error.message}
        action={onRetry && <Button onClick={onRetry}>Thử lại</Button>}
      />
    );
  }

  if (!items?.length) {
    return <Empty description={emptyText} />;
  }

  return (
    <Flex vertical gap={tokens.space[3]}>
      {notice && <Alert type={notice.type} showIcon closable title={notice.title} onClose={() => setNotice(null)} />}
      <Row gutter={gutter} className="sf-idx-grid">
        {items.map((p) => (
          <Col key={p.productId} {...cols}>
            <div className={p.inStock ? 'sf-idx-card' : 'sf-idx-card sf-idx-card--soldout'}>
              {!p.inStock && <span className="sf-idx-card__badge">Hết hàng</span>}
              <ProductCard
                name={p.name}
                imageUrl={p.thumbnailUrl}
                price={p.price}
                onOpen={() => navigate(`/products/${p.productId}`)}
                onAddToCart={p.inStock ? () => addToCart(p) : undefined}
              />
            </div>
          </Col>
        ))}
      </Row>
    </Flex>
  );
}
