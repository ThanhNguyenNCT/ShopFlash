import { useState } from 'react';
import { Alert, Button, Col, Row, Table, Typography } from 'antd';
import { PlayCircleOutlined } from '@ant-design/icons';
import { formatVND, tokens } from '@/shared/design-system';
import { compareReport } from '../../api';
import { EMPTY, formatInt, formatMs, formatRange } from '../../format';
import ErrorAlert from './ErrorAlert';
import Experiment from './Experiment';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import ResultTable from './ResultTable';
import StaleAlert from './StaleAlert';
import Verdict from './Verdict';
import { HINT_LABEL, isMoneyField, rangeParams, reportLabel, reportParams, runKey, useApi } from './labUtils';

const fmtValue = (field, v) => (v === null || v === undefined ? EMPTY : isMoneyField(field) ? formatVND(v) : String(v));

const MISMATCH_COLUMNS = [
  { title: 'Khóa', dataIndex: 'key', render: (v) => <span className="sf-code">{v}</span> },
  { title: 'Trường', dataIndex: 'field', render: (v) => <span className="sf-code">{v}</span> },
  { title: 'Oracle', dataIndex: 'oracle', align: 'right', render: (v, r) => <span className="sf-num">{fmtValue(r.field, v)}</span> },
  { title: 'Redis', dataIndex: 'redis', align: 'right', render: (v, r) => <span className="sf-num">{fmtValue(r.field, v)}</span> },
];

const lineCount = (s) => (s ? s.split('\n').filter((l) => l.trim()).length : 0);

function sourceTile(source, meta) {
  if (!meta) return null;
  const tone = source === 'redis' ? 'redis' : 'oracle';
  const name = source === 'redis' ? 'Redis' : 'Oracle';
  if (meta.supported === false) return { key: source, tone, label: name, value: 'Không hỗ trợ', caption: meta.note };
  return {
    key: source,
    tone,
    label: `${name} · ${HINT_LABEL[meta.variant]?.split(' ')[0] ?? meta.variant}`,
    value: formatMs(meta.elapsedMs),
    caption: `${formatInt(meta.roundTrips)} round-trip · ${formatInt(meta.rowCount)} dòng`,
  };
}

function matchTile(data) {
  if (data.match === true) return { key: 'm', tone: 'good', label: 'So từng dòng', value: 'Khớp', caption: 'Trùng khóa, trùng giá trị' };
  if (data.match === false) return { key: 'm', tone: 'bad', label: 'So từng dòng', value: 'Lệch', caption: `${data.mismatches?.length ?? 0} chỗ (liệt kê tối đa 5)` };
  return { key: 'm', tone: 'neutral', label: 'So từng dòng', value: 'Không so được', caption: 'Một bên không hỗ trợ báo cáo này' };
}

function Statement({ tone, title, statement }) {
  return (
    <div className="sf-lab-side">
      <div className="sf-lab-side__title">
        <span className={`sf-lab-swatch sf-lab-swatch--${tone}`} />
        {title}
        <Typography.Text type="secondary">({formatInt(lineCount(statement))} dòng)</Typography.Text>
      </div>
      {statement ? <pre className="sf-lab-mono sf-lab-mono--plan sf-lab-mono--wrap">{statement}</pre> : <Typography.Text type="secondary">Không có câu lệnh</Typography.Text>}
    </div>
  );
}

// D1: cùng báo cáo, cùng tham số chạy trên Oracle và Redis phải ra cùng kết quả; khác nhau ở cách viết lệnh.
export default function CompareSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const { data, loading, error, run } = useApi(compareReport);
  const [ran, setRan] = useState(null);
  const params = { ...rangeParams(range), ...reportParams(pick.report, pick) };
  const key = runKey(pick.report, params);

  const execute = () => {
    setRan({ key, range });
    run(pick.report, params);
  };

  const oracle = data?.oracle?.meta;
  const redis = data?.redis?.meta;
  const rows = data?.oracle?.data?.rows ?? data?.redis?.data?.rows;

  return (
    <Experiment
      description="Chạy cùng báo cáo, cùng tham số trên Oracle và read model Redis, so từng dòng theo khóa."
      controls={
        <>
          <ReportPicker value={pick} onChange={setPick} />
          <Button type="primary" icon={<PlayCircleOutlined />} loading={loading} onClick={execute}>
            So sánh hai nguồn
          </Button>
        </>
      }
    >
      <ErrorAlert error={error} title="Không so sánh được" />
      {(loading || data) && (
        <Verdict
          loading={loading}
          items={data && [matchTile(data), sourceTile('oracle', oracle), sourceTile('redis', redis)].filter(Boolean)}
          headline={
            data && (
              <>
                {reportLabel(data.report)} · {formatRange(ran?.range)}. Oracle viết{' '}
                <Typography.Text strong>{formatInt(lineCount(oracle?.statement))} dòng SQL</Typography.Text>, Redis{' '}
                <Typography.Text strong>{formatInt(lineCount(redis?.statement))} lệnh</Typography.Text>. Thời gian ở đây chỉ để tham khảo,
                số đo chuẩn ở tab Đo hiệu năng.
              </>
            )
          }
        />
      )}
      {data && (
        <>
          <StaleAlert stale={ran?.key !== key} action="So sánh hai nguồn" />
          {data.mismatches?.length > 0 && (
            <Table
              size="small"
              columns={MISMATCH_COLUMNS}
              dataSource={data.mismatches}
              rowKey={(r) => `${r.key}-${r.field}`}
              pagination={false}
              scroll={{ x: 'max-content' }}
            />
          )}
          <Row gutter={[tokens.space[4], tokens.space[4]]}>
            <Col xs={24} xl={12}>
              <Statement tone="oracle" title="Oracle: một câu SQL" statement={oracle?.statement} />
            </Col>
            <Col xs={24} xl={12}>
              {redis?.supported === false ? (
                <Alert type="warning" showIcon title="Redis không hỗ trợ báo cáo này" description={redis.note} />
              ) : (
                <Statement tone="redis" title="Redis: lệnh trên read model" statement={redis?.statement} />
              )}
            </Col>
          </Row>
          {rows && (
            <div>
              <Typography.Title level={5}>Kết quả {data.match === true ? '(giống nhau ở hai nguồn)' : '(Oracle)'}</Typography.Title>
              <ResultTable report={data.report} rows={rows} />
            </div>
          )}
        </>
      )}
    </Experiment>
  );
}
