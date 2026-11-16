import { useRef, useState } from 'react';
import { Button, Collapse, Space, Typography } from 'antd';
import { SwapOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { getReport } from '../../api';
import { formatInt, formatMs, formatRange, formatRatio } from '../../format';
import ErrorAlert from './ErrorAlert';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import Experiment from './Experiment';
import StaleAlert from './StaleAlert';
import Verdict from './Verdict';
import StatementView from './StatementView';
import { rangeParams, reportLabel, reportParams, runKey, settle } from './labUtils';

const RUNS = [
  { key: 'oracle', source: 'oracle', params: { joinHint: 'AUTO' } },
  { key: 'redis-agg', source: 'redis', params: { redisMode: 'AGGREGATE' } },
  { key: 'redis-client', source: 'redis', params: { redisMode: 'CLIENT' } },
];


// D4: AGGREGATE tính trên server Redis (1 round-trip); CLIENT kéo từng trang về Java tự gom (nhiều round-trip).
export default function RoundTripSection({ range }) {
  const [pick, setPick] = useState({ ...DEFAULT_PICK, report: 'revenue-by-category' });
  // { rows, key, report, range } của lần chạy gần nhất; bỏ đi khi bắt đầu chạy lại.
  const [result, setResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);
  const base = { ...rangeParams(range), ...reportParams(pick.report, pick) };
  const key = runKey(pick.report, base);

  const execute = async () => {
    const id = ++seq.current;
    setLoading(true);
    setResult(null);
    const ran = { key, report: pick.report, range };
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
    setResult({
      ...ran,
      rows: RUNS.map((r, i) => {
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
    });
    setLoading(false);
  };

  const rows = result?.rows;
  const agg = rows?.find((r) => r.key === 'redis-agg');
  const client = rows?.find((r) => r.key === 'redis-client');
  const bothOk = agg && client && !agg.error && !client.error && agg.roundTrips > 0 && client.roundTrips > 0;
  const timed = bothOk && agg.elapsedMs > 0 && client.elapsedMs > 0;
  const clientSlower = timed && client.elapsedMs >= agg.elapsedMs;

  const TILE = {
    oracle: { tone: 'oracle', label: 'Oracle · một câu SQL' },
    'redis-agg': { tone: 'redis', label: 'Redis · AGGREGATE (gom ở server)' },
    'redis-client': { tone: 'bad', label: 'Redis · CLIENT (kéo về app tự gom)' },
  };
  const tiles = rows?.map((r) => ({
    key: r.key,
    ...TILE[r.key],
    value: r.error ? 'Lỗi' : r.supported === false ? 'Không hỗ trợ' : formatMs(r.elapsedMs),
    caption: r.error ? r.error.message : `${formatInt(r.roundTrips)} round-trip · ${formatInt(r.rowCount)} dòng`,
  }));

  return (
    <Experiment
      description="Oracle, Redis gom trên server (FT.AGGREGATE) và Redis kéo dữ liệu về backend tự gom; chạy lần lượt."
      controls={
        <>
          <ReportPicker value={pick} onChange={setPick} />
          <Button type="primary" icon={<SwapOutlined />} loading={loading} onClick={execute}>
            Chạy 3 cách
          </Button>
        </>
      }
    >
      {(loading || result) && (
        <Verdict
          loading={loading}
          items={tiles}
          headline={
            result && (
              <>
                {reportLabel(result.report)} · {formatRange(result.range)}.{' '}
                {bothOk && (
                  <>
                    CLIENT cần <Typography.Text strong className="sf-num">{formatInt(client.roundTrips)}</Typography.Text> round-trip so với{' '}
                    <Typography.Text strong className="sf-num">{formatInt(agg.roundTrips)}</Typography.Text> của AGGREGATE
                    {timed && (
                      <>
                        , {clientSlower ? 'chậm hơn' : 'nhanh hơn'}{' '}
                        <Typography.Text strong className="sf-num">
                          {formatRatio(clientSlower ? client.elapsedMs / agg.elapsedMs : agg.elapsedMs / client.elapsedMs)}×
                        </Typography.Text>
                      </>
                    )}
                    .
                  </>
                )}
              </>
            )
          }
        />
      )}
      {result && <StaleAlert stale={result.key !== key} action="Chạy 3 cách" />}
      {rows && (
        <Collapse
          items={rows.map((r) => ({
            key: r.key,
            label: `Lệnh đã gửi: ${TILE[r.key].label}`,
            children: (
              <Space orientation="vertical" size={tokens.space[2]} className="sf-lab-full">
                <ErrorAlert error={r.error} />
                {r.note && <Typography.Text type="secondary">{r.note}</Typography.Text>}
                {!r.error && <StatementView statement={r.statement} />}
              </Space>
            ),
          }))}
        />
      )}
    </Experiment>
  );
}
