import { useEffect } from 'react';
import { Alert, Button, Card, Flex, Popconfirm, Space, Spin, Table, Tag, Typography } from 'antd';
import { BuildOutlined, CheckCircleOutlined, ExclamationCircleOutlined, ReloadOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { getAnalyticsStatus, rebuildAnalytics } from '../../api';
import ErrorAlert from './ErrorAlert';
import { formatCount, formatDateTime, formatMs, useApi } from './labUtils';

const diffCell = (oracle, redis) => {
  if (oracle === undefined || redis === undefined || oracle === null || redis === null) return '—';
  const d = redis - oracle;
  if (d === 0) return <Tag color="success">Bằng nhau</Tag>;
  return <Tag color="error" className="sf-num">{d > 0 ? '+' : ''}{formatCount(d)}</Tag>;
};

const COLUMNS = [
  { title: 'Đại lượng', dataIndex: 'label' },
  { title: 'Oracle (nguồn gốc)', dataIndex: 'oracle', align: 'right', render: (v) => <span className="sf-num">{formatCount(v)}</span> },
  { title: 'Redis (read model)', dataIndex: 'redis', align: 'right', render: (v) => <span className="sf-num">{formatCount(v)}</span> },
  { title: 'Chênh (Redis − Oracle)', key: 'diff', render: (_, r) => diffCell(r.oracle, r.redis) },
];

// D6: Oracle là nguồn gốc, Redis chỉ là bản sao dựng lại theo lô → nhất quán sau (eventual consistency).
export default function ConsistencySection() {
  const status = useApi(getAnalyticsStatus);
  const rebuild = useApi(rebuildAnalytics);
  const { run: loadStatus } = status;

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const doRebuild = async () => {
    const res = await rebuild.run();
    if (res) loadStatus();
  };

  const s = status.data;
  const last = formatDateTime(s?.redis?.lastRebuildAt);
  const rows = s
    ? [
        { key: 'orders', label: 'Đơn được tính (CONFIRMED, COMPLETED)', oracle: s.oracle?.countedOrders, redis: s.redis?.orderDocs },
        { key: 'lines', label: 'Dòng hàng', oracle: s.oracle?.orderItems, redis: s.redis?.lineDocs },
      ]
    : [];

  return (
    <Space orientation="vertical" size={tokens.space[4]} className="sf-lab-full">
      <Typography.Paragraph>
        Oracle giữ dữ liệu giao dịch và là nguồn gốc. Read model trên Redis được dựng lại toàn bộ từ Oracle theo lô, không cập nhật cùng
        lúc với đơn hàng, nên giữa hai lần dựng có thể lệch (nhất quán sau). Oracle thì luôn đọc được dữ liệu đã commit mới nhất.
      </Typography.Paragraph>
      <Alert type="info" showIcon title="Cách demo: chèn một đơn mới vào Oracle rồi bấm Làm mới để thấy lệch." />
      <Card
        size="small"
        title={
          <Flex wrap gap={tokens.space[2]} align="center">
            <span>Trạng thái read model</span>
            {s && (s.inSync ? (
              <Tag color="success" icon={<CheckCircleOutlined />}>Đồng bộ</Tag>
            ) : (
              <Tag color="error" icon={<ExclamationCircleOutlined />}>Lệch</Tag>
            ))}
            {s?.redis?.indexing && <Tag color="processing">Đang đánh index</Tag>}
          </Flex>
        }
      >
        <Spin spinning={status.loading}>
          <Space orientation="vertical" size={tokens.space[3]} className="sf-lab-full">
            <ErrorAlert error={status.error} title="Không đọc được trạng thái" />
            <Table size="small" columns={COLUMNS} dataSource={rows} pagination={false} scroll={{ x: 'max-content' }} />
            <Typography.Text>
              Dựng lần cuối:{' '}
              {last ? (
                <Typography.Text strong title={last.absolute}>
                  {last.relative} ({last.absolute})
                </Typography.Text>
              ) : (
                <Typography.Text type="secondary">chưa dựng</Typography.Text>
              )}
              {s?.redis?.lastRebuildMs != null && (
                <Typography.Text type="secondary" className="sf-num"> · mất {formatMs(s.redis.lastRebuildMs)}</Typography.Text>
              )}
            </Typography.Text>
          </Space>
        </Spin>
      </Card>
      <Flex wrap gap={tokens.space[3]}>
        <Button icon={<ReloadOutlined />} loading={status.loading} onClick={() => loadStatus()}>
          Làm mới
        </Button>
        <Popconfirm
          title="Dựng lại read model Redis?"
          description="Xóa toàn bộ order:analytics:* và order:line:* rồi nạp lại từ Oracle. Có thể mất vài chục giây."
          okText="Dựng lại"
          cancelText="Hủy"
          onConfirm={doRebuild}
        >
          <Button type="primary" icon={<BuildOutlined />} loading={rebuild.loading}>
            Dựng lại read model Redis
          </Button>
        </Popconfirm>
      </Flex>
      {rebuild.loading && <Alert type="info" showIcon title="Đang dựng lại read model, vui lòng chờ…" />}
      <ErrorAlert error={rebuild.error} title="Dựng lại thất bại" />
      {rebuild.data && !rebuild.loading && (
        <Alert
          type="success"
          showIcon
          title={`Đã dựng lại trong ${formatMs(rebuild.data.elapsedMs)}`}
          description={`Nạp ${formatCount(rebuild.data.orders)} đơn và ${formatCount(rebuild.data.lines)} dòng hàng từ Oracle.`}
        />
      )}
    </Space>
  );
}
