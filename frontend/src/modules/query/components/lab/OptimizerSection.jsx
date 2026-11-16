import { useRef, useState } from 'react';
import { Button, Card, Col, Collapse, Row, Select } from 'antd';
import { ApartmentOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { JOIN_HINTS, getPlan, getReport } from '../../api';
import { formatMs, formatRange, formatRatio } from '../../format';
import ErrorAlert from './ErrorAlert';
import Experiment from './Experiment';
import PlanTable from './PlanTable';
import PlanView from './PlanView';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import StaleAlert from './StaleAlert';
import Verdict from './Verdict';
import { parseXplan } from './xplan';
import { HINT_LABEL, JOIN_REPORTS, rangeParams, reportLabel, reportParams, runKey, settle } from './labUtils';

const hintOptions = JOIN_HINTS.map((h) => ({ value: h, label: HINT_LABEL[h] }));

async function attempt(promise) {
  try {
    return { value: await promise, error: null };
  } catch (error) {
    return { value: null, error };
  }
}

function hintTile(key, hint, slot, time) {
  const joins = slot?.value ? parseXplan(slot.value.lines).joins : [];
  return {
    key,
    tone: 'oracle',
    label: `Oracle · hint ${hint}`,
    value: time?.value ? formatMs(time.value.meta?.elapsedMs) : '—',
    caption: joins.length ? joins.join(', ') : slot?.error ? 'Không lấy được kế hoạch' : '',
  };
}

// D2: Oracle có optimizer dựa trên chi phí; ép hint là ép kiểu join và thấy ngay giá phải trả. Redis chạy đúng pipeline đã viết.
export default function OptimizerSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const [hintA, setHintA] = useState('AUTO');
  const [hintB, setHintB] = useState('NL');
  const [res, setRes] = useState(null);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);
  const base = { ...rangeParams(range), ...reportParams(pick.report, pick) };
  const key = runKey(pick.report, base, hintA, hintB);

  const execute = async () => {
    const id = ++seq.current;
    setLoading(true);
    setRes(null);
    const ran = { key, report: pick.report, range, hintA, hintB };
    const plans = await Promise.allSettled([
      getPlan(pick.report, { ...base, source: 'oracle', joinHint: hintA }),
      getPlan(pick.report, { ...base, source: 'oracle', joinHint: hintB }),
      getPlan(pick.report, { ...base, source: 'redis', redisMode: 'AGGREGATE' }),
    ]);
    // Đo thời gian lần lượt (không song song) sau khi plan đã nạp cache, để hai con số đặt cạnh nhau công bằng.
    const timeA = await attempt(getReport(pick.report, { ...base, source: 'oracle', joinHint: hintA }));
    const timeB = await attempt(getReport(pick.report, { ...base, source: 'oracle', joinHint: hintB }));
    if (id !== seq.current) return;
    setRes({ ...ran, a: settle(plans[0]), b: settle(plans[1]), redis: settle(plans[2]), timeA, timeB });
    setLoading(false);
  };

  const msA = res?.timeA?.value?.meta?.elapsedMs;
  const msB = res?.timeB?.value?.meta?.elapsedMs;
  const slower = msA > 0 && msB > 0 ? (msB >= msA ? { by: msB / msA, who: res.hintB } : { by: msA / msB, who: res.hintA }) : null;

  return (
    <Experiment
      description="Kế hoạch thực thi và thời gian của câu SQL với hai hint join. Redis không có optimizer, chỉ có FT.PROFILE."
      controls={
        <>
          <ReportPicker value={pick} onChange={setPick} reports={JOIN_REPORTS} />
          <Select aria-label="Hint thứ nhất" value={hintA} onChange={setHintA} options={hintOptions} prefix="A" />
          <Select aria-label="Hint thứ hai" value={hintB} onChange={setHintB} options={hintOptions} prefix="B" />
          <Button type="primary" icon={<ApartmentOutlined />} loading={loading} onClick={execute}>
            Chạy và xem kế hoạch
          </Button>
        </>
      }
    >
      {(loading || res) && (
        <Verdict
          loading={loading}
          items={
            res && [
              hintTile('a', res.hintA, res.a, res.timeA),
              hintTile('b', res.hintB, res.b, res.timeB),
              slower && {
                key: 'r',
                tone: slower.by >= 1.5 ? 'bad' : 'neutral',
                label: 'Chênh lệch',
                value: `${formatRatio(slower.by)}×`,
                caption: `${slower.who} chậm hơn`,
              },
            ].filter(Boolean)
          }
          headline={res && `${reportLabel(res.report)} · ${formatRange(res.range)}. Một lần chạy, chỉ để minh họa; số đo 5 lần ở tab Đo hiệu năng.`}
        />
      )}
      {res && <StaleAlert stale={res.key !== key} action="Chạy và xem kế hoạch" />}
      {res && (
        <>
          <ErrorAlert error={res.timeA.error ?? res.timeB.error} title="Không đo được thời gian" />
          <Row gutter={[tokens.space[4], tokens.space[4]]}>
            {[['a', res.hintA], ['b', res.hintB]].map(([k, hint]) => (
              <Col xs={24} xxl={12} key={k}>
                <Card size="small" title={<><span className="sf-lab-swatch sf-lab-swatch--oracle" />Oracle · {HINT_LABEL[hint]}</>}>
                  <ErrorAlert error={res[k].error} title="Không lấy được kế hoạch" />
                  <PlanTable lines={res[k].value?.lines} />
                </Card>
              </Col>
            ))}
          </Row>
          <Collapse
            items={[
              {
                key: 'redis',
                label: <><span className="sf-lab-swatch sf-lab-swatch--redis" />Redis · FT.PROFILE: không có optimizer, chạy đúng như lệnh viết</>,
                children: (
                  <>
                    <ErrorAlert error={res.redis.error} title="Không lấy được FT.PROFILE" />
                    <PlanView lines={res.redis.value?.lines} />
                  </>
                ),
              },
            ]}
          />
        </>
      )}
    </Experiment>
  );
}
