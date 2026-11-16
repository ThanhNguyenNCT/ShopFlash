import { Flex, Table, Typography } from 'antd';
import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { tokens } from '@/shared/design-system';
import { formatNumber } from '../format';

// Biểu đồ "quét theo C" của Concurrency Lab: throughput và p95 của từng chiến lược khi số lượt đồng thời tăng.
// Bảng màu của design system có ít màu, nên chuỗi được phân biệt bằng màu VÀ kiểu nét: Oracle nét liền, Redis nét đứt.
// Số liệu đầy đủ (kèm độ lệch chuẩn và số bán được) nằm ở bảng dưới biểu đồ.

const { color, text, space } = tokens;
const ORACLE_COLORS = [color.brand, color.ink, color.success, color.warning, color.info, color.inkMuted];
const REDIS_COLORS = [color.brand, color.ink, color.success, color.info];
const axisTick = { fontSize: text.caption[0], fill: color.inkMuted };

function seriesStyle(strategies) {
  let oracle = 0;
  let redis = 0;
  return Object.fromEntries(strategies.map((s) => {
    const isRedis = s.startsWith('REDIS');
    const stroke = isRedis ? REDIS_COLORS[redis++ % REDIS_COLORS.length] : ORACLE_COLORS[oracle++ % ORACLE_COLORS.length];
    return [s, { stroke, strokeDasharray: isRedis ? '6 4' : undefined }];
  }));
}

function SweepTooltip({ active, label, payload, unit, digits }) {
  if (!active || !payload?.length) return null;
  const rows = [...payload].sort((a, b) => b.value - a.value);
  return (
    <div className="sf-cc-chart-tooltip">
      <strong>C = {label}</strong>
      {rows.map((row) => (
        <div key={row.dataKey} className="sf-cc-chart-tooltip__row">
          <span><span className="sf-cc-chart-tooltip__swatch" style={{ background: row.stroke }} />{row.dataKey}</span>
          <strong className="sf-num">{formatNumber(row.value, digits)} {unit}</strong>
        </div>
      ))}
    </div>
  );
}

function SweepChart({ title, data, strategies, styles, unit, digits }) {
  return (
    <div className="sf-cc-chart">
      <Typography.Text strong>{title}</Typography.Text>
      <div className="sf-cc-chart__plot" role="img" aria-label={`${title} theo số lượt đồng thời C; số liệu ở bảng bên dưới`}>
        <ResponsiveContainer width="100%" height="100%">
          <LineChart data={data} margin={{ top: space[2], right: space[4], bottom: space[2], left: space[2] }}>
            <CartesianGrid vertical={false} stroke={color.line} />
            <XAxis dataKey="c" tick={axisTick} stroke={color.lineStrong} tickFormatter={(v) => `C = ${v}`} />
            <YAxis scale="log" domain={['auto', 'auto']} allowDataOverflow tick={axisTick} stroke={color.lineStrong}
              width={space[8] * 2} tickFormatter={(v) => formatNumber(v, v < 10 ? 1 : 0)} />
            <Tooltip content={<SweepTooltip unit={unit} digits={digits} />} />
            <Legend wrapperStyle={{ fontSize: text.caption[0] }} />
            {strategies.map((s) => (
              <Line key={s} type="linear" dataKey={s} stroke={styles[s].stroke} strokeDasharray={styles[s].strokeDasharray}
                strokeWidth={2} dot={{ r: 3 }} isAnimationActive={false} />
            ))}
          </LineChart>
        </ResponsiveContainer>
      </div>
    </div>
  );
}

/** sweep: [{ concurrency, result }] với result là response của POST /concurrency-lab/runs. */
export default function LabSweepCharts({ sweep }) {
  if (!sweep.length) return null;
  const strategies = sweep[0].result.results.map((r) => r.strategy);
  const styles = seriesStyle(strategies);
  const pick = (metric) => sweep.map(({ concurrency, result }) => ({
    c: String(concurrency),
    ...Object.fromEntries(result.results.map((r) => [r.strategy, Math.max(r.summary[metric].mean, 0.001)])),
  }));
  const { buyers, quota } = sweep[0].result.config;

  const rows = strategies.flatMap((strategy) => sweep.map(({ concurrency, result }) => {
    const r = result.results.find((x) => x.strategy === strategy);
    return { key: `${strategy}-${concurrency}`, strategy, concurrency, summary: r.summary };
  }));
  const stat = (s, digits) => (s.sd === 0 ? formatNumber(s.mean, digits) : `${formatNumber(s.mean, digits)} ± ${formatNumber(s.sd, digits)}`);
  const columns = [
    { title: 'Chiến lược', dataIndex: 'strategy', render: (v) => <span className="sf-cc-code">{v}</span> },
    { title: 'C', dataIndex: 'concurrency', align: 'right', render: (v) => <span className="sf-num">{v}</span> },
    { title: 'Bán được', key: 'sold', align: 'right', render: (_, r) => <span className="sf-num">{stat(r.summary.finalSold, 0)}</span> },
    { title: 'Lượt lỗi', key: 'errors', align: 'right', render: (_, r) => <span className="sf-num">{stat(r.summary.errors, 0)}</span> },
    { title: 'Lượt/giây', key: 'tp', align: 'right', render: (_, r) => <span className="sf-num">{stat(r.summary.throughput, 0)}</span> },
    { title: 'p95 (ms)', key: 'p95', align: 'right', render: (_, r) => <span className="sf-num">{stat(r.summary.p95Ms, 1)}</span> },
  ];

  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Text type="secondary">
        N = {formatNumber(buyers)}, M = {formatNumber(quota)}. Trục dọc theo thang log. Oracle nét liền, Redis nét đứt.
        Throughput tính trên mọi lượt, kể cả lượt lỗi; xem cột "Bán được" ở bảng để biết chiến lược nào bán thiếu.
      </Typography.Text>
      <div className="sf-cc-charts">
        <SweepChart title="Throughput (lượt/giây)" data={pick('throughput')} strategies={strategies} styles={styles} unit="lượt/giây" digits={0} />
        <SweepChart title="Độ trễ p95 (ms)" data={pick('p95Ms')} strategies={strategies} styles={styles} unit="ms" digits={1} />
      </div>
      <Table size="small" columns={columns} dataSource={rows} pagination={{ pageSize: sweep.length * 2, hideOnSinglePage: true }}
        scroll={{ x: 'max-content' }} />
    </Flex>
  );
}
