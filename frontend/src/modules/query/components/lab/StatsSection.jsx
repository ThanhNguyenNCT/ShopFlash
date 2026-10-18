import { useRef, useState } from 'react';
import { Button, Card, Col, Empty, Flex, Popconfirm, Row, Select, Space, Table, Tag, Typography } from 'antd';
import { CloudDownloadOutlined, DeleteOutlined, ReloadOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { JOIN_HINTS, changeOracleStats, getPlan } from '../../api';
import { formatInt, formatMs, formatRange } from '../../format';
import ErrorAlert from './ErrorAlert';
import PlanView from './PlanView';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import { HINT_LABEL, JOIN_REPORTS, formatDateTime, rangeParams, reportLabel, reportParams, runKey, useApi } from './labUtils';

const ACTION_LABEL = { DELETE: 'Sau khi xóa thống kê', GATHER: 'Sau khi thu thập thống kê' };

const STAT_COLUMNS = [
  { title: 'Bảng', dataIndex: 'table', render: (v) => <span className="sf-code">{v}</span> },
  {
    title: 'Số dòng (NUM_ROWS)',
    dataIndex: 'numRows',
    align: 'right',
    render: (v) => (v === null || v === undefined ? <Tag color="warning">chưa có</Tag> : <span className="sf-num">{formatInt(v)}</span>),
  },
  {
    title: 'Phân tích lần cuối',
    dataIndex: 'lastAnalyzed',
    render: (v) => {
      const t = formatDateTime(v);
      return t ? <span title={t.absolute}>{t.absolute} ({t.relative})</span> : <Tag color="warning">chưa có</Tag>;
    },
  },
];

// D3: optimizer của Oracle dựa vào thống kê. Xóa thống kê → Oracle phải đoán (dynamic sampling) → kế hoạch có thể đổi.
export default function StatsSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const [hint, setHint] = useState('AUTO');
  const stats = useApi(changeOracleStats);
  const [lastAction, setLastAction] = useState(null);
  // Hai kế hoạch gần nhất: [cũ, mới]. Mỗi lần xem lại thì đẩy kế hoạch mới vào, kế hoạch cũ vẫn hiện để so.
  // Chỉ so hai kế hoạch cùng báo cáo, cùng hint, cùng khoảng thời gian: đổi một trong số đó thì kế hoạch cũ bị ẩn.
  const [allPlans, setPlans] = useState([]);
  const params = { ...rangeParams(range), ...reportParams(pick.report, pick), source: 'oracle', joinHint: hint };
  const key = runKey(pick.report, params);
  const plans = allPlans.filter((p) => p.key === key);
  const [planLoading, setPlanLoading] = useState(false);
  const [planError, setPlanError] = useState(null);
  const seq = useRef(0);

  const doStats = async (action) => {
    const res = await stats.run(action);
    if (res) setLastAction(action);
  };

  const refetchPlan = async () => {
    const id = ++seq.current;
    setPlanLoading(true);
    setPlanError(null);
    try {
      const plan = await getPlan(pick.report, params);
      if (id !== seq.current) return;
      const label = lastAction ? ACTION_LABEL[lastAction] : 'Trước khi đổi thống kê';
      const entry = { plan, label, key, report: pick.report, hint, range, at: new Date().toISOString() };
      setPlans((prev) => [...prev.filter((p) => p.key === key), entry].slice(-2));
    } catch (e) {
      if (id === seq.current) setPlanError(e);
    } finally {
      if (id === seq.current) setPlanLoading(false);
    }
  };

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Optimizer của Oracle tính chi phí từ thống kê bảng (số dòng, phân bố giá trị). Khi xóa thống kê, Oracle phải lấy mẫu động
        (dynamic sampling) và ước lượng số dòng có thể sai, nên kế hoạch có thể đổi. Thứ tự demo: Xem lại kế hoạch → Xóa thống kê → Xem
        lại kế hoạch → Thu thập thống kê → Xem lại kế hoạch. Redis không có thống kê hay optimizer nên không có thí nghiệm tương ứng.
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[3]} align="center">
        <Popconfirm
          title="Xóa thống kê Oracle?"
          description="Xóa thống kê của ORDERS, ORDER_ITEMS, PRODUCTS, CATEGORIES, USERS. Thu thập lại bằng nút bên cạnh."
          okText="Xóa thống kê"
          okButtonProps={{ danger: true }}
          cancelText="Hủy"
          onConfirm={() => doStats('DELETE')}
        >
          <Button danger icon={<DeleteOutlined />} loading={stats.loading}>
            Xóa thống kê
          </Button>
        </Popconfirm>
        <Button icon={<CloudDownloadOutlined />} loading={stats.loading} onClick={() => doStats('GATHER')}>
          Thu thập thống kê
        </Button>
      </Flex>
      <ErrorAlert error={stats.error} title="Không đổi được thống kê" />
      <Card
        size="small"
        title="Thống kê bảng"
        extra={stats.data && <Typography.Text type="secondary" className="sf-num">{stats.data.action} · {formatMs(stats.data.elapsedMs)}</Typography.Text>}
      >
        {stats.data || stats.loading ? (
          <Table size="small" columns={STAT_COLUMNS} dataSource={stats.data?.tables ?? []} rowKey="table" pagination={false} scroll={{ x: 'max-content' }} loading={stats.loading} />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Bấm Xóa hoặc Thu thập thống kê để xem số liệu từng bảng" />
        )}
      </Card>
      <Flex wrap gap={tokens.space[3]} align="center">
        <ReportPicker value={pick} onChange={setPick} reports={JOIN_REPORTS} />
        <Select aria-label="Hint" value={hint} onChange={setHint} options={JOIN_HINTS.map((h) => ({ value: h, label: HINT_LABEL[h] }))} />
        <Button type="primary" icon={<ReloadOutlined />} loading={planLoading} onClick={refetchPlan}>
          Xem lại kế hoạch
        </Button>
      </Flex>
      <ErrorAlert error={planError} title="Không lấy được kế hoạch" />
      <Row gutter={[tokens.space[4], tokens.space[4]]}>
        {plans.length === 0 && (
          <Col xs={24}>
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                allPlans.length > 0
                  ? 'Báo cáo, hint hoặc khoảng thời gian đã đổi nên kế hoạch cũ được ẩn. Bấm Xem lại kế hoạch.'
                  : 'Chưa có kế hoạch. Bấm Xem lại kế hoạch.'
              }
            />
          </Col>
        )}
        {plans.map((p, i) => (
          <Col xs={24} xl={12} key={p.at}>
            <Card
              size="small"
              title={`${plans.length === 2 && i === 0 ? 'Trước' : 'Mới nhất'}: ${p.label}`}
              extra={<Typography.Text type="secondary" className="sf-num">{formatDateTime(p.at)?.absolute}</Typography.Text>}
            >
              <Typography.Paragraph type="secondary">
                {reportLabel(p.report)} · hint {p.hint} · {formatRange(p.range)}
              </Typography.Paragraph>
              <PlanView lines={p.plan.lines} />
            </Card>
          </Col>
        ))}
      </Row>
    </Space>
  );
}
