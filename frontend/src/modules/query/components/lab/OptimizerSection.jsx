import { useRef, useState } from 'react';
import { Button, Card, Col, Flex, Row, Select, Space, Spin, Typography } from 'antd';
import { ApartmentOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { JOIN_HINTS, getPlan } from '../../api';
import ErrorAlert from './ErrorAlert';
import PlanView from './PlanView';
import ReportPicker, { DEFAULT_PICK } from './ReportPicker';
import StatementView from './StatementView';
import { HINT_LABEL, JOIN_REPORTS, rangeParams, reportParams, settle } from './labUtils';

const hintOptions = JOIN_HINTS.map((h) => ({ value: h, label: HINT_LABEL[h] }));

function PlanPanel({ title, slot, loading }) {
  return (
    <Card size="small" title={title}>
      <Spin spinning={loading}>
        <Space orientation="vertical" size={tokens.space[3]} className="sf-lab-full">
          <ErrorAlert error={slot?.error} title="Không lấy được kế hoạch" />
          <PlanView lines={slot?.value?.lines} emptyText="Bấm Xem kế hoạch" />
          {slot?.value?.statement && <StatementView statement={slot.value.statement} collapsible />}
        </Space>
      </Spin>
    </Card>
  );
}

// D2: Oracle có optimizer dựa trên chi phí, đổi hint là đổi phép join; Redis chạy đúng pipeline đã viết.
export default function OptimizerSection({ range }) {
  const [pick, setPick] = useState(DEFAULT_PICK);
  const [hintA, setHintA] = useState('AUTO');
  const [hintB, setHintB] = useState('NL');
  const [plans, setPlans] = useState(null);
  const [loading, setLoading] = useState(false);
  const seq = useRef(0);

  const execute = async () => {
    const id = ++seq.current;
    setLoading(true);
    const base = { ...rangeParams(range), ...reportParams(pick.report, pick) };
    const results = await Promise.allSettled([
      getPlan(pick.report, { ...base, source: 'oracle', joinHint: hintA }),
      getPlan(pick.report, { ...base, source: 'oracle', joinHint: hintB }),
      getPlan(pick.report, { ...base, source: 'redis', redisMode: 'AGGREGATE' }),
    ]);
    if (id !== seq.current) return;
    setPlans({ a: settle(results[0]), b: settle(results[1]), redis: settle(results[2]), hintA, hintB });
    setLoading(false);
  };

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Oracle có optimizer dựa trên chi phí (CBO): cùng một câu SQL, nó tự chọn thứ tự và kiểu join (NESTED LOOPS, HASH JOIN, MERGE JOIN)
        theo thống kê. Hint ép optimizer đổi lựa chọn. Redis không có optimizer: FT.AGGREGATE chạy đúng các bước GROUPBY/REDUCE/SORTBY
        mà người viết lệnh đặt ra, FT.PROFILE chỉ cho thấy số bản ghi qua từng bước. Dòng join được tô màu.
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[3]} align="center">
        <ReportPicker value={pick} onChange={setPick} reports={JOIN_REPORTS} />
        <Select aria-label="Hint thứ nhất" value={hintA} onChange={setHintA} options={hintOptions} prefix="A" />
        <Select aria-label="Hint thứ hai" value={hintB} onChange={setHintB} options={hintOptions} prefix="B" />
        <Button type="primary" icon={<ApartmentOutlined />} loading={loading} onClick={execute}>
          Xem kế hoạch
        </Button>
      </Flex>
      <Row gutter={[tokens.space[4], tokens.space[4]]}>
        <Col xs={24} xl={12}>
          <PlanPanel title={`Oracle · ${plans?.hintA ?? hintA}`} slot={plans?.a} loading={loading} />
        </Col>
        <Col xs={24} xl={12}>
          <PlanPanel title={`Oracle · ${plans?.hintB ?? hintB}`} slot={plans?.b} loading={loading} />
        </Col>
        <Col xs={24}>
          <PlanPanel title="Redis · FT.PROFILE (không có optimizer)" slot={plans?.redis} loading={loading} />
        </Col>
      </Row>
    </Space>
  );
}
