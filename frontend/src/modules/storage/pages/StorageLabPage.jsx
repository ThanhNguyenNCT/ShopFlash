import { useCallback, useEffect, useState } from 'react';
import { Alert, Button, Card, Descriptions, Statistic, Table, Tag, Typography } from 'antd';
import { DatabaseOutlined, ReloadOutlined, SyncOutlined } from '@ant-design/icons';
import { getProjectionStatus, getStorageFootprint, rebuildProjection, runConstraintDemo } from '../api';
import '../storage.css';

const formatBytes = (bytes = 0) => new Intl.NumberFormat('vi-VN').format(bytes) + ' B';
const formatTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '');
const showValue = (value) => (value === null || value === undefined ? 'null' : typeof value === 'object' ? JSON.stringify(value) : String(value));

const ISSUE_LABEL = { MISSING: 'Thiếu', EXTRA: 'Thừa', MISMATCH: 'Sai lệch', INVALID: 'Không hợp lệ' };
const ISSUE_COLOR = { MISSING: 'warning', EXTRA: 'default', MISMATCH: 'error', INVALID: 'error' };

function syncTag(status) {
  if (!status) return null;
  if (status.checkState !== 'COMPLETED') return <Tag color="default">Chưa xác định</Tag>;
  return status.inSync ? <Tag color="success">Đồng bộ</Tag> : <Tag color="error">Có sai lệch</Tag>;
}

const issueColumns = [
  { title: 'Product ID', dataIndex: 'productId', render: (value, row) => value ?? row.key },
  { title: 'Loại', dataIndex: 'kind', render: (kind) => <Tag color={ISSUE_COLOR[kind]}>{ISSUE_LABEL[kind] ?? kind}</Tag> },
  { title: 'Chi tiết', dataIndex: 'message' },
  {
    title: 'Field: Oracle → Redis',
    dataIndex: 'fields',
    render: (fields) => (fields?.length ? <ul className="sf-storage-lab-diff">{fields.map((f) => <li key={f.field}><span className="sf-storage-code">{f.field}</span>: {showValue(f.oracle)} → {showValue(f.redis)}</li>)}</ul> : '—'),
  },
];

const integrityRows = [
  { key: 'fk', rule: 'Category phải tồn tại', oracle: 'FK PRODUCTS → CATEGORIES', app: 'API từ chối categoryId không tồn tại' },
  { key: 'sku', rule: 'SKU không trùng', oracle: 'UNIQUE (sku)', app: 'Kiểm tra trước khi ghi và trả lỗi rõ ràng' },
  { key: 'price', rule: 'Giá không âm', oracle: 'CHECK (base_price >= 0)', app: 'Form và API kiểm tra giá trị' },
  { key: 'inventory', rule: 'Tồn kho không thấp hơn đã giữ', oracle: 'CHECK + version', app: 'Chỉ cập nhật tồn kho; kiểm tra số đã giữ và tránh ghi đè khi có người khác sửa cùng lúc' },
];

export default function StorageLabPage() {
  const [status, setStatus] = useState(null);
  const [footprint, setFootprint] = useState(null);
  const [loadingStatus, setLoadingStatus] = useState(true);
  const [loadingFootprint, setLoadingFootprint] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const [demoLoading, setDemoLoading] = useState(false);
  const [demo, setDemo] = useState(null);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const loadStatus = useCallback(async () => {
    setLoadingStatus(true); setError(null);
    try { setStatus(await getProjectionStatus()); } catch (nextError) { setError(nextError); } finally { setLoadingStatus(false); }
  }, []);
  useEffect(() => { loadStatus(); }, [loadStatus]);
  const loadFootprint = async () => {
    setLoadingFootprint(true); setError(null);
    try { setFootprint(await getStorageFootprint()); } catch (nextError) { setError(nextError); } finally { setLoadingFootprint(false); }
  };
  const rebuild = async () => {
    setRebuilding(true); setError(null);
    try {
      const result = await rebuildProjection();
      // Kiểm tra lại theo từng Product sau rebuild, không chỉ so số lượng.
      const checked = await getProjectionStatus();
      setStatus(checked);
      const verdict = checked.checkState !== 'COMPLETED' ? 'chưa kiểm tra lại được' : checked.inSync ? 'kiểm tra lại: khớp toàn bộ' : 'kiểm tra lại: vẫn còn sai lệch';
      setNotice(`Đã dựng lại ${result.products} document trong ${result.elapsedMs} ms, ${verdict}.`);
    } catch (nextError) { setError(nextError); } finally { setRebuilding(false); }
  };
  const runDemo = async () => {
    setDemoLoading(true); setError(null);
    try { setDemo(await runConstraintDemo()); } catch (nextError) { setError(nextError); } finally { setDemoLoading(false); }
  };

  return <div className="sf-storage-page">
    <div className="sf-storage-page__head"><div className="sf-storage-page__heading"><Typography.Title level={3} style={{ margin: 0 }}>So sánh Oracle–Redis</Typography.Title><Typography.Text type="secondary">Quan sát cách Oracle giữ toàn vẹn và Redis lưu bản dữ liệu sản phẩm từ ShopFlash.</Typography.Text></div><Button icon={<ReloadOutlined />} onClick={loadStatus}>Tải lại trạng thái</Button></div>
    {notice && <Alert type="success" showIcon closable message={notice} onClose={() => setNotice(null)} />}
    {error && <Alert type="error" showIcon closable message={error.message || 'Không tải được số liệu Storage Lab'} onClose={() => setError(null)} />}
    <Card className="sf-storage-lab-card sf-storage-lab-card--projection" title="Dữ liệu sản phẩm và danh mục" loading={loadingStatus} extra={<DatabaseOutlined />}>
      <div className="sf-storage-lab-stats">
        <div className="sf-storage-lab-stat-group">
          <Statistic title="Danh mục lưu ở Oracle" value={status?.oracleCategories ?? '—'} />
          <Statistic title="Sản phẩm lưu ở Oracle" value={status?.oracleProducts ?? '—'} />
        </div>
        <Statistic className="sf-storage-lab-stat--redis" title="Document ở Redis" value={status?.redisDocuments ?? '—'} />
      </div>
      {status?.checkState === 'COMPLETED' && <div className="sf-storage-lab-check">
        <Statistic title="Khớp" value={status.matched} />
        <Statistic title="Thiếu" value={status.missing} />
        <Statistic title="Thừa" value={status.extra} />
        <Statistic title="Sai lệch" value={status.mismatched} />
        <Statistic title="Không hợp lệ" value={status.invalid} />
      </div>}
      {status?.checkState === 'FAILED' && <Alert type="warning" showIcon message={status.failureMessage} />}
      <div className="sf-storage-lab-sync-actions">
        {syncTag(status)}
        {status?.checkedAt && <span className="sf-storage-lab-note">Kiểm tra lúc {formatTime(status.checkedAt)} · {status.elapsedMs} ms</span>}
        <Button type="primary" icon={<SyncOutlined />} loading={rebuilding} onClick={rebuild}>Đồng bộ lại dữ liệu</Button>
      </div>
      <Typography.Paragraph className="sf-storage-lab-note">Đối chiếu từng sản phẩm và từng field giữa Oracle và Redis. Hai DBMS không đọc cùng một thời điểm, nên sai lệch xuất hiện khi đang có đơn hàng có thể chỉ là tạm thời; bấm Tải lại trạng thái để kiểm tra lại.</Typography.Paragraph>
      {status?.issues?.length > 0 && <Table size="small" rowKey={(row) => `${row.kind}:${row.key}`} pagination={{ pageSize: 10 }} scroll={{ x: 'max-content' }} columns={issueColumns} dataSource={status.issues}
        title={() => `Một số document cần xem (tối đa ${status.issueLimit})`} />}
    </Card>
    <Card className="sf-storage-lab-card" title="E1 · Toàn vẹn dữ liệu">
      <Typography.Paragraph type="secondary">Các rule được kiểm tra ở form/API để báo lỗi rõ ràng, đồng thời Oracle là lớp bảo vệ cuối cùng bằng constraint.</Typography.Paragraph>
      <Table size="small" rowKey="key" pagination={false} scroll={{ x: 'max-content' }} columns={[{ title: 'Rule', dataIndex: 'rule' }, { title: 'Oracle', dataIndex: 'oracle' }, { title: 'Ứng dụng', dataIndex: 'app' }]} dataSource={integrityRows} />
      <div className="sf-storage-lab-actions"><Button loading={demoLoading} onClick={runDemo}>Thử document sai trên Redis</Button></div>
      {demo && <Alert className="sf-storage-lab-result" type="warning" showIcon message="RedisJSON đã nhận document sai ràng buộc" action={<Button type="text" onClick={() => setDemo(null)}>Ẩn kết quả</Button>} description={<><Typography.Paragraph type="secondary">Key kiểm thử đã được xóa ngay; Oracle và dữ liệu sản phẩm thật trên Redis không bị thay đổi.</Typography.Paragraph><ul>{demo.oracleViolations.map((violation) => <li key={violation}>{violation}</li>)}</ul><Typography.Paragraph className="sf-storage-lab-command" copyable={{ text: demo.redisCommand }}><Typography.Text strong>Lệnh Redis đã chạy: </Typography.Text><code>{demo.redisCommand}</code></Typography.Paragraph><Typography.Text className="sf-storage-code" copyable={{ text: demo.storedDocument }}>Document Redis: {demo.storedDocument}</Typography.Text></>} />}
    </Card>
    <Card className="sf-storage-lab-card" title="E2 · Dung lượng lưu trữ">
      <Typography.Paragraph type="secondary">Đo theo dữ liệu sản phẩm và danh mục hiện tại. Hai đơn vị đo khác nhau nên chỉ dùng để so sánh trong cấu hình đã nêu.</Typography.Paragraph>
      <div className="sf-storage-lab-actions"><Button icon={<DatabaseOutlined />} loading={loadingFootprint} onClick={loadFootprint}>Đo dung lượng</Button></div>
      {footprint && <Descriptions className="sf-storage-lab-footprint" size="small" column={1} items={[{ key: 'oracle', label: 'Oracle table/index allocated', children: formatBytes(footprint.oracleAllocatedBytes) }, { key: 'redis', label: 'Redis product document keys', children: formatBytes(footprint.redisDocumentKeyBytes) }, { key: 'docs', label: 'Số Redis document', children: footprint.redisDocuments }]} />}
    </Card>
    {footprint && <Card className="sf-storage-lab-card" title="Oracle segments"><Table rowKey={(row) => `${row.type}:${row.name}`} size="small" pagination={false} scroll={{ x: 'max-content' }} columns={[{ title: 'Segment', dataIndex: 'name' }, { title: 'Loại', dataIndex: 'type' }, { title: 'Allocated bytes', dataIndex: 'bytes', align: 'right', render: (value) => <span className="sf-storage-num">{formatBytes(value)}</span> }]} dataSource={footprint.oracleSegments} /></Card>}
  </div>;
}
