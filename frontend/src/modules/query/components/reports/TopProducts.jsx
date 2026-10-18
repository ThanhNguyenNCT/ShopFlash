import { useState } from 'react';
import { Segmented, Table } from 'antd';
import { PriceTag, tokens } from '@/shared/design-system';
import ReportCard from './ReportCard';
import useReport from './useReport';
import { formatInt } from '../../format';

const LIMITS = [5, 10, 20];

const columns = [
  { title: '#', dataIndex: 'rank', key: 'rank', align: 'right', width: tokens.space[8] + tokens.space[4], render: (v) => <span className="sf-num">{v}</span> },
  {
    title: 'Sản phẩm', dataIndex: 'productName', key: 'productName',
    render: (name, row) => (
      <span>
        {name} <span className="sf-code sf-muted">#{row.productId}</span>
      </span>
    ),
  },
  { title: 'Danh mục', dataIndex: 'categoryName', key: 'categoryName' },
  { title: 'Số lượng', dataIndex: 'quantity', key: 'quantity', align: 'right', render: (v) => <span className="sf-num">{formatInt(v)}</span> },
  { title: 'Doanh thu', dataIndex: 'revenue', key: 'revenue', align: 'right', render: (v) => <PriceTag price={v} size="sm" /> },
];

/** F6 top sản phẩm bán chạy (sắp theo số lượng). Danh sách đã giới hạn bởi `limit` nên không phân trang. */
export default function TopProducts({ params }) {
  const [limit, setLimit] = useState(10);
  const state = useReport('top-products', { ...params, limit });

  return (
    <ReportCard
      title="Top sản phẩm bán chạy"
      feature="F6"
      state={state}
      extra={
        <Segmented
          size="small"
          aria-label="Số sản phẩm"
          value={limit}
          onChange={setLimit}
          options={LIMITS.map((n) => ({ value: n, label: `Top ${n}` }))}
        />
      }
    >
      <Table
        rowKey="productId"
        size="small"
        columns={columns}
        dataSource={state.data?.rows ?? []}
        pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: 'Chưa có sản phẩm nào được bán trong khoảng thời gian này' }}
      />
    </ReportCard>
  );
}
