import { Alert, Table, Typography } from 'antd';
import { PriceTag } from '@/shared/design-system';
import ReportCard from './ReportCard';
import useReport from './useReport';
import usePager from './usePager';
import { formatInt } from '../../format';

const columns = [
  { title: 'Mã', dataIndex: 'productId', key: 'productId', render: (v) => <span className="sf-code">#{v}</span> },
  { title: 'Sản phẩm', dataIndex: 'productName', key: 'productName' },
  { title: 'Danh mục', dataIndex: 'categoryName', key: 'categoryName' },
  { title: 'Giá niêm yết', dataIndex: 'basePrice', key: 'basePrice', align: 'right', render: (v) => <PriceTag price={v} size="sm" /> },
];

/**
 * F6 sản phẩm đang bán nhưng chưa từng có trong đơn nào (NOT EXISTS). Không phụ thuộc khoảng ngày.
 * Redis trả meta.supported=false vì không có anti-join: hiển thị như một kết quả so sánh, không phải lỗi.
 */
export default function UnsoldProducts({ params }) {
  // params không có from/to (trang không gửi cho báo cáo này), nên đổi ngày không làm tải lại.
  const pager = usePager(JSON.stringify(params));
  const state = useReport('unsold-products', { ...params, page: pager.page, size: pager.size });
  const { meta } = state;
  const unsupported = meta?.supported === false;
  const { rows = [], page } = state.data ?? {};

  return (
    <ReportCard title="Sản phẩm chưa từng bán" state={state}>
      {unsupported ? (
        <Alert
          type="info"
          showIcon
          title="Redis không chạy được báo cáo này"
          description={
            <>
              <Typography.Paragraph style={{ marginBottom: 0 }}>
                {meta.note || 'Nguồn dữ liệu này không hỗ trợ báo cáo.'}
              </Typography.Paragraph>
              <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
                Câu hỏi "sản phẩm nào không có trong đơn hàng nào" cần phép anti-join (<span className="sf-code">NOT EXISTS</span>).
                RediSearch chỉ lọc và gom nhóm trên tài liệu đang có, nên không trả lời được về những gì không tồn tại.
                Chuyển nguồn sang Oracle để xem kết quả.
              </Typography.Paragraph>
            </>
          }
        />
      ) : (
        <>
          <Typography.Paragraph type="secondary" className="sf-report-summary">
            Sản phẩm đang bán chưa xuất hiện trong đơn nào, tính trên toàn bộ dữ liệu (không theo khoảng ngày).
          </Typography.Paragraph>
          <Table
            rowKey="productId"
            size="small"
            columns={columns}
            dataSource={rows}
            scroll={{ x: 'max-content' }}
            locale={{ emptyText: 'Mọi sản phẩm đều đã bán được ít nhất một lần' }}
            pagination={{
              current: pager.page,
              pageSize: pager.size,
              total: page?.total ?? 0,
              showSizeChanger: true,
              pageSizeOptions: [10, 20, 50, 100],
              size: 'small',
              showTotal: (total, [a, b]) => `${formatInt(a)}–${formatInt(b)} / ${formatInt(total)} sản phẩm`,
              onChange: pager.onChange,
            }}
          />
        </>
      )}
    </ReportCard>
  );
}
