import { Alert, Button, Card, Col, Flex, Row, Skeleton, Table, Tag, Typography } from 'antd';
import { ReloadOutlined, RightOutlined } from '@ant-design/icons';
import { Link, useNavigate, useOutletContext } from 'react-router-dom';
import { formatVND, PriceTag, tokens } from '@/shared/design-system';
import useReport from '../components/reports/useReport';
import { formatInt, formatMonth } from '../format';
import '../components/reports/reports.css';

const TOP_LIMIT = 5;
const TOP_PARAMS = { limit: TOP_LIMIT };
// Không truyền from/to: backend (và mock) mặc định 12 tháng gần nhất, tính cả tháng hiện tại.
const MONTH_PARAMS = {};

const topColumns = [
  { title: '#', dataIndex: 'rank', key: 'rank', align: 'right', width: tokens.space[8] + tokens.space[4], render: (v) => <span className="sf-num">{v}</span> },
  {
    title: 'Sản phẩm', dataIndex: 'productName', key: 'productName',
    render: (name, row) => (
      <span>
        {name} <span className="sf-code sf-muted">#{row.productId}</span>
      </span>
    ),
  },
  { title: 'Số lượng', dataIndex: 'quantity', key: 'quantity', align: 'right', render: (v) => <span className="sf-num">{formatInt(v)}</span> },
  { title: 'Doanh thu', dataIndex: 'revenue', key: 'revenue', align: 'right', render: (v) => <PriceTag price={v} size="sm" /> },
];

function LoadError({ error, onRetry }) {
  return (
    <Alert
      type="error"
      showIcon
      title={error.message || 'Không tải được dữ liệu.'}
      description={<span>Mã lỗi: <span className="sf-code">{error.code}</span></span>}
      action={<Button size="small" icon={<ReloadOutlined />} onClick={onRetry}>Thử lại</Button>}
    />
  );
}

function Kpi({ label, value, hint }) {
  return (
    <Card style={{ height: '100%' }}>
      <div style={{ display: 'grid', gap: tokens.space[1] }}>
        <Typography.Text type="secondary">{label}</Typography.Text>
        <span
          className="sf-num"
          style={{ fontSize: tokens.text.title[0], lineHeight: `${tokens.text.title[1]}px`, fontWeight: tokens.text.title[2], overflowWrap: 'anywhere' }}
        >
          {value}
        </span>
        {hint && <Typography.Text type="secondary" style={{ fontSize: tokens.text.caption[0] }}>{hint}</Typography.Text>}
      </div>
    </Card>
  );
}

function KpiRow() {
  const state = useReport('revenue-by-month', MONTH_PARAMS);
  const rows = state.data?.rows ?? [];
  const gutter = [tokens.space[4], tokens.space[4]];

  if (state.error) return <LoadError error={state.error} onRetry={state.reload} />;
  if (state.loading && !state.meta) {
    return (
      <Row gutter={gutter}>
        {[0, 1, 2].map((i) => <Col key={i} xs={24} md={8}><Card><Skeleton active paragraph={{ rows: 1 }} /></Card></Col>)}
      </Row>
    );
  }

  const revenue = rows.reduce((s, r) => s + Number(r.revenue || 0), 0);
  const orders = rows.reduce((s, r) => s + Number(r.orderCount || 0), 0);
  const last = rows[rows.length - 1];
  const range = rows.length ? `${formatMonth(rows[0].month)} – ${formatMonth(last.month)}` : 'Chưa có đơn';

  return (
    <Row gutter={gutter}>
      <Col xs={24} md={8}><Kpi label="Doanh thu 12 tháng" value={formatVND(revenue)} hint={range} /></Col>
      <Col xs={24} md={8}><Kpi label="Số đơn 12 tháng" value={formatInt(orders)} hint="Đơn CONFIRMED hoặc COMPLETED" /></Col>
      <Col xs={24} md={8}>
        <Kpi
          label={last ? `Tháng gần nhất (${formatMonth(last.month)})` : 'Tháng gần nhất'}
          value={last ? formatVND(last.revenue) : '—'}
          hint={last ? `${formatInt(last.orderCount)} đơn` : null}
        />
      </Col>
    </Row>
  );
}

function TopProductsCard() {
  const state = useReport('top-products', TOP_PARAMS);
  let body;
  if (state.error) body = <LoadError error={state.error} onRetry={state.reload} />;
  else if (state.loading && !state.meta) body = <Skeleton active paragraph={{ rows: TOP_LIMIT }} />;
  else {
    body = (
      <Table
        rowKey="productId"
        size="small"
        columns={topColumns}
        dataSource={state.data?.rows ?? []}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Chưa có sản phẩm nào được bán trong 12 tháng gần nhất' }}
      />
    );
  }
  return (
    <Card
      className="sf-report-card"
      title={<span className="sf-report-card__title">Top {TOP_LIMIT} sản phẩm bán chạy</span>}
      extra={<Link to="/admin/reports">Xem báo cáo <RightOutlined /></Link>}
    >
      {body}
    </Card>
  );
}

function QuickLinks({ menu }) {
  const navigate = useNavigate();
  const items = menu.filter((r) => r.path !== '/admin');
  return (
    <Row gutter={[tokens.space[4], tokens.space[4]]}>
      {items.map((r) => {
        const todo = r.menu.status === 'todo';
        return (
          <Col key={r.path} xs={24} sm={12} xl={8}>
            <Card
              hoverable
              role="link"
              tabIndex={0}
              aria-label={`Mở ${r.menu.label}`}
              onClick={() => navigate(r.path)}
              onKeyDown={(e) => { if (e.key === 'Enter') navigate(r.path); }}
              style={{ height: '100%' }}
            >
              <div style={{ display: 'grid', gap: tokens.space[2] }}>
                <Flex align="center" justify="space-between" gap={tokens.space[2]}>
                  <Typography.Text strong style={{ fontSize: tokens.text.heading[0] }}>
                    <span style={{ marginInlineEnd: tokens.space[2] }}>{r.menu.icon}</span>
                    {r.menu.label}
                  </Typography.Text>
                  <Tag color={todo ? 'warning' : 'success'}>{todo ? 'Đang làm' : 'Đã có'}</Tag>
                </Flex>
                <Typography.Text type="secondary">
                  {r.menu.group}{r.menu.owner ? ` · ${r.menu.owner}` : ''}
                </Typography.Text>
                <span className="sf-code">{r.path}</span>
              </div>
            </Card>
          </Col>
        );
      })}
    </Row>
  );
}

/** Trang chủ quản trị: chỉ số chính, top sản phẩm và lối tắt tới mọi khu quản trị. */
export default function AdminDashboardPage() {
  const { adminMenu = [] } = useOutletContext() ?? {};
  return (
    <div className="sf-reports-page">
      <div className="sf-reports-page__head">
        <Typography.Title level={3} style={{ margin: 0 }}>Tổng quan</Typography.Title>
        <Typography.Text type="secondary">
          Doanh thu và đơn hàng 12 tháng gần nhất (nguồn Oracle), sản phẩm bán chạy và lối tắt tới các khu quản trị.
        </Typography.Text>
      </div>
      <KpiRow />
      <TopProductsCard />
      <Typography.Title level={4} style={{ margin: 0 }}>Khu quản trị</Typography.Title>
      <QuickLinks menu={adminMenu} />
    </div>
  );
}
