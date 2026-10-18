import { useState } from 'react';
import { Alert, Button, Col, Flex, Row, Tag, Typography } from 'antd';
import { RightOutlined, SearchOutlined, ThunderboltOutlined } from '@ant-design/icons';
import { Link, useNavigate } from 'react-router-dom';
import { FlashCountdown, ProductCard, tokens } from '@/shared/design-system';
import ComingSoon from '@/shared/components/ComingSoon';

// F3, F4 Trang chủ (TV2). PLACEHOLDER CÓ DỮ LIỆU MINH HỌA: bố cục cửa hàng thật nhưng sản phẩm là dữ liệu giả.
// TV2 thay toàn bộ file này bằng trang chủ lấy dữ liệu thật (giữ export default). Route khai báo trong ../routes.jsx.

const CATEGORIES = ['Điện thoại', 'Laptop', 'Tai nghe', 'Đồng hồ', 'Phụ kiện', 'Gia dụng', 'Thời trang'];

// Ảnh minh họa dựng bằng SVG từ token màu (không có ảnh thật).
function sampleThumb(label, i) {
  const { color } = tokens;
  const bg = i % 2 ? color.surfaceSunken : color.brandTint;
  const fg = i % 2 ? color.inkMuted : color.brand;
  const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 200 200"><rect width="200" height="200" fill="${bg}"/>`
    + `<text x="100" y="118" text-anchor="middle" font-family="sans-serif" font-size="56" font-weight="800" fill="${fg}">${label}</text></svg>`;
  return `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`;
}

const SAMPLE_PRODUCTS = [
  { id: 1001, name: 'Tai nghe Bluetooth chống ồn ANC, pin 40 giờ', price: 549000, originalPrice: 999000, short: 'TN' },
  { id: 1002, name: 'Sạc nhanh GaN 65W 3 cổng USB-C', price: 389000, originalPrice: 590000, short: 'SC' },
  { id: 1003, name: 'Bình giữ nhiệt inox 750ml', price: 179000, originalPrice: 250000, short: 'BG' },
  { id: 1004, name: 'Áo thun cotton oversize unisex', price: 129000, short: 'AT' },
  { id: 1005, name: 'Chuột không dây im lặng 2.4GHz', price: 159000, originalPrice: 220000, short: 'CH' },
  { id: 1006, name: 'Nồi chiên không dầu 5 lít', price: 1290000, originalPrice: 1890000, short: 'NC' },
  { id: 1007, name: 'Bàn phím cơ 87 phím, switch đỏ', price: 690000, short: 'BP' },
  { id: 1008, name: 'Đồng hồ thông minh đo nhịp tim, chống nước', price: 899000, originalPrice: 1490000, short: 'ĐH' },
].map((p, i) => ({ ...p, imageUrl: sampleThumb(p.short, i) }));

const SAMPLE_FLASH = [
  { id: 1001, name: 'Tai nghe Bluetooth chống ồn ANC', price: 299000, originalPrice: 999000, sold: 86, quota: 100, short: 'TN' },
  { id: 1006, name: 'Nồi chiên không dầu 5 lít', price: 790000, originalPrice: 1890000, sold: 41, quota: 50, short: 'NC' },
  { id: 1008, name: 'Đồng hồ thông minh đo nhịp tim', price: 499000, originalPrice: 1490000, sold: 12, quota: 80, short: 'ĐH' },
  { id: 1002, name: 'Sạc nhanh GaN 65W 3 cổng', price: 199000, originalPrice: 590000, sold: 30, quota: 30, short: 'SC' },
].map((p, i) => ({ ...p, imageUrl: sampleThumb(p.short, i + 1) }));

const SAMPLE_DURATION_MS = (2 * 3600 + 14 * 60 + 37) * 1000;

function SampleTag() {
  return <Tag color="warning">Dữ liệu minh họa</Tag>;
}

function SectionHead({ title, to, linkLabel }) {
  return (
    <Flex align="center" justify="space-between" wrap gap={tokens.space[2]}>
      <Flex align="center" wrap gap={tokens.space[2]}>
        <Typography.Title level={4} style={{ margin: 0 }}>{title}</Typography.Title>
        <SampleTag />
      </Flex>
      {to && <Link to={to}>{linkLabel} <RightOutlined /></Link>}
    </Flex>
  );
}

export default function HomePage() {
  const navigate = useNavigate();
  const [flashEndsAt] = useState(() => Date.now() + SAMPLE_DURATION_MS);
  const gutter = [tokens.space[4], tokens.space[4]];
  const section = { display: 'grid', gap: tokens.space[3] };

  return (
    <div style={{ display: 'grid', gap: tokens.space[6] }}>
      <Alert
        type="info"
        showIcon
        title="Trang chủ đang dùng dữ liệu minh họa"
        description="Sản phẩm, giá và suất flash sale bên dưới là dữ liệu giả để xem bố cục. TV2 (module indexing) sẽ thay bằng dữ liệu thật, xem hướng dẫn ở cuối trang."
      />

      {/* Banner */}
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
            Flash sale giới hạn suất, giá tốt cho người nhanh tay. Tìm nhanh hàng nghìn sản phẩm theo tên, SKU và danh mục.
          </Typography.Text>
        </div>
        <Flex wrap gap={tokens.space[2]}>
          <Button type="primary" size="large" icon={<ThunderboltOutlined />} onClick={() => navigate('/flash-sale')}>
            Săn flash sale
          </Button>
          <Button size="large" icon={<SearchOutlined />} onClick={() => navigate('/search')}>
            Xem tất cả sản phẩm
          </Button>
        </Flex>
        <Flex wrap gap={tokens.space[2]} aria-label="Danh mục nổi bật">
          {CATEGORIES.map((c) => (
            <Button key={c} size="small" shape="round" onClick={() => navigate(`/search?q=${encodeURIComponent(c)}`)}>
              {c}
            </Button>
          ))}
        </Flex>
      </section>

      {/* Flash sale */}
      <section style={section} aria-label="Flash sale">
        <SectionHead title="Flash sale hôm nay" to="/flash-sale" linkLabel="Xem tất cả" />
        <FlashCountdown endsAt={flashEndsAt} title="FLASH SALE · KẾT THÚC SAU" />
        <Row gutter={gutter}>
          {SAMPLE_FLASH.map((p) => (
            <Col key={p.id} xs={12} md={6}>
              <ProductCard
                name={p.name}
                imageUrl={p.imageUrl}
                price={p.price}
                originalPrice={p.originalPrice}
                sold={p.sold}
                quota={p.quota}
                onOpen={() => navigate(`/products/${p.id}`)}
                onBuy={() => navigate('/flash-sale')}
              />
            </Col>
          ))}
        </Row>
      </section>

      {/* Lưới sản phẩm */}
      <section style={section} aria-label="Gợi ý hôm nay">
        <SectionHead title="Gợi ý hôm nay" to="/search" linkLabel="Xem thêm" />
        <Row gutter={gutter}>
          {SAMPLE_PRODUCTS.map((p) => (
            <Col key={p.id} xs={12} sm={8} lg={6}>
              <ProductCard
                name={p.name}
                imageUrl={p.imageUrl}
                price={p.price}
                originalPrice={p.originalPrice}
                onOpen={() => navigate(`/products/${p.id}`)}
              />
            </Col>
          ))}
        </Row>
      </section>

      <ComingSoon
        compact
        feature="F3, F4"
        title="Trang chủ với dữ liệu thật"
        owner="TV2"
        module="indexing"
        file="frontend/src/modules/indexing/pages/HomePage.jsx"
        description="Giữ bố cục này (banner, dải flash sale, lưới sản phẩm) nhưng lấy dữ liệu thật: sản phẩm từ chỉ mục tìm kiếm (Oracle index hoặc RediSearch), flash sale từ module concurrency."
        todo={[
          'Lưới sản phẩm lấy từ API tìm kiếm của indexing (F3/F4: Oracle index hoặc RediSearch), có Skeleton khi tải, Empty khi rỗng, lỗi có nút Thử lại.',
          'Danh mục nổi bật lấy từ CATEGORIES (module storage, TV1); bấm sang /search với bộ lọc danh mục.',
          'Dải flash sale dùng dữ liệu chương trình đang chạy của concurrency (TV5); không có chương trình thì ẩn dải.',
          'Bỏ toàn bộ dữ liệu minh họa (SAMPLE_*), Tag "Dữ liệu minh họa" và khung hướng dẫn này.',
        ]}
        api={['GET /api/products/search?sort=&page=&size=', 'GET /api/categories', 'GET /api/flash-sales/active (module concurrency)']}
      />
    </div>
  );
}
