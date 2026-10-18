import { tokens } from '@/shared/design-system';

const { color, text, radius } = tokens;

// Thuộc tính dùng chung cho trục và lưới recharts: chữ phụ, lưới mờ, không viền trục thừa.
export const axisProps = {
  tick: { fill: color.inkMuted, fontSize: text.caption[0] },
  tickLine: false,
  axisLine: { stroke: color.line },
};

export const gridProps = { stroke: color.line, strokeDasharray: '0', vertical: false };

export const cursorFill = { fill: color.surfaceSunken };

/** Hộp tooltip: tiêu đề + các dòng [nhãn, giá trị, màu đánh dấu]. */
export function ChartTooltip({ active, title, rows }) {
  if (!active || !rows?.length) return null;
  return (
    <div className="sf-chart-tooltip">
      <div className="sf-chart-tooltip__title">{title}</div>
      {rows.map(([label, value, swatch]) => (
        <div key={label} className="sf-chart-tooltip__row">
          <span className="sf-chart-tooltip__label">
            <span className="sf-chart-tooltip__swatch" style={{ background: swatch }} />
            {label}
          </span>
          <strong className="sf-num">{value}</strong>
        </div>
      ))}
    </div>
  );
}

export const barRadius = radius.sm;
