import { Table } from 'antd';
import { columnsFor, rowKeyFor } from './labUtils';

// Bảng dòng kết quả của một báo cáo; phân trang phía client 10 dòng một trang.
export default function ResultTable({ report, rows, loading }) {
  return (
    <Table
      size="small"
      loading={loading}
      columns={columnsFor(report, rows)}
      dataSource={rows ?? []}
      rowKey={rowKeyFor(report)}
      pagination={{ pageSize: 10, size: 'small', showSizeChanger: false, hideOnSinglePage: true }}
      scroll={{ x: 'max-content' }}
    />
  );
}
