import { Alert, Button, Card, Checkbox, Flex, Form, InputNumber, Select, Space, Table, Tag, Typography } from 'antd';
import { DashboardOutlined, DownloadOutlined } from '@ant-design/icons';
import { Bar, BarChart, CartesianGrid, Cell, ErrorBar, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import dayjs from 'dayjs';
import { tokens } from '@/shared/design-system';
import { JOIN_HINTS, REDIS_MODES, REPORTS, runBenchmark } from '../../api';
import { formatInt, formatMs, formatRange } from '../../format';
import ErrorAlert from './ErrorAlert';
import StaleAlert from './StaleAlert';
import { rangeParams, reportLabel, runKey, sourceLabel, useApi } from './labUtils';

const SOURCE_COLOR = { oracle: tokens.color.brand, redis: tokens.color.info };
const ALL_VARIANTS = [
  ...JOIN_HINTS.map((v) => ({ source: 'oracle', variant: v })),
  ...REDIS_MODES.map((v) => ({ source: 'redis', variant: v })),
];
const keyOf = (v) => `${v.source}:${v.variant}`;
const DEFAULT_VARIANTS = ['oracle:AUTO', 'oracle:NL', 'redis:AGGREGATE', 'redis:CLIENT'];

const TABLE_COLUMNS = [
  { title: 'Nguồn', dataIndex: 'source', render: (v) => <Tag><span className={`sf-lab-swatch sf-lab-swatch--${v}`} />{sourceLabel(v)}</Tag> },
  { title: 'Biến thể', dataIndex: 'variant', render: (v) => <span className="sf-code">{v}</span> },
  { title: 'Số lần', dataIndex: 'runs', align: 'right', render: (v) => <span className="sf-num">{v}</span> },
  {
    title: 'Trung bình ± độ lệch',
    key: 'mean',
    align: 'right',
    sorter: (a, b) => a.meanMs - b.meanMs,
    defaultSortOrder: 'ascend',
    render: (_, r) => <span className="sf-num">{formatMs(r.meanMs)} ± {formatMs(r.stdDevMs)}</span>,
  },
  { title: 'p50', dataIndex: 'p50Ms', align: 'right', render: (v) => <span className="sf-num">{formatMs(v)}</span> },
  { title: 'p95', dataIndex: 'p95Ms', align: 'right', render: (v) => <span className="sf-num">{formatMs(v)}</span> },
  { title: 'Round-trip', dataIndex: 'roundTrips', align: 'right', render: (v) => <span className="sf-num">{formatInt(v)}</span> },
];

const axisTick = { fontSize: tokens.text.caption[0], fill: tokens.color.inkMuted };

function ChartTooltip({ active, payload }) {
  if (!active || !payload?.length) return null;
  const d = payload[0].payload;
  return (
    <Card size="small">
      <Typography.Text strong>{d.name}</Typography.Text>
      <div className="sf-num">Trung bình: {formatMs(d.meanMs)}</div>
      <div className="sf-num">Độ lệch chuẩn: {formatMs(d.stdDevMs)}</div>
      <div className="sf-num">p95: {formatMs(d.p95Ms)}</div>
    </Card>
  );
}

function BenchmarkChart({ results }) {
  const data = results.map((r) => ({ ...r, name: `${sourceLabel(r.source)} ${r.variant}` }));
  return (
    <figure style={{ margin: 0 }} aria-label="Biểu đồ thời gian trung bình theo biến thể">
      <ResponsiveContainer width="100%" height={tokens.space[8] * 10}>
        <BarChart data={data} margin={{ top: tokens.space[4], right: tokens.space[2], left: 0, bottom: 0 }}>
          <CartesianGrid vertical={false} stroke={tokens.color.line} />
          <XAxis dataKey="name" tick={axisTick} interval={0} angle={-20} textAnchor="end" height={tokens.space[8] * 2} stroke={tokens.color.lineStrong} />
          <YAxis tick={axisTick} stroke={tokens.color.lineStrong} unit=" ms" width={tokens.space[8] * 2 + tokens.space[4]} />
          <Tooltip content={<ChartTooltip />} cursor={{ fill: tokens.color.surfaceSunken }} />
          <Bar dataKey="meanMs" name="Trung bình (ms)" radius={[tokens.radius.sm, tokens.radius.sm, 0, 0]} maxBarSize={tokens.space[8] * 2}>
            {data.map((d) => (
              <Cell key={d.name} fill={SOURCE_COLOR[d.source]} />
            ))}
            <ErrorBar dataKey="stdDevMs" width={tokens.space[2]} strokeWidth={1.5} stroke={tokens.color.ink} />
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <figcaption>
        <Flex wrap gap={tokens.space[4]} justify="center">
          <Typography.Text><span className="sf-lab-swatch sf-lab-swatch--oracle" />Oracle</Typography.Text>
          <Typography.Text><span className="sf-lab-swatch sf-lab-swatch--redis" />Redis</Typography.Text>
          <Typography.Text type="secondary">Thanh lỗi: ± 1 độ lệch chuẩn</Typography.Text>
        </Flex>
      </figcaption>
    </figure>
  );
}

// CSV cùng cột với CSV của backend (mỗi mẫu một dòng), dựng từ kết quả đang hiện nên khớp bảng và không phải đo lại.
const CSV_HEADER = 'report,source,variant,run,elapsedMs,roundTrips';

function toCsv({ report, results }) {
  const lines = results.flatMap((r) =>
    (r.samplesMs ?? []).map((ms, i) => [report, r.source, r.variant, i + 1, ms, r.roundTrips].join(',')));
  return `${[CSV_HEADER, ...lines].join('\n')}\n`;
}

function downloadCsv(result) {
  const blob = new Blob([toCsv(result)], { type: 'text/csv;charset=utf-8' });
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = `benchmark-${result.report}-${dayjs().format('YYYYMMDD-HHmmss')}.csv`;
  document.body.appendChild(a);
  a.click();
  a.remove();
  URL.revokeObjectURL(url);
}

// Gắn tham số của lần đo vào kết quả: tiêu đề và cảnh báo "tham số đã đổi" luôn khớp với số liệu đang hiện,
// và chỉ có khi đo thành công.
const measure = async (report, body, run) => ({ ...(await runBenchmark(report, body)), run });

// Đo hiệu năng: chạy lặp nhiều lần mỗi biến thể trên backend, trả thống kê thời gian.
export default function BenchmarkSection({ range }) {
  const [form] = Form.useForm();
  const values = Form.useWatch([], form);
  const report = values?.report ?? 'top-products';
  const bench = useApi(measure);
  const reportMeta = REPORTS.find((r) => r.slug === report);

  const variantOptions = ALL_VARIANTS.map((v) => ({
    value: keyOf(v),
    label: `${sourceLabel(v.source)} ${v.variant}`,
    disabled: v.source === 'redis' && report === 'unsold-products',
  }));

  const buildBody = (v) => ({
    ...rangeParams(range),
    ...(v.report === 'top-products' ? { limit: v.limit } : {}),
    warmup: v.warmup,
    runs: v.runs,
    variants: (v.variants ?? [])
      .filter((k) => !(v.report === 'unsold-products' && k.startsWith('redis:')))
      .map((k) => {
        const [source, variant] = k.split(':');
        return { source, variant };
      }),
  });

  const keyOfRun = (v) => runKey(v.report, buildBody(v));
  const currentKey = values ? keyOfRun(values) : null;

  const onFinish = async (v) => {
    const body = buildBody(v);
    await bench.run(v.report, body, { key: keyOfRun(v), report: v.report, body, range });
  };

  const results = bench.data?.results ?? [];
  const lastRun = bench.data?.run;
  const stale = results.length > 0 && lastRun?.key !== currentKey;

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Mỗi biến thể chạy vài lần khởi động (không tính) rồi chạy đo nhiều lần trên backend; thời gian chỉ gồm gọi DB và xử lý kết quả,
        không tính HTTP. So sánh trung bình kèm độ lệch chuẩn để thấy chênh lệch có thật hay chỉ là dao động.
      </Typography.Paragraph>
      <Alert type="warning" showIcon title="Đo hiệu năng có thể mất vài chục giây đến vài phút (số lần × số biến thể). Không bấm lại khi đang chạy." />
      <Form
        form={form}
        layout="vertical"
        initialValues={{ report: 'top-products', limit: 10, runs: 5, warmup: 1, variants: DEFAULT_VARIANTS }}
        onFinish={onFinish}
        disabled={bench.loading}
      >
        <Flex wrap gap={tokens.space[4]}>
          <Form.Item name="report" label="Báo cáo" rules={[{ required: true, message: 'Chọn một báo cáo' }]}>
            <Select style={{ minWidth: tokens.space[8] * 7 }} options={REPORTS.map((r) => ({ value: r.slug, label: r.label }))} />
          </Form.Item>
          {report === 'top-products' && (
            <Form.Item name="limit" label="Top" rules={[{ required: true, type: 'integer', min: 1, max: 100, message: 'Từ 1 đến 100' }]}>
              <InputNumber min={1} max={100} precision={0} />
            </Form.Item>
          )}
          <Form.Item name="runs" label="Số lần đo" rules={[{ required: true, type: 'integer', min: 1, max: 30, message: 'Từ 1 đến 30 lần' }]}>
            <InputNumber min={1} max={30} precision={0} />
          </Form.Item>
          <Form.Item name="warmup" label="Khởi động" rules={[{ required: true, type: 'integer', min: 0, max: 5, message: 'Từ 0 đến 5 lần' }]}>
            <InputNumber min={0} max={5} precision={0} />
          </Form.Item>
        </Flex>
        <Form.Item
          name="variants"
          label="Biến thể"
          dependencies={['report']}
          rules={[
            ({ getFieldValue }) => ({
              validator: (_, value = []) => {
                const usable = getFieldValue('report') === 'unsold-products' ? value.filter((k) => k.startsWith('oracle:')) : value;
                return usable.length > 0 ? Promise.resolve() : Promise.reject(new Error('Chọn ít nhất một biến thể dùng được cho báo cáo này'));
              },
            }),
          ]}
          extra={
            report === 'unsold-products'
              ? 'Redis không hỗ trợ báo cáo này nên chỉ đo Oracle.'
              : reportMeta && !reportMeta.joinHint
                ? 'Báo cáo này không có join: các hint Oracle cho cùng một kế hoạch.'
                : null
          }
        >
          <Checkbox.Group options={variantOptions} />
        </Form.Item>
        <Flex wrap gap={tokens.space[3]}>
          <Button type="primary" htmlType="submit" icon={<DashboardOutlined />} loading={bench.loading}>
            {bench.loading ? 'Đang đo…' : 'Chạy đo'}
          </Button>
          <Button icon={<DownloadOutlined />} onClick={() => downloadCsv(bench.data)} disabled={bench.loading || results.length === 0}>
            Tải CSV
          </Button>
        </Flex>
      </Form>
      <ErrorAlert error={bench.error} title="Đo hiệu năng thất bại" />
      {(results.length > 0 || bench.loading) && (
        <Card
          size="small"
          title={
            !bench.loading && lastRun
              ? `${reportLabel(lastRun.report)} · ${formatRange(lastRun.range)} · ${lastRun.body.runs} lần đo, ${lastRun.body.warmup} lần khởi động`
              : 'Đang đo…'
          }
        >
          <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
            <StaleAlert stale={stale} action="Chạy đo" />
            <Table
              size="small"
              columns={TABLE_COLUMNS}
              dataSource={results}
              rowKey={keyOf}
              loading={bench.loading}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
            {results.length > 0 && <BenchmarkChart results={results} />}
          </Space>
        </Card>
      )}
    </Space>
  );
}
