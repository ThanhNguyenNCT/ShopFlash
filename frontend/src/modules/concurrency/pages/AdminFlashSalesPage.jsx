import { useCallback, useEffect, useState } from 'react';
import { Alert, App, Button, Card, Select, Table, Tag, Typography } from 'antd';
import { PlusOutlined, ReloadOutlined } from '@ant-design/icons';
import { StockBar } from '@/shared/design-system';
import { listSales } from '../api';
import { SALE_STATUS, formatDateTime, saleStatusOptions } from '../format';
import SaleDetailDrawer from '../components/SaleDetailDrawer';
import SaleFormModal from '../components/SaleFormModal';
import '../concurrency.css';

// F9 Quản lý flash sale (TV5): danh sách chương trình; chi tiết, thao tác và đối chiếu runtime ở ngăn bên phải.
// Vòng đời: Nháp → Đã lên lịch → Đang chạy (job, tới giờ) → Đã kết thúc (job). Hủy được khi Nháp hoặc Đã lên lịch.

function StatusTag({ status }) {
  return <Tag color={SALE_STATUS[status]?.color}>{SALE_STATUS[status]?.label ?? status}</Tag>;
}

export default function AdminFlashSalesPage() {
  const { message } = App.useApp();
  const [filters, setFilters] = useState({ status: undefined, page: 1, size: 20 });
  const [data, setData] = useState({ rows: [], page: 1, size: 20, total: 0 });
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [creating, setCreating] = useState(false);
  const [openId, setOpenId] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await listSales(filters));
      setError(null);
    } catch (problem) {
      setError(problem);
    } finally {
      setLoading(false);
    }
  }, [filters]);

  useEffect(() => { load(); }, [load]);

  const created = (detail, text) => {
    setCreating(false);
    message.success(text);
    load();
    setOpenId(detail.flashSaleId);
  };

  const columns = [
    {
      title: 'Chương trình', key: 'name',
      render: (_, r) => (
        <div className="sf-cc-name">
          <Typography.Link strong onClick={() => setOpenId(r.flashSaleId)}>{r.name}</Typography.Link>
          <span className="sf-cc-code">#{r.flashSaleId}</span>
          <span className="sf-cc-muted sf-cc-narrow-only">{formatDateTime(r.startAt)} – {formatDateTime(r.endAt)}</span>
          <span className="sf-cc-narrow-only"><StatusTag status={r.status} /></span>
        </div>
      ),
    },
    {
      title: 'Thời gian', key: 'time', responsive: ['md'],
      render: (_, r) => <div className="sf-cc-name"><span>{formatDateTime(r.startAt)}</span><span className="sf-cc-muted">đến {formatDateTime(r.endAt)}</span></div>,
    },
    { title: 'Trạng thái', dataIndex: 'status', responsive: ['md'], render: (v) => <StatusTag status={v} /> },
    { title: 'Sản phẩm', dataIndex: 'itemCount', align: 'right', responsive: ['md'], render: (v) => <span className="sf-num">{v}</span> },
    {
      title: 'Suất', key: 'quota', responsive: ['sm'],
      render: (_, r) => (r.allocatedTotal > 0
        ? <div className="sf-cc-stock"><StockBar sold={r.soldTotal} quota={r.allocatedTotal} /></div>
        : <Typography.Text type="secondary">Chưa có</Typography.Text>),
    },
    {
      title: '', key: 'open', align: 'right', responsive: ['sm'],
      render: (_, r) => <Button onClick={() => setOpenId(r.flashSaleId)}>Chi tiết</Button>,
    },
  ];

  return (
    <div className="sf-cc-admin">
      <div className="sf-cc-admin__head">
        <div className="sf-cc-name">
          <Typography.Title level={3} style={{ margin: 0 }}>Chương trình flash sale</Typography.Title>
          <Typography.Text type="secondary">
            Tạo chương trình nháp, chọn sản phẩm rồi lên lịch. Job tự mở bán khi tới giờ và kết thúc sau giờ kết thúc.
          </Typography.Text>
        </div>
        <Button type="primary" icon={<PlusOutlined />} onClick={() => setCreating(true)}>Tạo chương trình</Button>
      </div>

      {error && (
        <Alert type="error" showIcon title={error.message ?? 'Không tải được danh sách chương trình.'}
          action={<Button size="small" icon={<ReloadOutlined />} onClick={load}>Thử lại</Button>} />
      )}

      <Card>
        <div className="sf-cc-toolbar">
          <Select allowClear placeholder="Mọi trạng thái" aria-label="Lọc theo trạng thái" className="sf-cc-toolbar__select"
            options={saleStatusOptions} value={filters.status}
            onChange={(status) => setFilters((f) => ({ ...f, status, page: 1 }))} />
          <span className="sf-cc-toolbar__grow" />
          <Button icon={<ReloadOutlined />} onClick={load}>Tải lại</Button>
        </div>
        <Table rowKey="flashSaleId" columns={columns} dataSource={data.rows} loading={loading} scroll={{ x: 'max-content' }}
          pagination={{
            current: data.page, pageSize: data.size, total: data.total, showSizeChanger: true,
            showTotal: (total) => `${total} chương trình`,
            onChange: (page, size) => setFilters((f) => ({ ...f, page, size })),
          }} />
      </Card>

      {creating && <SaleFormModal sale={null} onCancel={() => setCreating(false)} onSaved={created} />}
      {openId && <SaleDetailDrawer key={openId} saleId={openId} onClose={() => setOpenId(null)} onChanged={load} />}
    </div>
  );
}
