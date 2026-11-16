import { Table, Typography } from 'antd';
import { PriceTag, formatVND } from '@/shared/design-system';
import ReportCard from './ReportCard';
import useReport from './useReport';
import usePager from './usePager';
import { formatInt } from '../../format';

const columns = [
  {
    title: 'Khách hàng', dataIndex: 'fullName', key: 'fullName',
    render: (name, row) => (
      <span>
        {name} <span className="sf-code sf-muted">#{row.userId}</span>
      </span>
    ),
  },
  { title: 'Email', dataIndex: 'email', key: 'email' },
  { title: 'Số đơn', dataIndex: 'orderCount', key: 'orderCount', align: 'right', render: (v) => <span className="sf-num">{formatInt(v)}</span> },
  { title: 'Tổng chi tiêu', dataIndex: 'totalSpent', key: 'totalSpent', align: 'right', render: (v) => <PriceTag price={v} size="sm" /> },
];

/** F6 khách VIP: chi tiêu lớn hơn mức trung bình của mọi khách. Phân trang phía server. */
export default function VipCustomers({ params }) {
  const pager = usePager(JSON.stringify(params));
  const state = useReport('vip-customers', { ...params, page: pager.page, size: pager.size });
  const { rows = [], page, summary } = state.data ?? {};

  return (
    <ReportCard title="Khách VIP" state={state}>
      {summary && (
        <Typography.Paragraph type="secondary" className="sf-report-summary">
          Khách VIP là khách có tổng chi tiêu cao hơn mức trung bình.{' '}
          Chi tiêu trung bình: <strong className="sf-num sf-ink">{formatVND(summary.averageSpent)}</strong>
          {' · '}Số khách có đơn: <strong className="sf-num sf-ink">{formatInt(summary.customerCount)}</strong>
          {page && <>{' · '}Số khách VIP: <strong className="sf-num sf-ink">{formatInt(page.total)}</strong></>}
        </Typography.Paragraph>
      )}
      <Table
        rowKey="userId"
        size="small"
        columns={columns}
        dataSource={rows}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Không có khách nào vượt mức chi tiêu trung bình' }}
        pagination={{
          current: pager.page,
          pageSize: pager.size,
          total: page?.total ?? 0,
          showSizeChanger: true,
          pageSizeOptions: [10, 20, 50, 100],
          size: 'small',
          showTotal: (total, [a, b]) => `${formatInt(a)}–${formatInt(b)} / ${formatInt(total)} khách`,
          onChange: pager.onChange,
        }}
      />
    </ReportCard>
  );
}
