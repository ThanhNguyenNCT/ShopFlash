import { useCallback, useEffect, useState } from 'react';
import { Button, Card, Flex, Typography } from 'antd';
import { DownOutlined, RightOutlined, SearchOutlined, ThunderboltOutlined, UpOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { tokens } from '@/shared/design-system';
import { getCategories, searchProducts } from '../api';
import ProductGrid from '../components/ProductGrid';

// Trang chủ (TV2): danh mục và sản phẩm thật từ API của module indexing (không có từ khóa nên backend đọc Oracle).
// Dải flash sale chờ module concurrency (TV5) có API chương trình đang chạy; hiện chỉ để lối vào trang Flash sale.
// Số danh mục hiện sẵn ở trang chủ (khoảng 2 dòng); phần còn lại mở bằng nút "Xem thêm".
const CATEGORY_PREVIEW = 12;

export default function HomePage() {
  const navigate = useNavigate();
  const [categories, setCategories] = useState([]);
  const [showAllCategories, setShowAllCategories] = useState(false);
  const [products, setProducts] = useState({ loading: true, error: null, items: [] });

  const loadProducts = useCallback(() => {
    setProducts((p) => ({ ...p, loading: true, error: null }));
    searchProducts({ inStock: true, sort: 'price_asc', size: 8 })
      .then((res) => setProducts({ loading: false, error: null, items: res.data.items }))
      .catch((error) => setProducts({ loading: false, error, items: [] }));
  }, []);

  useEffect(() => {
    getCategories().then(setCategories).catch(() => setCategories([]));
    loadProducts();
  }, [loadProducts]);

  const section = { display: 'grid', gap: tokens.space[3] };

  return (
    <div style={{ display: 'grid', gap: tokens.space[6] }}>
      <section
        style={{
          display: 'grid',
          gap: tokens.space[4],
          padding: tokens.space[6],
          background: tokens.color.brandTint,
          borderRadius: tokens.radius.lg,
        }}
      >
        <div style={{ display: 'grid', gap: tokens.space[2] }}>
          <Typography.Title level={1} style={{ margin: 0, fontSize: tokens.text.display[0], lineHeight: `${tokens.text.display[1]}px` }}>
            Săn deal chớp nhoáng mỗi ngày
          </Typography.Title>
          <Typography.Text type="secondary">
            Tìm nhanh sản phẩm theo tên, lọc theo danh mục và khoảng giá.
          </Typography.Text>
        </div>
        <Flex wrap gap={tokens.space[2]}>
          <Button type="primary" size="large" icon={<SearchOutlined />} onClick={() => navigate('/search')}>
            Xem tất cả sản phẩm
          </Button>
          <Button size="large" icon={<ThunderboltOutlined />} onClick={() => navigate('/flash-sale')}>
            Săn flash sale
          </Button>
        </Flex>
        {categories.length > 0 && (
          <Flex wrap gap={tokens.space[2]} aria-label="Danh mục">
            {(showAllCategories ? categories : categories.slice(0, CATEGORY_PREVIEW)).map((c) => (
              <Button key={c.categoryId} size="small" shape="round" onClick={() => navigate(`/search?categoryId=${c.categoryId}`)}>
                {c.name}
              </Button>
            ))}
            {categories.length > CATEGORY_PREVIEW && (
              <Button
                size="small"
                shape="round"
                type="link"
                aria-expanded={showAllCategories}
                icon={showAllCategories ? <UpOutlined /> : <DownOutlined />}
                iconPlacement="end"
                onClick={() => setShowAllCategories((v) => !v)}
              >
                {showAllCategories ? 'Thu gọn' : `Xem thêm ${categories.length - CATEGORY_PREVIEW} danh mục`}
              </Button>
            )}
          </Flex>
        )}
      </section>

      <section style={section} aria-label="Sản phẩm đang bán">
        <Flex align="center" justify="space-between" wrap gap={tokens.space[2]}>
          <Typography.Title level={4} style={{ margin: 0 }}>Sản phẩm đang bán</Typography.Title>
          <Link to="/search?inStock=true">Xem thêm <RightOutlined /></Link>
        </Flex>
        <ProductGrid
          items={products.items}
          loading={products.loading}
          error={products.error}
          onRetry={loadProducts}
          emptyText="Chưa có sản phẩm nào đang bán"
        />
      </section>

      <Card size="small">
        <Flex align="center" justify="space-between" wrap gap={tokens.space[3]}>
          <Flex vertical>
            <Typography.Text strong>Flash sale</Typography.Text>
            <Typography.Text type="secondary">Giá tốt, giới hạn suất cho người nhanh tay.</Typography.Text>
          </Flex>
          <Button icon={<ThunderboltOutlined />} onClick={() => navigate('/flash-sale')}>Vào trang Flash sale</Button>
        </Flex>
      </Card>
    </div>
  );
}
