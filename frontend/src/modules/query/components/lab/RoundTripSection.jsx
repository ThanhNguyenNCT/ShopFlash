import { useRef, useState } from 'react';
import { Button, Flex, Space, Table, Tag, Typography } from 'antd';
import { SwapOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { getReport } from '../../api';
import ErrorAlert from './ErrorAlert';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import StatementView from './StatementView';
import { HINT_LABEL, formatCount, formatMs, rangeParams, reportParams, settle, sourceLabel } from './labUtils';

const RUNS = [
  { key: 'oracle', source: 'oracle', params: { joinHint: 'AUTO' } },
  { key: 'redis-agg', source: 'redis', params: { redisMode: 'AGGREGATE' } },
  { key: 'redis-client', source: 'redis', params: { redisMode: 'CLIENT' } },
];

const COLUMNS = [
  { title: 'Nguồn', dataIndex: 'source', render: (v) => <Tag>{sourceLabel(v)}</Tag> },
  { title: 'Chế độ', dataIndex: 'variant', render: (v) => HINT_LABEL[v] ?? v },
  { title: 'Thời gian', dataIndex: 'elapsedMs', align: 'right', render: (v) => <span className="sf-num">{formatMs(v)}</span> },
  { title: 'Round-trip', dataIndex: 'roundTrips', align: 'right', render: (v) => <span className="sf-num">{formatCount(v)}</span> },
  { title: 'Số dòng', dataIndex: 'rowCount', align: 'right', render: (v) => <span className="sf-num">{formatCount(v)}</span> },
  {
    title: 'Trạng thái',
    key: 'status',
    render: (_, r) => (r.error ? <Tag color="error">Lỗi</Tag> : r.supported === false ? <Tag color="warning">Không hỗ trợ</Tag> : <Tag color="success">OK</Tag>),
  },
];

// D4: AGGREGATE tính trên server Redis (1 round-trip); CLIENT kéo từng trang về Java tự gom (nhiều round-trip).
export default function RoundTripSection({ range }) {
  const [pick, setPick] = useState({ ...DEFAULT_PICK, report: 'revenue-by-category' });
  const [rows, setRows] = useState(null);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);

  const execute = async () => {
    const id = ++seq.current;
    setLoading(true);
    const base = { ...rangeParams(range), ...reportParams(pick.report, pick) };
    // Chạy lần lượt, không song song: gọi cùng lúc thì các cách tranh CPU của máy với nhau và thời gian bị đội lên.
    const results = [];
    for (const r of RUNS) {
      try {
        results.push({ status: 'fulfilled', value: await getReport(pick.report, { ...base, source: r.source, ...r.params }) });
      } catch (reason) {
        results.push({ status: 'rejected', reason });
      }
    }
    if (id !== seq.current) return;
    setRows(
      RUNS.map((r, i) => {
        const { value, error } = settle(results[i]);
        const meta = value?.meta ?? {};
        return {
          key: r.key,
          source: r.source,
          variant: meta.variant ?? r.params.joinHint ?? r.params.redisMode,
          elapsedMs: meta.elapsedMs,
          roundTrips: meta.roundTrips,
          rowCount: meta.rowCount,
          supported: meta.supported,
          statement: meta.statement,
          note: meta.note,
          error,
        };
      }),
    );
    setLoading(false);
  };

  const agg = rows?.find((r) => r.key === 'redis-agg');
  const client = rows?.find((r) => r.key === 'redis-client');

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Oracle trả kết quả của cả câu SQL trong một lần gọi. Với Redis có hai cách: AGGREGATE đẩy việc gom nhóm cho FT.AGGREGATE trên
        server (1 round-trip), còn CLIENT đọc dữ liệu thô bằng con trỏ (FT.AGGREGATE … WITHCURSOR, 10.000 dòng mỗi lần) về Java rồi
        tự gom và cộng — mỗi lần đọc là một round-trip qua mạng. Ba cách được chạy lần lượt để thời gian không ảnh hưởng nhau. Cùng kết quả nhưng số lượt gọi và thời gian khác hẳn.
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[3]} align="center">
        <ReportPicker value={pick} onChange={setPick} />
        <Button type="primary" icon={<SwapOutlined />} loading={loading} onClick={execute}>
          Chạy 3 cách
        </Button>
      </Flex>
      {rows && (
        <Table
          size="small"
          columns={COLUMNS}
          dataSource={rows}
          loading={loading}
          pagination={false}
          scroll={{ x: 'max-content' }}
          expandable={{
            expandedRowRender: (r) => (
              <Space orientation="vertical" size={tokens.space[2]} className="sf-lab-full">
                <ErrorAlert error={r.error} />
                {r.note && <Typography.Text type="secondary">{r.note}</Typography.Text>}
                {!r.error && <StatementView statement={r.statement} />}
              </Space>
            ),
          }}
        />
      )}
      {agg?.roundTrips && client?.roundTrips && !agg.error && !client.error && (
        <Typography.Text>
          CLIENT cần <Typography.Text strong className="sf-num">{formatCount(client.roundTrips)}</Typography.Text> round-trip so với{' '}
          <Typography.Text strong className="sf-num">{formatCount(agg.roundTrips)}</Typography.Text> của AGGREGATE
          {agg.elapsedMs > 0 && client.elapsedMs > 0 && (
            <>, chậm hơn <Typography.Text strong className="sf-num">{(client.elapsedMs / agg.elapsedMs).toLocaleString('vi-VN', { maximumFractionDigits: 1 })}</Typography.Text> lần</>
          )}
          . Mở từng dòng để xem lệnh đã gửi.
        </Typography.Text>
      )}
    </Space>
  );
}
