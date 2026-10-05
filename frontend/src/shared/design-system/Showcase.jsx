import { Button, Input, Space, Table, Typography } from 'antd';
import { color } from './tokens';
import {
  PriceTag, FlashCountdown, StockBar, OrderStatusTag, ProductCard,
} from './index';

// Trang xem toàn bộ design system. Gắn vào router ở đường dẫn /design-system khi dựng xong app.
const inTwoHours = Date.now() + 2 * 3600 * 1000 + 14 * 60 * 1000;

const sampleOrders = [
  { id: 'DH-100231', total: 549000, status: 'PENDING' },
  { id: 'DH-100230', total: 1290000, status: 'PAID' },
  { id: 'DH-100228', total: 89000, status: 'SHIPPED' },
  { id: 'DH-100225', total: 320000, status: 'CANCELLED' },
];

function Section({ title, children }) {
  return (
    <section style={{ display: 'grid', gap: 'var(--sf-space-3)' }}>
      <Typography.Title level={4} style={{ margin: 0 }}>{title}</Typography.Title>
      {children}
    </section>
  );
}

export default function Showcase() {
  return (
    <div style={{ maxWidth: 1080, margin: '0 auto', padding: 'var(--sf-space-8) var(--sf-space-4)', display: 'grid', gap: 'var(--sf-space-8)' }}>
      <Typography.Title level={2} style={{ margin: 0 }}>ShopFlash Design System</Typography.Title>

      <Section title="Màu">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(140px, 1fr))', gap: 'var(--sf-space-3)' }}>
          {Object.entries(color).map(([name, value]) => (
            <div key={name} style={{ display: 'grid', gap: 'var(--sf-space-1)' }}>
              <div style={{ height: 48, background: value, borderRadius: 'var(--sf-radius-md)', border: '1px solid var(--sf-color-line)' }} />
              <span className="sf-code">{name}<br />{value}</span>
            </div>
          ))}
        </div>
      </Section>

      <Section title="Nút và form (Ant Design đã theme)">
        <Space wrap>
          <Button type="primary">Mua ngay</Button>
          <Button>Thêm vào giỏ</Button>
          <Button type="text">Xem thêm</Button>
          <Button danger>Xóa sản phẩm</Button>
          <Input placeholder="Tìm sản phẩm" style={{ width: 240 }} />
        </Space>
      </Section>

      <Section title="Flash sale">
        <FlashCountdown endsAt={inTwoHours} />
        <div style={{ maxWidth: 320 }}><StockBar sold={88} quota={100} /></div>
      </Section>

      <Section title="Giá">
        <Space direction="vertical">
          <PriceTag price={549000} originalPrice={999000} size="lg" />
          <PriceTag price={549000} originalPrice={999000} />
          <PriceTag price={129000} size="sm" />
        </Space>
      </Section>

      <Section title="Thẻ sản phẩm">
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fill, minmax(200px, 1fr))', gap: 'var(--sf-space-4)' }}>
          <ProductCard name="Tai nghe không dây chống ồn, pin 30 giờ" price={549000} originalPrice={999000} sold={88} quota={100} onBuy={() => {}} />
          <ProductCard name="Áo thun oversize cotton 100%" price={129000} originalPrice={199000} onBuy={() => {}} onAddToCart={() => {}} buyLabel="Mua ngay" />
          <ProductCard name="Bình giữ nhiệt 750ml" price={159000} originalPrice={259000} sold={50} quota={50} onBuy={() => {}} />
        </div>
      </Section>

      <Section title="Trạng thái đơn hàng">
        <Table
          rowKey="id"
          pagination={false}
          dataSource={sampleOrders}
          columns={[
            { title: 'Mã đơn', dataIndex: 'id', render: (v) => <span className="sf-code">{v}</span> },
            { title: 'Tổng tiền', dataIndex: 'total', align: 'right', render: (v) => <PriceTag price={v} size="sm" /> },
            { title: 'Trạng thái', dataIndex: 'status', render: (v) => <OrderStatusTag status={v} /> },
          ]}
        />
      </Section>
    </div>
  );
}
