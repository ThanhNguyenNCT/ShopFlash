import { useState } from 'react';
import { DatePicker, Flex, Space, Tabs, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import CompareSection from '../components/lab/CompareSection';
import OptimizerSection from '../components/lab/OptimizerSection';
import StatsSection from '../components/lab/StatsSection';
import RoundTripSection from '../components/lab/RoundTripSection';
import SubquerySection from '../components/lab/SubquerySection';
import ConsistencySection from '../components/lab/ConsistencySection';
import BenchmarkSection from '../components/lab/BenchmarkSection';
import { DATE_FORMAT, defaultRange } from '../components/lab/labUtils';
import '../components/lab/lab.css';

// Phòng thí nghiệm query processing: 6 điểm khác nhau giữa Oracle và Redis + đo hiệu năng.
export default function QueryLabPage() {
  const [range, setRange] = useState(defaultRange);

  const items = [
    { key: 'd1', label: '1. Kết quả giống nhau', children: <CompareSection range={range} /> },
    { key: 'd2', label: '2. Optimizer', children: <OptimizerSection range={range} /> },
    { key: 'd3', label: '3. Thống kê', children: <StatsSection range={range} /> },
    { key: 'd4', label: '4. Round-trip', children: <RoundTripSection range={range} /> },
    { key: 'd5', label: '5. Subquery', children: <SubquerySection range={range} /> },
    { key: 'd6', label: '6. Nhất quán', children: <ConsistencySection /> },
    { key: 'bench', label: 'Đo hiệu năng', children: <BenchmarkSection range={range} /> },
  ];

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Flex wrap gap={tokens.space[3]} justify="space-between" align="flex-end">
        <div>
          <Typography.Title level={3} style={{ margin: 0 }}>
            So sánh Oracle–Redis
          </Typography.Title>
          <Typography.Text type="secondary">Query processing: cùng báo cáo F5/F6, hai cách xử lý truy vấn.</Typography.Text>
        </div>
        <Flex vertical gap={tokens.space[1]}>
          <Typography.Text type="secondary">Khoảng thời gian (áp dụng cho mọi thí nghiệm)</Typography.Text>
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
            title={`${range[0].format(DATE_FORMAT)} → ${range[1].format(DATE_FORMAT)}`}
          />
        </Flex>
      </Flex>
      <Tabs items={items} defaultActiveKey="d1" />
    </Space>
  );
}
