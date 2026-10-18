import { Empty, Typography } from 'antd';
import { Bar, BarChart, CartesianGrid, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { formatVND, tokens } from '@/shared/design-system';
import ReportCard from './ReportCard';
import useReport from './useReport';
import { ChartTooltip, axisProps, barRadius, cursorFill, gridProps } from './chartTheme';
import { formatInt, formatMonth, formatVNDCompact } from './format';

const REVENUE_COLOR = tokens.color.brand;
const ORDERS_COLOR = tokens.color.info;
// Chiều cao 2 biểu đồ: doanh thu là chính, số đơn là dải phụ phía dưới.
const MAIN_HEIGHT = tokens.space[8] * 8;
const SUB_HEIGHT = tokens.space[8] * 4;
const Y_WIDTH = tokens.space[8] * 2;

function MonthTooltip({ active, payload, label }) {
  const row = payload?.[0]?.payload;
  return (
    <ChartTooltip
      active={active && row}
      title={formatMonth(label)}
      rows={row && [
        ['Doanh thu', formatVND(row.revenue), REVENUE_COLOR],
        ['Số đơn', formatInt(row.orderCount), ORDERS_COLOR],
      ]}
    />
  );
}

/**
 * F5 doanh thu theo tháng. Hai đại lượng khác thang (tiền và số đơn) nên vẽ thành hai biểu đồ
 * chung trục tháng, đồng bộ con trỏ (syncId), thay vì một biểu đồ hai trục Y dễ gây hiểu nhầm.
 */
export default function RevenueByMonth({ params }) {
  const state = useReport('revenue-by-month', params);
  const rows = state.data?.rows ?? [];

  return (
    <ReportCard title="Doanh thu theo tháng" feature="F5" state={state} skeletonRows={8}>
      {rows.length === 0 ? (
        <Empty description="Không có đơn nào trong khoảng thời gian này" />
      ) : (
        <div className="sf-month-chart">
          <Typography.Text type="secondary" className="sf-chart-caption">
            <span className="sf-chart-swatch" style={{ background: REVENUE_COLOR }} /> Doanh thu (₫)
          </Typography.Text>
          <ResponsiveContainer width="100%" height={MAIN_HEIGHT}>
            <BarChart data={rows} syncId="revenue-by-month" margin={{ top: tokens.space[2], right: tokens.space[2], left: 0, bottom: 0 }}>
              <CartesianGrid {...gridProps} />
              <XAxis dataKey="month" tickFormatter={formatMonth} {...axisProps} hide />
              <YAxis tickFormatter={formatVNDCompact} width={Y_WIDTH} {...axisProps} axisLine={false} />
              <Tooltip content={<MonthTooltip />} cursor={cursorFill} />
              <Bar dataKey="revenue" name="Doanh thu" fill={REVENUE_COLOR} radius={[barRadius, barRadius, 0, 0]} maxBarSize={tokens.space[8] + tokens.space[4]} />
            </BarChart>
          </ResponsiveContainer>
          <Typography.Text type="secondary" className="sf-chart-caption">
            <span className="sf-chart-swatch" style={{ background: ORDERS_COLOR }} /> Số đơn
          </Typography.Text>
          <ResponsiveContainer width="100%" height={SUB_HEIGHT}>
            <LineChart data={rows} syncId="revenue-by-month" margin={{ top: tokens.space[2], right: tokens.space[2], left: 0, bottom: 0 }}>
              <CartesianGrid {...gridProps} />
              <XAxis dataKey="month" tickFormatter={formatMonth} {...axisProps} minTickGap={tokens.space[2]} />
              <YAxis tickFormatter={formatInt} width={Y_WIDTH} {...axisProps} axisLine={false} tickCount={3} />
              <Tooltip content={<MonthTooltip />} cursor={{ stroke: tokens.color.lineStrong }} />
              <Line
                type="monotone"
                dataKey="orderCount"
                name="Số đơn"
                stroke={ORDERS_COLOR}
                strokeWidth={2}
                dot={{ r: 3, fill: ORDERS_COLOR, stroke: tokens.color.surface, strokeWidth: 2 }}
                activeDot={{ r: 5, stroke: tokens.color.surface, strokeWidth: 2 }}
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      )}
    </ReportCard>
  );
}
