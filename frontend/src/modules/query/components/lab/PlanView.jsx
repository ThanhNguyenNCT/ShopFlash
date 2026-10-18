import { Empty, Typography } from 'antd';

const JOIN_RE = /NESTED LOOPS|HASH JOIN|MERGE JOIN/i;
// Oracle in dòng này khi bảng không có thống kê (dynamic sampling) — dùng cho phần thống kê.
const WARN_RE = /dynamic (statistics|sampling)/i;

export function joinOperations(lines = []) {
  const ops = lines.map((l) => l.match(JOIN_RE)?.[0]?.toUpperCase()).filter(Boolean);
  return [...new Set(ops)];
}

// Kế hoạch thực thi dạng chữ đơn cách, cuộn ngang; dòng có phép join được tô màu brand.
export default function PlanView({ lines, emptyText = 'Chưa có kế hoạch' }) {
  if (!lines?.length) return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={emptyText} />;
  const joins = joinOperations(lines);
  return (
    <>
      {joins.length > 0 && (
        <Typography.Paragraph type="secondary">
          Phép join: <Typography.Text strong>{joins.join(', ')}</Typography.Text>
        </Typography.Paragraph>
      )}
      <pre className="sf-lab-mono" aria-label="Kế hoạch thực thi">
        {lines.map((line, i) => {
          const cls = JOIN_RE.test(line) ? ' sf-lab-plan__line--join' : WARN_RE.test(line) ? ' sf-lab-plan__line--warn' : '';
          return (
            <span key={i} className={`sf-lab-plan__line${cls}`}>
              {line || ' '}
            </span>
          );
        })}
      </pre>
    </>
  );
}
