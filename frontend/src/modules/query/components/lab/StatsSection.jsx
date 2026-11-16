import { useRef, useState } from 'react';
import { Button, Card, Col, Collapse, Popconfirm, Row, Select, Table, Tag } from 'antd';
import { CloudDownloadOutlined, DeleteOutlined, EyeOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { JOIN_HINTS, changeOracleStats, getPlan } from '../../api';
import { formatInt, formatMs, formatRange, formatRatio } from '../../format';
import ErrorAlert from './ErrorAlert';
import Experiment from './Experiment';
import PlanTable from './PlanTable';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import Verdict from './Verdict';
import { estimateError, parseRows, parseXplan, topJoin } from './xplan';
import { HINT_LABEL, JOIN_REPORTS, formatDateTime, rangeParams, reportLabel, reportParams, runKey } from './labUtils';

// Ba bước của demo: mỗi bước một nút, mỗi bước để lại một "ảnh chụp" kế hoạch để so.
const STEPS = {
  before: { label: 'Ban đầu', action: null },
  deleted: { label: 'Sau khi xóa thống kê', action: 'DELETE' },
  gathered: { label: 'Sau khi thu thập lại', action: 'GATHER' },
};

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
      return t ? <span title={t.absolute}>{t.relative}</span> : <Tag color="warning">chưa có</Tag>;
    },
  },
];

function snapshotTile(step, snap, first) {
  const plan = parseXplan(snap.plan.lines);
  const join = topJoin(plan);
  const est = parseRows(join?.cells?.['E-Rows']);
  const act = parseRows(join?.cells?.['A-Rows']);
  const err = estimateError(join);
  const changed = first && plan.hash !== first.hash;
  return {
    key: step,
    tone: err !== null && err >= 10 ? 'bad' : 'oracle',
    label: STEPS[step].label,
    value: join ? join.op.replace(/\s+/g, ' ') : plan.joins.join(', ') || '—',
    caption: (
      <>
        Ước lượng {est !== null ? formatInt(est) : '—'} / thực tế {act !== null ? formatInt(act) : '—'} dòng
        {err !== null && err >= 1.5 && ` (lệch ${formatRatio(err)}×)`}
        <br />
        Plan hash <span className="sf-code">{plan.hash}</span>
        {changed && <Tag color="warning" style={{ marginLeft: tokens.space[1] }}>đổi kế hoạch</Tag>}
        {plan.dynamic && <Tag style={{ marginLeft: tokens.space[1] }}>dynamic sampling</Tag>}
      </>
    ),
  };
}

// D3: optimizer của Oracle dựa vào thống kê. Xóa thống kê → Oracle phải đoán → ước lượng sai → kế hoạch đổi.
export default function StatsSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const [hint, setHint] = useState('AUTO');
  // { key, snaps: { before, deleted, gathered } }: chỉ so ảnh chụp cùng báo cáo, hint, khoảng ngày.
  const [state, setState] = useState({ key: null, snaps: {} });
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState(null);
  const [tables, setTables] = useState(null);
  const seq = useRef(0);
  const params = { ...rangeParams(range), ...reportParams(pick.report, pick), source: 'oracle', joinHint: hint };
  const key = runKey(pick.report, params);
  const snaps = state.key === key ? state.snaps : {};

  const runStep = async (step) => {
    const id = ++seq.current;
    setBusy(step);
    setError(null);
    try {
      const action = STEPS[step].action;
      if (action) {
        const res = await changeOracleStats(action);
        setTables(res);
      }
      const plan = await getPlan(pick.report, params);
      if (id !== seq.current) return;
      const snap = { plan, at: new Date().toISOString(), report: pick.report, hint, range };
      setState((prev) => ({ key, snaps: { ...(prev.key === key ? prev.snaps : {}), [step]: snap } }));
    } catch (e) {
      if (id === seq.current) setError(e);
    } finally {
      if (id === seq.current) setBusy(null);
    }
  };

  const order = ['before', 'deleted', 'gathered'].filter((s) => snaps[s]);
  const firstHash = snaps.before ? parseXplan(snaps.before.plan.lines) : null;
  const any = order.length > 0 ? snaps[order[0]] : null;

  return (
    <Experiment
      description="Thống kê optimizer của ORDERS, ORDER_ITEMS, PRODUCTS, CATEGORIES, USERS và kế hoạch thực thi tương ứng."
      controls={
        <>
          <ReportPicker value={pick} onChange={setPick} reports={JOIN_REPORTS} />
          <Select aria-label="Hint" value={hint} onChange={setHint} options={JOIN_HINTS.map((h) => ({ value: h, label: HINT_LABEL[h] }))} />
        </>
      }
    >
      <div className="sf-lab-stats-steps">
        <Button type="primary" icon={<EyeOutlined />} loading={busy === 'before'} disabled={!!busy && busy !== 'before'} onClick={() => runStep('before')}>
          Xem kế hoạch
        </Button>
        <Popconfirm
          title="Xóa thống kê Oracle?"
          description="Xóa thống kê của ORDERS, ORDER_ITEMS, PRODUCTS, CATEGORIES, USERS. Thu thập lại bằng nút bên cạnh."
          okText="Xóa thống kê"
          okButtonProps={{ danger: true }}
          cancelText="Hủy"
          onConfirm={() => runStep('deleted')}
        >
          <Button danger icon={<DeleteOutlined />} loading={busy === 'deleted'} disabled={!!busy && busy !== 'deleted'}>
            Xóa thống kê
          </Button>
        </Popconfirm>
        <Button icon={<CloudDownloadOutlined />} loading={busy === 'gathered'} disabled={!!busy && busy !== 'gathered'} onClick={() => runStep('gathered')}>
          Thu thập thống kê
        </Button>
      </div>
      <ErrorAlert error={error} title="Không chạy được bước này" />
      {order.length > 0 && (
        <Verdict
          items={order.map((s) => snapshotTile(s, snaps[s], s === 'before' ? null : firstHash))}
          headline={`${reportLabel(any.report)} · hint ${any.hint} · ${formatRange(any.range)}.`}
        />
      )}
      {order.length > 0 && (
        <Row gutter={[tokens.space[4], tokens.space[4]]}>
          {order.map((s) => (
            <Col xs={24} xxl={order.length === 1 ? 24 : 12} key={s} hidden={order.length === 3 && s === 'gathered'}>
              <Card size="small" title={STEPS[s].label} extra={<span className="sf-num">{formatDateTime(snaps[s].at)?.absolute}</span>}>
                <PlanTable lines={snaps[s].plan.lines} />
              </Card>
            </Col>
          ))}
        </Row>
      )}
      {tables && (
        <Collapse
          items={[
            {
              key: 'tables',
              label: `Thống kê bảng sau lần ${tables.action === 'DELETE' ? 'xóa' : 'thu thập'} gần nhất (${formatMs(tables.elapsedMs)})`,
              children: <Table size="small" columns={STAT_COLUMNS} dataSource={tables.tables ?? []} rowKey="table" pagination={false} scroll={{ x: 'max-content' }} />,
            },
          ]}
        />
      )}
    </Experiment>
  );
}
