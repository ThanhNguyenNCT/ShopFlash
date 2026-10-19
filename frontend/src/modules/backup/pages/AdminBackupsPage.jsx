import { useCallback, useEffect, useMemo, useState } from 'react';
import { Alert, Button, Card, Col, Row, Statistic, Steps, Table, Tag, Tooltip, Typography } from 'antd';
import { CloudUploadOutlined, DatabaseOutlined, ReloadOutlined, SafetyCertificateOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { createBackup, getBackups, getBackupSystemStatus } from '../api';
import '../backup.css';

const PAGE_SIZE = 10;
const numberOrDash = (value) => (value === null || value === undefined ? '—' : value);
const formatBytes = (value) => (value === null || value === undefined ? '—' : `${new Intl.NumberFormat('vi-VN').format(value)} B`);
const formatTime = (value) => (value ? new Date(value).toLocaleString('vi-VN') : '—');

function sourceTag(status) {
  if (status === 'UP') return <Tag color="success">Sẵn sàng</Tag>;
  if (status === 'DOWN') return <Tag color="error">Không kết nối được</Tag>;
  return <Tag>Chưa tải</Tag>;
}

function backupTag(status) {
  if (status === 'COMPLETED') return <Tag color="success">Hoàn tất</Tag>;
  if (status === 'FAILED') return <Tag color="error">Thất bại</Tag>;
  return <Tag>{status}</Tag>;
}

export default function AdminBackupsPage() {
  const [status, setStatus] = useState(null);
  const [history, setHistory] = useState({ rows: [], page: 1, size: PAGE_SIZE, total: 0 });
  const [page, setPage] = useState(1);
  const [loading, setLoading] = useState(true);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState(null);
  const [notice, setNotice] = useState(null);

  const load = useCallback(async (targetPage) => {
    setLoading(true);
    setError(null);
    try {
      const [nextStatus, nextHistory] = await Promise.all([
        getBackupSystemStatus(),
        getBackups({ page: targetPage, size: PAGE_SIZE }),
      ]);
      setStatus(nextStatus);
      setHistory(nextHistory);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(page); }, [load, page]);

  const create = async () => {
    setCreating(true);
    setError(null);
    setNotice(null);
    try {
      const backup = await createBackup();
      setNotice(`Đã tạo bản sao lưu ${backup.backupId}: Oracle ${formatBytes(backup.oracle?.bytes)}, Redis ${formatBytes(backup.redis?.bytes)}.`);
      if (page === 1) await load(1);
      else setPage(1);
    } catch (nextError) {
      setError(nextError);
    } finally {
      setCreating(false);
    }
  };

  const oracle = status?.oracle;
  const redis = status?.redis;
  const runner = status?.runner;
  const canCreate = !loading && !creating && runner?.configured && oracle?.status === 'UP' && redis?.status === 'UP';
  const createHint = canCreate
    ? 'Tạo tệp Data Pump cho toàn bộ lược đồ SHOP và tệp RDB cho toàn bộ dữ liệu Redis.'
    : 'Cần Docker Compose, Oracle và Redis ở trạng thái sẵn sàng trước khi tạo bản sao lưu.';

  const columns = useMemo(() => [
    { title: 'Mã sao lưu', dataIndex: 'backupId', ellipsis: true },
    { title: 'Thời điểm', dataIndex: 'createdAt', render: formatTime },
    { title: 'Tệp Data Pump Oracle', dataIndex: 'oracle', align: 'right', className: 'sf-num', render: (value) => formatBytes(value?.bytes) },
    { title: 'Redis RDB', dataIndex: 'redis', align: 'right', className: 'sf-num', render: (value) => formatBytes(value?.bytes) },
    { title: 'SHA-256', dataIndex: 'oracle', render: (value) => value?.sha256 ? `${value.sha256.slice(0, 12)}…` : '—' },
    { title: 'Trạng thái', dataIndex: 'status', render: backupTag },
  ], []);

  return (
    <div className="sf-backup-page">
      <div className="sf-backup-page__head">
        <div className="sf-backup-page__heading">
          <Typography.Title level={3}>Sao lưu và phục hồi</Typography.Title>
          <Typography.Text type="secondary">Sao lưu toàn bộ lược đồ SHOP và dữ liệu Redis của ShopFlash vào các tệp cục bộ kèm thông tin kiểm tra.</Typography.Text>
        </div>
        <Button icon={<ReloadOutlined />} loading={loading} onClick={() => load(page)}>Tải lại trạng thái</Button>
      </div>

      {notice && <Alert type="success" showIcon closable message={notice} onClose={() => setNotice(null)} />}
      {error && <Alert type="error" showIcon closable message={error.message || 'Không tải được trạng thái sao lưu'} onClose={() => setError(null)} />}

      <Row gutter={[tokens.space[4], tokens.space[4]]}>
        <Col xs={24} xl={12}>
          <Card className="sf-backup-card" loading={loading} title="Oracle · toàn bộ lược đồ SHOP" extra={sourceTag(oracle?.status)}>
            <div className="sf-backup-stat-grid">
              <Statistic title="Bảng nghiệp vụ" value={numberOrDash(oracle?.applicationTables)} />
              <Statistic title="Danh mục · mốc kiểm tra" value={numberOrDash(oracle?.categories)} />
              <Statistic title="Sản phẩm · mốc kiểm tra" value={numberOrDash(oracle?.products)} />
              <Statistic title="Tồn kho · mốc kiểm tra" value={numberOrDash(oracle?.inventoryRows)} />
            </div>
          </Card>
        </Col>
        <Col xs={24} xl={12}>
          <Card className="sf-backup-card" loading={loading} title="Redis · toàn bộ dữ liệu ShopFlash" extra={sourceTag(redis?.status)}>
            <div className="sf-backup-stat-grid">
              <Statistic title="Tổng số khóa" value={numberOrDash(redis?.totalKeys)} />
              <Statistic title="Tài liệu JSON sản phẩm · mốc kiểm tra" value={numberOrDash(redis?.productDocuments)} />
            </div>
          </Card>
        </Col>
      </Row>

      <Card className="sf-backup-card" title="Tạo bản sao lưu" extra={<CloudUploadOutlined />}>
        <div className="sf-backup-runner">
          <div>
            <Typography.Text strong>Trình tạo bản sao lưu</Typography.Text>
            <Typography.Paragraph type="secondary">
              {runner?.message || 'Đang kiểm tra cấu hình trình tạo bản sao lưu.'}
            </Typography.Paragraph>
          </div>
          <Tooltip title={createHint}>
            <span><Button type="primary" icon={<CloudUploadOutlined />} loading={creating} disabled={!canCreate} onClick={create}>Tạo bản sao lưu</Button></span>
          </Tooltip>
        </div>
        <Alert
          className="sf-backup-card__notice"
          type="info"
          showIcon
          message="Bản sao lưu được lưu ngoài Docker volume"
          description="Mỗi bản sao lưu gồm tệp Oracle Data Pump, Redis RDB và tệp thông tin ghi kích thước, SHA-256 cùng số liệu kiểm tra ban đầu. Muốn khôi phục ghi đè, hãy dừng ứng dụng rồi chạy tập lệnh khôi phục."
        />
      </Card>

      <Card className="sf-backup-card" title="Lịch sử sao lưu">
        <Table
          rowKey="backupId"
          columns={columns}
          dataSource={history.rows}
          loading={loading}
          scroll={{ x: 'max-content' }}
          pagination={{
            current: history.page,
            pageSize: history.size,
            total: history.total,
            showSizeChanger: false,
            onChange: setPage,
          }}
        />
      </Card>

      <Card className="sf-backup-card" title="Quy trình khôi phục minh họa" extra={<SafetyCertificateOutlined />}>
        <Steps
          responsive
          current={0}
          items={[
            { title: 'Tạo bản sao lưu', description: 'Trình sao lưu toàn bộ lược đồ và dữ liệu Redis, rồi tạo tệp thông tin.' },
            { title: 'Giả lập mất dữ liệu', description: 'Dừng ứng dụng rồi chạy tập lệnh có kiểm soát.' },
            { title: 'Khôi phục và xác minh', description: 'Đối chiếu số liệu và sản phẩm mẫu với tệp thông tin.' },
          ]}
        />
        <Typography.Paragraph className="sf-backup-card__caption" type="secondary">
          Không thể khôi phục ghi đè trực tiếp từ trang này. Việc nhập dữ liệu vào Oracle và khôi phục Redis RDB chỉ được chạy sau khi ứng dụng dừng, theo tập lệnh minh họa.
        </Typography.Paragraph>
      </Card>

      {status?.generatedAt && <Typography.Text className="sf-backup-page__updated" type="secondary">Cập nhật lúc {formatTime(status.generatedAt)}</Typography.Text>}
      <DatabaseOutlined className="sf-backup-page__decoration" aria-hidden="true" />
    </div>
  );
}
