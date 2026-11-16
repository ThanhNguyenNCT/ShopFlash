import { useMemo, useState } from 'react';
import { Col, Row, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import { REPORTS } from '../api';
import ReportsToolbar from '../components/reports/ReportsToolbar';
import { DATE_FORMAT, defaultRange } from '../format';
import RevenueByMonth from '../components/reports/RevenueByMonth';
import RevenueByCategory from '../components/reports/RevenueByCategory';
import TopProducts from '../components/reports/TopProducts';
import VipCustomers from '../components/reports/VipCustomers';
import UnsoldProducts from '../components/reports/UnsoldProducts';
import '../components/reports/reports.css';

const JOIN_REPORTS = new Set(REPORTS.filter((r) => r.joinHint).map((r) => r.slug));
// Báo cáo không phụ thuộc khoảng ngày (backend bỏ qua from/to).
const DATELESS_REPORTS = new Set(['unsold-products']);

/** Tham số gửi cho từng báo cáo: chỉ gửi những gì báo cáo đó dùng, để đổi bộ lọc không làm tải lại khối không liên quan. */
function paramsFor(slug, filters) {
  const { range, source, joinHint, redisMode } = filters;
  const p = { source };
  if (!DATELESS_REPORTS.has(slug)) {
    p.from = range[0].format(DATE_FORMAT);
    p.to = range[1].format(DATE_FORMAT);
  }
  if (source === 'oracle' && JOIN_REPORTS.has(slug)) p.joinHint = joinHint;
  if (source === 'redis') p.redisMode = redisMode;
  return p;
}

export default function ReportsPage() {
  const [filters, setFilters] = useState(() => ({
    range: defaultRange(),
    source: 'oracle',
    joinHint: 'AUTO',
    redisMode: 'AGGREGATE',
  }));
  const params = useMemo(
    () => Object.fromEntries(REPORTS.map((r) => [r.slug, paramsFor(r.slug, filters)])),
    [filters],
  );
  const gutter = [tokens.space[4], tokens.space[4]];

  return (
    <div className="sf-reports-page">
      <div className="sf-reports-page__head">
        <Typography.Title level={3} style={{ margin: 0 }}>Báo cáo</Typography.Title>
        <Typography.Text type="secondary">
          Doanh thu, sản phẩm bán chạy và khách VIP. Đơn được tính: trạng thái CONFIRMED hoặc COMPLETED, giờ Việt Nam.
        </Typography.Text>
      </div>
      <ReportsToolbar value={filters} onChange={setFilters} />
      <Row gutter={gutter}>
        <Col span={24}><RevenueByMonth params={params['revenue-by-month']} /></Col>
        <Col span={24}><RevenueByCategory params={params['revenue-by-category']} /></Col>
        <Col xs={24} xxl={12}><TopProducts params={params['top-products']} /></Col>
        <Col xs={24} xxl={12}><VipCustomers params={params['vip-customers']} /></Col>
        <Col span={24}><UnsoldProducts params={params['unsold-products']} /></Col>
      </Row>
    </div>
  );
}
