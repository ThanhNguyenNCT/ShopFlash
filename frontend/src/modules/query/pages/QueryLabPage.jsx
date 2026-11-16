import { useState } from 'react';
import { DatePicker, Flex, Segmented, Tabs, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import CompareSection from '../components/lab/CompareSection';
import OptimizerSection from '../components/lab/OptimizerSection';
import StatsSection from '../components/lab/StatsSection';
import RoundTripSection from '../components/lab/RoundTripSection';
import SubquerySection from '../components/lab/SubquerySection';
import ConsistencySection from '../components/lab/ConsistencySection';
import BenchmarkSection from '../components/lab/BenchmarkSection';
import { defaultRange } from '../format';
import '../components/lab/lab.css';

// Một bước có hai thí nghiệm con: chọn bằng Segmented, giữ state của cả hai khi chuyển qua lại.
function TwoPart({ parts }) {
  const [active, setActive] = useState(parts[0].key);
  return (
    <Flex vertical gap={tokens.space[4]}>
      <Segmented value={active} onChange={setActive} options={parts.map((p) => ({ value: p.key, label: p.label }))} />
      {parts.map((p) => (
        <div key={p.key} hidden={p.key !== active}>
          {p.children}
        </div>
      ))}
    </Flex>
  );
}

// Query Lab: D1 kết quả hai nguồn · D2, D3 optimizer (hint, thống kê) · D4, D5 round-trip, subquery · D6 nhất quán · đo hiệu năng.
export default function QueryLabPage() {
  const [range, setRange] = useState(defaultRange);

  const items = [
    { key: 'd1', label: 'Kết quả hai nguồn', children: <CompareSection range={range} /> },
    {
      key: 'd2',
      label: 'Optimizer',
      children: (
        <TwoPart
          parts={[
            { key: 'hint', label: 'Hint join', children: <OptimizerSection range={range} /> },
            { key: 'stats', label: 'Thống kê', children: <StatsSection range={range} /> },
          ]}
        />
      ),
    },
    {
      key: 'd3',
      label: 'Round-trip và subquery',
      children: (
        <TwoPart
          parts={[
            { key: 'rt', label: 'Round-trip', children: <RoundTripSection range={range} /> },
            { key: 'sub', label: 'Subquery', children: <SubquerySection range={range} /> },
          ]}
        />
      ),
    },
    { key: 'd4', label: 'Nhất quán', children: <ConsistencySection /> },
    { key: 'bench', label: 'Đo hiệu năng', children: <BenchmarkSection range={range} /> },
  ];

  return (
    <Flex vertical gap={tokens.space[4]} className="sf-lab-full">
      <div className="sf-lab-page__head">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            Query Lab
          </Typography.Title>
          <Typography.Text type="secondary">
            So sánh xử lý truy vấn của các báo cáo trên Oracle và Redis.
          </Typography.Text>
          <div className="sf-lab-legend" style={{ marginTop: tokens.space[2] }}>
            <span><span className="sf-lab-swatch sf-lab-swatch--oracle" />Oracle: nguồn gốc, SQL + optimizer</span>
            <span><span className="sf-lab-swatch sf-lab-swatch--redis" />Redis: read model, FT.AGGREGATE</span>
          </div>
        </div>
        <Flex vertical gap={tokens.space[1]}>
          <Typography.Text type="secondary">Khoảng thời gian</Typography.Text>
          <DatePicker.RangePicker
            aria-label="Khoảng thời gian"
            value={range}
            format="DD/MM/YYYY"
            allowClear={false}
            onChange={(v) => v && setRange(v)}
            presets={[
              { label: '12 tháng gần nhất', value: defaultRange() },
              { label: 'Năm nay', value: [defaultRange()[1].startOf('year'), defaultRange()[1]] },
            ]}
          />
        </Flex>
      </div>
      <Tabs items={items} defaultActiveKey="d1" />
    </Flex>
  );
}
