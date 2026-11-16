import { useCallback, useEffect, useRef, useState } from 'react';
import {
  Alert, App, Button, Card, Checkbox, Descriptions, Flex, Form, InputNumber, Table, Tag, Typography,
} from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { getLabState, resetLab, runLab } from '../api';
import LabSweepCharts from '../components/LabSweepCharts';
import { formatNumber } from '../format';
import '../concurrency.css';

// Concurrency Lab (TV5, admin): một "hot item" có M suất, N người cùng mua (mỗi người 1 suất), backend chạy trên C
// thread với cổng xuất phát chung. So sánh 10 cách kiểm soát đồng thời trên Oracle và Redis.
// Contract và ý nghĩa số đo: backend/.../modules/concurrency/AGENTS.md, mục "Concurrency Lab".

const STRATEGIES = [
  { value: 'ORACLE_NAIVE', store: 'Oracle', label: 'Đọc rồi ghi, không khóa', expect: 'Bán vượt' },
  { value: 'ORACLE_PESSIMISTIC', store: 'Oracle', label: 'SELECT … FOR UPDATE', expect: 'Đúng, các lượt xếp hàng chờ khóa' },
  { value: 'ORACLE_NOWAIT', store: 'Oracle', label: 'FOR UPDATE NOWAIT', expect: 'Đúng, gặp khóa thì lỗi ngay ORA-00054' },
  { value: 'ORACLE_OPTIMISTIC', store: 'Oracle', label: 'Optimistic theo version', expect: 'Đúng, thử lại nhiều' },
  { value: 'ORACLE_ATOMIC', store: 'Oracle', label: 'Một câu UPDATE có điều kiện', expect: 'Đúng, 1 round-trip' },
  { value: 'ORACLE_SERIALIZABLE', store: 'Oracle', label: 'Mức cô lập SERIALIZABLE', expect: 'Đúng, nhiều ORA-08177' },
  { value: 'REDIS_NAIVE', store: 'Redis', label: 'HGET rồi HSET', expect: 'Bán vượt' },
  { value: 'REDIS_WATCH', store: 'Redis', label: 'WATCH / MULTI / EXEC', expect: 'Đúng, thử lại nhiều' },
  { value: 'REDIS_DECR', store: 'Redis', label: 'HINCRBY rồi bù lại', expect: 'Đúng số bán, bộ đếm có lúc âm' },
  { value: 'REDIS_LUA', store: 'Redis', label: 'Lua script', expect: 'Đúng, 1 round-trip' },
];
const BY_VALUE = Object.fromEntries(STRATEGIES.map((s) => [s.value, s]));
const ALL = STRATEGIES.map((s) => s.value);

const DEFAULTS = { strategies: ALL, buyers: 1000, quota: 100, concurrency: 64, runs: 5, warmup: 1 };
const STATE_POLL_MS = 1000;
// Các mức C của thí nghiệm E2 (báo cáo mục 3.5).
const SWEEP_CONCURRENCY = [1, 8, 32, 64, 128];

/** "12,3 ± 1,2": trung bình và độ lệch chuẩn mẫu qua các lần đo. */
function meanSd(stat, digits = 1) {
  if (!stat) return '–';
  const integral = Number.isInteger(stat.mean) && stat.sd === 0;
  const mean = formatNumber(stat.mean, integral ? 0 : digits);
  return stat.sd === 0 ? mean : `${mean} ± ${formatNumber(stat.sd, digits)}`;
}

const num = (v, digits = 0) => <span className="sf-num">{formatNumber(v, digits)}</span>;

function StateCard({ state, onReload, onReset, resetting }) {
  const oracle = state?.oracle;
  const redis = state?.redis;
  return (
    <Card size="small" title="Bộ đếm hiện tại" extra={<Button size="small" icon={<ReloadOutlined />} onClick={onReload}>Tải lại</Button>}>
      <Flex vertical gap={tokens.space[3]}>
        <Descriptions size="small" column={{ xs: 1, md: 2 }} bordered items={[
          {
            key: 'oracle', label: 'Oracle (cc_lab_items)',
            children: oracle ? <>phân bổ {num(oracle.allocated)} · đã bán {num(oracle.sold)} · version {num(oracle.version)}</> : 'Chưa đặt lại lần nào',
          },
          {
            key: 'redis', label: 'Redis (cclab:item)',
            children: redis ? <>phân bổ {num(redis.allocated)} · còn lại {num(redis.remaining)} · version {num(redis.version)}</> : 'Chưa đặt lại lần nào',
          },
        ]} />
        <Flex wrap gap={tokens.space[2]} align="center">
          {state?.running ? <Tag color="processing">Đang chạy</Tag> : <Tag>Rảnh</Tag>}
          <span className="sf-cc-toolbar__grow" />
          <Button onClick={onReset} loading={resetting} disabled={state?.running}>Đặt lại bộ đếm theo số suất M</Button>
        </Flex>
      </Flex>
    </Card>
  );
}

function RunsTable({ runs }) {
  const columns = [
    { title: 'Lần', dataIndex: 'run', render: (v) => num(v) },
    { title: 'Thành công', dataIndex: 'successes', align: 'right', render: (v) => num(v) },
    { title: 'Hết suất', dataIndex: 'soldOut', align: 'right', render: (v) => num(v) },
    { title: 'Bán vượt', dataIndex: 'oversold', align: 'right', render: (v) => num(v) },
    { title: 'Đã bán cuối', dataIndex: 'finalSold', align: 'right', render: (v) => num(v) },
    { title: 'Lost update', dataIndex: 'lostUpdates', align: 'right', render: (v) => num(v) },
    { title: 'Thử lại', dataIndex: 'retries', align: 'right', render: (v) => num(v) },
    {
      title: 'Lỗi', key: 'errors',
      render: (_, r) => (r.errors === 0 ? num(0) : (
        <Flex wrap gap={tokens.space[1]}>
          {Object.entries(r.errorCodes).map(([code, count]) => <Tag key={code} className="sf-cc-code">{code} × {count}</Tag>)}
        </Flex>
      )),
    },
    { title: 'Thời gian (ms)', dataIndex: 'elapsedMs', align: 'right', render: (v) => num(v, 1) },
    { title: 'Lượt/giây', dataIndex: 'throughput', align: 'right', render: (v) => num(v, 1) },
    { title: 'p50 (ms)', dataIndex: 'p50Ms', align: 'right', render: (v) => num(v, 2) },
    { title: 'p95 (ms)', dataIndex: 'p95Ms', align: 'right', render: (v) => num(v, 2) },
    { title: 'p99 (ms)', dataIndex: 'p99Ms', align: 'right', render: (v) => num(v, 2) },
  ];
  return <Table size="small" rowKey="run" columns={columns} dataSource={runs} pagination={false} scroll={{ x: 'max-content' }} />;
}

function ResultTable({ result }) {
  const { config } = result;
  const columns = [
    {
      title: 'Chiến lược', key: 'strategy', fixed: 'left',
      render: (_, r) => (
        <div className="sf-cc-name">
          <strong>{BY_VALUE[r.strategy]?.label ?? r.strategy}</strong>
          <span className="sf-cc-code">{r.strategy}</span>
        </div>
      ),
    },
    {
      title: 'Thành công', key: 'successes', align: 'right',
      render: (_, r) => (
        <div className="sf-cc-name">
          <span className="sf-num">{meanSd(r.summary.successes)}</span>
          {r.summary.successes.mean < config.quota && config.buyers > config.quota && <Tag color="warning">Bán thiếu</Tag>}
        </div>
      ),
    },
    {
      title: 'Bán vượt', key: 'oversold', align: 'right',
      render: (_, r) => (r.summary.oversold.mean > 0
        ? <Tag color="error">Bán vượt {meanSd(r.summary.oversold)}</Tag>
        : <span className="sf-num">0</span>),
    },
    { title: 'Đã bán cuối', key: 'finalSold', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.finalSold)}</span> },
    {
      title: 'Lost update', key: 'lostUpdates', align: 'right',
      render: (_, r) => (r.summary.lostUpdates.mean > 0
        ? <Tag color="error">{meanSd(r.summary.lostUpdates)}</Tag>
        : <span className="sf-num">0</span>),
    },
    { title: 'Thử lại', key: 'retries', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.retries)}</span> },
    { title: 'Lỗi', key: 'errors', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.errors)}</span> },
    { title: 'Lượt/giây', key: 'throughput', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.throughput)}</span> },
    { title: 'p50 (ms)', key: 'p50', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.p50Ms, 2)}</span> },
    { title: 'p95 (ms)', key: 'p95', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.p95Ms, 2)}</span> },
    { title: 'p99 (ms)', key: 'p99', align: 'right', render: (_, r) => <span className="sf-num">{meanSd(r.summary.p99Ms, 2)}</span> },
  ];
  return (
    <Flex vertical gap={tokens.space[3]}>
      <Descriptions size="small" column={{ xs: 1, sm: 2, lg: 4 }} bordered items={[
        { key: 'n', label: 'Người mua N', children: num(config.buyers) },
        { key: 'm', label: 'Số suất M', children: num(config.quota) },
        { key: 'c', label: 'Thread C', children: num(config.concurrency) },
        { key: 'runs', label: 'Lần đo (warmup)', children: <>{num(config.runs)} ({num(config.warmup)})</> },
        { key: 'op', label: 'Pool Oracle', children: num(config.oraclePoolSize) },
        { key: 'rp', label: 'Pool Redis', children: num(config.redisPoolSize) },
        { key: 'retry', label: 'Thử lại tối đa', children: num(config.maxRetries) },
      ]} />
      <Table size="small" rowKey="strategy" columns={columns} dataSource={result.results} pagination={false}
        scroll={{ x: 'max-content' }}
        rowClassName={(r) => (r.summary.oversold.mean > 0 || r.summary.lostUpdates.mean > 0 ? 'sf-cc-row--bad' : '')}
        expandable={{ expandedRowRender: (r) => <RunsTable runs={r.runs} /> }} />
    </Flex>
  );
}

export default function ConcurrencyLabPage() {
  const { message } = App.useApp();
  const [form] = Form.useForm();
  const [state, setState] = useState(null);
  const [running, setRunning] = useState(false);
  const [resetting, setResetting] = useState(false);
  const [elapsed, setElapsed] = useState(0);
  const [result, setResult] = useState(null);
  const [error, setError] = useState(null);
  const [sweep, setSweep] = useState([]);
  const [sweepStep, setSweepStep] = useState(null); // mức C đang chạy khi quét
  const startedRef = useRef(0);

  const loadState = useCallback(async () => {
    try {
      setState(await getLabState());
    } catch (problem) {
      setError(problem);
    }
  }, []);

  useEffect(() => { loadState(); }, [loadState]);

  // Trong lúc chạy: hỏi bộ đếm mỗi giây để thấy số liệu thay đổi, và đếm thời gian đã chạy.
  useEffect(() => {
    if (!running) return undefined;
    const timer = setInterval(() => {
      setElapsed(Math.round((Date.now() - startedRef.current) / 1000));
      loadState();
    }, STATE_POLL_MS);
    return () => clearInterval(timer);
  }, [running, loadState]);

  const run = async (values) => {
    setRunning(true);
    setError(null);
    setElapsed(0);
    startedRef.current = Date.now();
    try {
      setResult(await runLab(values));
    } catch (problem) {
      setError(problem.code === 'LAB_RUN_IN_PROGRESS'
        ? { ...problem, message: 'Đang có một lần chạy khác (có thể từ tab khác). Đợi xong rồi chạy lại.' }
        : problem);
    } finally {
      setRunning(false);
      loadState();
    }
  };

  // Quét theo C: chạy lần lượt cùng thông số với từng mức C, mỗi mức xong thì vẽ thêm một điểm.
  const runSweep = async () => {
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return;
    }
    setRunning(true);
    setError(null);
    setElapsed(0);
    setSweep([]);
    startedRef.current = Date.now();
    try {
      for (const concurrency of SWEEP_CONCURRENCY) {
        setSweepStep(concurrency);
        const stepResult = await runLab({ ...values, concurrency });
        setSweep((current) => [...current, { concurrency, result: stepResult }]);
      }
    } catch (problem) {
      setError(problem.code === 'LAB_RUN_IN_PROGRESS'
        ? { ...problem, message: 'Đang có một lần chạy khác (có thể từ tab khác). Đợi xong rồi quét lại.' }
        : problem);
    } finally {
      setSweepStep(null);
      setRunning(false);
      loadState();
    }
  };

  const reset = async () => {
    const quota = form.getFieldValue('quota');
    if (!quota) {
      message.warning('Nhập số suất M trước khi đặt lại.');
      return;
    }
    setResetting(true);
    try {
      setState(await resetLab(quota));
      message.success(`Đã đặt lại bộ đếm Oracle và Redis về ${quota} suất.`);
    } catch (problem) {
      message.error(problem.message ?? 'Không đặt lại được bộ đếm.');
    } finally {
      setResetting(false);
    }
  };

  const busy = running || Boolean(state?.running);

  return (
    <Flex vertical gap={tokens.space[4]}>
      <div className="sf-cc-name">
        <Typography.Title level={3} style={{ margin: 0 }}>Concurrency Lab</Typography.Title>
        <Typography.Text type="secondary">
          Một sản phẩm có M suất, N người cùng mua mỗi người 1 suất, backend chạy trên C thread và cho mọi lượt xuất phát
          cùng lúc. Mỗi chiến lược chạy warmup (không tính) rồi đo nhiều lần, lấy trung bình ± độ lệch chuẩn. Chạy đồng bộ,
          thông số lớn có thể mất vài chục giây.
        </Typography.Text>
      </div>

      <Card size="small" title="Thông số">
        <Form form={form} layout="vertical" initialValues={DEFAULTS} onFinish={run} disabled={busy} requiredMark={false}>
          <Form.Item name="strategies" label="Chiến lược"
            rules={[{ required: true, type: 'array', min: 1, message: 'Chọn ít nhất một chiến lược.' }]}>
            <Checkbox.Group className="sf-cc-strategies">
              {STRATEGIES.map((s) => (
                <Checkbox key={s.value} value={s.value}>
                  <Tag>{s.store}</Tag>{s.label}
                  <span className="sf-cc-hint sf-cc-muted">Kỳ vọng: {s.expect}</span>
                </Checkbox>
              ))}
            </Checkbox.Group>
          </Form.Item>
          <Flex wrap gap={tokens.space[2]} style={{ marginBottom: tokens.space[4] }}>
            <Button size="small" onClick={() => form.setFieldValue('strategies', ALL)}>Chọn tất cả</Button>
            <Button size="small" onClick={() => form.setFieldValue('strategies', ALL.filter((v) => BY_VALUE[v].store === 'Oracle'))}>Chỉ Oracle</Button>
            <Button size="small" onClick={() => form.setFieldValue('strategies', ALL.filter((v) => BY_VALUE[v].store === 'Redis'))}>Chỉ Redis</Button>
          </Flex>
          <div className="sf-cc-lab-form">
            <Form.Item name="buyers" label="Người mua N" rules={[{ required: true, message: 'Nhập N.' }]}>
              <InputNumber min={1} max={10000} precision={0} className="sf-cc-field-full" />
            </Form.Item>
            <Form.Item name="quota" label="Số suất M" dependencies={['buyers']}
              rules={[{ required: true, message: 'Nhập M.' }, ({ getFieldValue }) => ({
                validator: (_, v) => (!v || !getFieldValue('buyers') || v <= getFieldValue('buyers')
                  ? Promise.resolve() : Promise.reject(new Error('M không được lớn hơn N.'))),
              })]}>
              <InputNumber min={1} max={10000} precision={0} className="sf-cc-field-full" />
            </Form.Item>
            <Form.Item name="concurrency" label="Thread C" extra="Số lượt chạy cùng lúc"
              rules={[{ required: true, message: 'Nhập C.' }]}>
              <InputNumber min={1} max={256} precision={0} className="sf-cc-field-full" />
            </Form.Item>
            <Form.Item name="runs" label="Số lần đo" rules={[{ required: true, message: 'Nhập số lần đo.' }]}>
              <InputNumber min={1} max={10} precision={0} className="sf-cc-field-full" />
            </Form.Item>
            <Form.Item name="warmup" label="Warmup" rules={[{ required: true, message: 'Nhập số lần warmup.' }]}>
              <InputNumber min={0} max={3} precision={0} className="sf-cc-field-full" />
            </Form.Item>
          </div>
          <Flex wrap gap={tokens.space[3]} align="center">
            <Button type="primary" htmlType="submit" loading={running && sweepStep === null}>Chạy thí nghiệm</Button>
            <Button onClick={runSweep} loading={sweepStep !== null}>
              Quét theo C ({SWEEP_CONCURRENCY.join(', ')})
            </Button>
            {running && (
              <Typography.Text type="secondary" className="sf-num">
                {sweepStep !== null && `Đang chạy C = ${sweepStep} · `}Đã chạy {elapsed} giây…
              </Typography.Text>
            )}
            {!running && state?.running && <Typography.Text type="secondary">Một lần chạy khác đang diễn ra.</Typography.Text>}
          </Flex>
          <p className="sf-cc-muted sf-cc-hint">
            "Quét theo C" bỏ qua ô Thread C và chạy lại cùng thông số với {SWEEP_CONCURRENCY.length} mức C, rồi vẽ biểu đồ
            throughput và p95. Thời gian gấp khoảng {SWEEP_CONCURRENCY.length} lần một lần chạy; nên chọn ít chiến lược.
          </p>
        </Form>
      </Card>

      <StateCard state={state} onReload={loadState} onReset={reset} resetting={resetting} />

      {error && <Alert type="error" showIcon closable title={`${error.code}: ${error.message}`} onClose={() => setError(null)} />}

      {result && (
        <Card size="small" title="Kết quả">
          <Typography.Paragraph type="secondary">
            Bán vượt = số người được báo thành công vượt quá M. Lost update = số lượt báo thành công mà bộ đếm không ghi
            nhận. Bán thiếu: còn người mua nhưng bán chưa hết M (lượt bị từ chối vì khóa, xung đột hoặc hết lượt thử lại).
            Bấm mũi tên ở đầu dòng để xem từng lần đo và mã lỗi.
          </Typography.Paragraph>
          <ResultTable result={result} />
        </Card>
      )}

      {sweep.length > 0 && (
        <Card size="small" title="Throughput và độ trễ theo số lượt đồng thời C">
          <LabSweepCharts sweep={sweep} />
        </Card>
      )}
    </Flex>
  );
}
