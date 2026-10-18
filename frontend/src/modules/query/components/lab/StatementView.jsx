import { Collapse, Typography } from 'antd';

// Câu SQL hoặc các lệnh Redis (mỗi lệnh một dòng). collapsible=true để gọn khi đặt cạnh bảng kết quả.
export default function StatementView({ statement, label = 'Câu lệnh', collapsible = false }) {
  if (!statement) return <Typography.Text type="secondary">Không có câu lệnh</Typography.Text>;
  const lines = statement.split('\n').length;
  const body = (
    <pre className="sf-lab-mono" aria-label={label}>
      {statement}
    </pre>
  );
  if (!collapsible) return body;
  return (
    <Collapse
      size="small"
      items={[{ key: 'stmt', label: `${label} (${lines} dòng)`, children: body }]}
    />
  );
}
