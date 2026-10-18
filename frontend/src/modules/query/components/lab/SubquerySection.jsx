import { useRef, useState } from 'react';
import { Alert, Button, Card, Col, Flex, Row, Space, Spin, Statistic, Typography } from 'antd';
import { BranchesOutlined } from '@ant-design/icons';
import { formatVND, tokens } from '@/shared/design-system';
import { getReport } from '../../api';
import { formatInt, formatMs, formatRange } from '../../format';
import ErrorAlert from './ErrorAlert';
import ResultTable from './ResultTable';
import StaleAlert from './StaleAlert';
import StatementView from './StatementView';
import { rangeParams, runKey, sourceLabel } from './labUtils';

const PAGE = { page: 1, size: 20 };

function SideCard({ report, source, slot, loading }) {
  const meta = slot?.value?.meta;
  const rows = slot?.value?.data?.rows;
  return (
    <Card size="small" title={`${sourceLabel(source)}${meta ? ` · ${formatInt(meta.roundTrips)} round-trip` : ''}`}>
      <Spin spinning={loading}>
        <Space orientation="vertical" size={tokens.space[3]} className="sf-lab-full">
          <ErrorAlert error={slot?.error} />
          {meta && (
            <Flex wrap gap={tokens.space[6]}>
              <Statistic title="Round-trip" value={formatInt(meta.roundTrips)} />
              <Statistic title="Thời gian" value={formatMs(meta.elapsedMs)} />
              {slot.value.data?.summary && (
                <Statistic title="Chi tiêu trung bình" value={formatVND(slot.value.data.summary.averageSpent)} />
              )}
            </Flex>
          )}
          {meta?.supported === false && (
            <Alert type="warning" showIcon title={`${sourceLabel(source)} không hỗ trợ`} description={meta.note} />
          )}
          {meta?.supported !== false && meta && (
            <StatementView statement={meta.statement} label={source === 'redis' ? 'Lệnh Redis (mỗi dòng một lệnh)' : 'Một câu SQL'} />
          )}
          {rows && <ResultTable report={report} rows={rows} />}
        </Space>
      </Spin>
    </Card>
  );
}

// Chạy một nguồn; lỗi được giữ lại thành { value, error } để khung của nguồn đó tự hiện.
async function runSource(report, params) {
  try {
    return { value: await getReport(report, params), error: null };
  } catch (error) {
    return { value: null, error };
  }
}

function usePair(report, range) {
  const [state, setState] = useState(null);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);
  const base = { ...rangeParams(range), ...PAGE };
  const key = runKey(report, base);
  const run = async () => {
    const id = ++seq.current;
    setLoading(true);
    setState(null);
    const ran = { key, range };
    // Lần lượt Oracle rồi Redis: chạy song song thì hai bên tranh CPU, thời gian đặt cạnh nhau sẽ sai.
    const oracle = await runSource(report, { ...base, source: 'oracle' });
    const redis = await runSource(report, { ...base, source: 'redis' });
    if (id !== seq.current) return;
    setState({ ...ran, oracle, redis });
    setLoading(false);
  };
  return { state, loading, run, stale: !!state && state.key !== key };
}

// Khoảng thời gian của lần chạy + cảnh báo khi tham số đã đổi sau đó.
function PairHeader({ pair, datelessNote }) {
  if (!pair.state) return null;
  return (
    <Space orientation="vertical" size={tokens.space[2]} className="sf-lab-full" style={{ marginBottom: tokens.space[4] }}>
      <StaleAlert stale={pair.stale} action="Chạy trên hai nguồn" />
      <Typography.Text type="secondary">
        {datelessNote ?? `Khoảng thời gian: ${formatRange(pair.state.range)}`}
      </Typography.Text>
    </Space>
  );
}

// D5: Oracle viết subquery/anti-join trong một câu; Redis không có subquery nên phải tách lượt hoặc không làm được.
export default function SubquerySection({ range }) {
  const vip = usePair('vip-customers', range);
  // Báo cáo này không theo khoảng ngày (backend bỏ qua from/to) nên không gửi, và đổi ngày không làm kết quả cũ đi.
  const unsold = usePair('unsold-products', null);

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        SQL cho phép lồng câu truy vấn: khách VIP là khách có tổng chi lớn hơn trung bình, tính bằng{' '}
        <Typography.Text code>HAVING SUM(...) &gt; (SELECT AVG(...))</Typography.Text> trong một câu, Oracle gửi một lần. Redis không có
        subquery: phải gọi một lần để lấy trung bình, rồi gọi lần nữa để lọc (ít nhất 2 round-trip). Với “sản phẩm chưa từng bán” (
        <Typography.Text code>NOT EXISTS</Typography.Text>, anti-join), Redis không có cách nào trên read model hiện có.
      </Typography.Paragraph>

      <Card title="Khách VIP (subquery vô hướng)">
        <Flex justify="flex-end" style={{ marginBottom: tokens.space[4] }}>
          <Button type="primary" icon={<BranchesOutlined />} loading={vip.loading} onClick={vip.run}>
            Chạy trên hai nguồn
          </Button>
        </Flex>
        <PairHeader pair={vip} />
        <Row gutter={[tokens.space[4], tokens.space[4]]}>
          <Col xs={24} xl={12}>
            <SideCard report="vip-customers" source="oracle" slot={vip.state?.oracle} loading={vip.loading} />
          </Col>
          <Col xs={24} xl={12}>
            <SideCard report="vip-customers" source="redis" slot={vip.state?.redis} loading={vip.loading} />
          </Col>
        </Row>
      </Card>

      <Card title="Sản phẩm chưa từng bán (NOT EXISTS)">
        <Flex justify="flex-end" style={{ marginBottom: tokens.space[4] }}>
          <Button icon={<BranchesOutlined />} loading={unsold.loading} onClick={unsold.run}>
            Chạy trên hai nguồn
          </Button>
        </Flex>
        <PairHeader pair={unsold} datelessNote="Báo cáo này tính trên toàn bộ dữ liệu, không theo khoảng thời gian." />
        <Row gutter={[tokens.space[4], tokens.space[4]]}>
          <Col xs={24} xl={12}>
            <SideCard report="unsold-products" source="oracle" slot={unsold.state?.oracle} loading={unsold.loading} />
          </Col>
          <Col xs={24} xl={12}>
            <SideCard report="unsold-products" source="redis" slot={unsold.state?.redis} loading={unsold.loading} />
          </Col>
        </Row>
      </Card>
    </Space>
  );
}
