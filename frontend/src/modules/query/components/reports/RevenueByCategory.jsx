import { Col, Empty, Row, Table } from 'antd';
import { Bar, BarChart, CartesianGrid, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts';
import { PriceTag, formatVND, tokens } from '@/shared/design-system';
import ReportCard from './ReportCard';
import useReport from './useReport';
import { ChartTooltip, axisProps, barRadius, cursorFill, gridProps } from './chartTheme';
import { formatInt, formatVNDCompact } from '../../format';

const BAR_COLOR = tokens.color.brand;
const ROW_HEIGHT = tokens.space[8] + tokens.space[1];
const LABEL_WIDTH = tokens.space[8] * 3;

function CategoryTooltip({ active, payload }) {
  const row = payload?.[0]?.payload;
  return (
    <ChartTooltip
      active={active && row}
      title={row?.categoryName}
      rows={row && [
        ['Doanh thu', formatVND(row.revenue), BAR_COLOR],
        ['Số lượng', formatInt(row.quantity), tokens.color.lineStrong],
      ]}
    />
  );
}

const columns = [
  { title: 'Danh mục', dataIndex: 'categoryName', key: 'categoryName', ellipsis: true },
  {
    title: 'Số lượng', dataIndex: 'quantity', key: 'quantity', align: 'right',
    render: (v) => <span className="sf-num">{formatInt(v)}</span>,
  },
  {
    title: 'Doanh thu', dataIndex: 'revenue', key: 'revenue', align: 'right',
    render: (v) => <PriceTag price={v} size="sm" />,
  },
];

/** F5 doanh thu theo danh mục: thanh ngang (đã sắp giảm dần từ backend) + bảng số liệu. */
export default function RevenueByCategory({ params }) {
  const state = useReport('revenue-by-category', params);
  const rows = state.data?.rows ?? [];

  return (
    <ReportCard title="Doanh thu theo danh mục" feature="F5" state={state} skeletonRows={8}>
      {rows.length === 0 ? (
        <Empty description="Không có đơn nào trong khoảng thời gian này" />
      ) : (
        <Row gutter={[tokens.space[6], tokens.space[4]]}>
          <Col xs={24} xl={13}>
            <ResponsiveContainer width="100%" height={rows.length * ROW_HEIGHT + tokens.space[8]}>
              <BarChart data={rows} layout="vertical" margin={{ top: 0, right: tokens.space[4], left: 0, bottom: 0 }}>
                <CartesianGrid {...gridProps} vertical horizontal={false} />
                <XAxis type="number" tickFormatter={formatVNDCompact} {...axisProps} />
                <YAxis type="category" dataKey="categoryName" width={LABEL_WIDTH} {...axisProps} axisLine={false} interval={0} />
                <Tooltip content={<CategoryTooltip />} cursor={cursorFill} />
                <Bar dataKey="revenue" name="Doanh thu" fill={BAR_COLOR} radius={[0, barRadius, barRadius, 0]} maxBarSize={tokens.space[6]} />
              </BarChart>
            </ResponsiveContainer>
          </Col>
          <Col xs={24} xl={11}>
            <Table
              rowKey="categoryId"
              size="small"
              columns={columns}
              dataSource={rows}
              pagination={rows.length > 10 ? { pageSize: 10, size: 'small', showSizeChanger: false } : false}
              scroll={{ x: 'max-content' }}
            />
          </Col>
        </Row>
      )}
    </ReportCard>
  );
}
