import { useCallback, useEffect, useState } from 'react';
import { Alert, App, Button, Flex, Popconfirm, Switch, Table, Tag, Typography } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';
import { getRuntime, rebuildRuntime } from '../api';
import { formatDateTime, formatNumber } from '../format';

const AUTO_REFRESH_MS = 2000;

/**
 * Đối chiếu Oracle (đã bán) với Redis (còn lại) của một chương trình SCHEDULED/ACTIVE.
 * held = allocated - sold - remaining là số suất khách đang giữ nhưng chưa đặt đơn; âm hoặc thiếu key là lệch.
 */
export default function RuntimePanel({ saleId, status }) {
  const { message } = App.useApp();
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);
  const [auto, setAuto] = useState(false);
  const [rebuilding, setRebuilding] = useState(false);
  const canRebuild = status === 'SCHEDULED' || status === 'ACTIVE';

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setData(await getRuntime(saleId));
      setError(null);
    } catch (problem) {
      setError(problem);
    } finally {
      setLoading(false);
    }
  }, [saleId]);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (!auto) return undefined;
    const timer = setInterval(() => { if (document.visibilityState === 'visible') load(); }, AUTO_REFRESH_MS);
    return () => clearInterval(timer);
  }, [auto, load]);

  const rebuild = async () => {
    setRebuilding(true);
    try {
      const result = await rebuildRuntime(saleId);
      message.success(`Đã dựng lại ${result.items} sản phẩm; bỏ ${result.droppedReservations} suất đang giữ.`);
      load();
    } catch (problem) {
      message.error(problem.message ?? 'Không dựng lại được runtime.');
    } finally {
      setRebuilding(false);
    }
  };

  const columns = [
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, r) => <div className="sf-cc-name"><strong>{r.productName}</strong><span className="sf-cc-code">#{r.productId}</span></div>,
    },
    { title: 'Phân bổ', dataIndex: 'allocated', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    { title: 'Đã bán (Oracle)', dataIndex: 'sold', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    { title: 'Còn lại (Redis)', dataIndex: 'remaining', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    { title: 'Đang giữ', dataIndex: 'held', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    { title: 'Reservation chờ', dataIndex: 'pendingReservations', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    {
      title: 'Đối chiếu', dataIndex: 'inSync',
      render: (v) => (v ? <Tag color="success">Khớp</Tag> : <Tag color="error">Lệch</Tag>),
    },
  ];

  const violationColumns = [
    { title: 'Sản phẩm', dataIndex: 'productId', render: (v) => <span className="sf-cc-code">#{v}</span> },
    { title: 'reserved_flash thực tế', dataIndex: 'reservedFlash', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
    { title: 'Đáng lẽ phải là', dataIndex: 'expectedReserved', align: 'right', render: (v) => <span className="sf-num">{formatNumber(v)}</span> },
  ];

  return (
    <Flex vertical gap={tokens.space[3]}>
      <Typography.Paragraph type="secondary" style={{ margin: 0 }}>
        Đã bán lấy từ Oracle, còn lại lấy từ Redis. Đang giữ = phân bổ − đã bán − còn lại: số suất khách đã giữ nhưng chưa
        đặt đơn, phải bằng số reservation đang chờ (có thể lệch trong lúc job dọn chưa chạy). Đang giữ âm hoặc Redis
        thiếu key là lệch.
      </Typography.Paragraph>
      <Flex wrap gap={tokens.space[3]} align="center">
        <Button icon={<ReloadOutlined />} onClick={load} loading={loading}>Tải lại</Button>
        <Flex gap={tokens.space[2]} align="center">
          <Switch checked={auto} onChange={setAuto} aria-label="Tự tải lại mỗi 2 giây" />
          <span>Tự tải lại mỗi 2 giây</span>
        </Flex>
        <span className="sf-cc-toolbar__grow" />
        {canRebuild && (
          <Popconfirm title="Dựng lại runtime từ Oracle?"
            description="Các suất khách đang giữ (chưa đặt đơn) sẽ bị bỏ và mở bán lại."
            okText="Dựng lại" cancelText="Không" okButtonProps={{ danger: true }} onConfirm={rebuild}>
            <Button loading={rebuilding}>Dựng lại runtime</Button>
          </Popconfirm>
        )}
      </Flex>
      {error && <Alert type="error" showIcon title={error.message ?? 'Không tải được runtime.'} />}
      {data && (
        <>
          <Typography.Text type="secondary">
            Giờ server {formatDateTime(data.serverTime)} · Trạng thái Oracle {data.status}
          </Typography.Text>
          {!data.runtimeLoaded && canRebuild && (
            <Alert type="warning" showIcon title="Redis chưa có key runtime của chương trình này"
              description="Khách chưa giữ suất được. Job nạp lại chạy mỗi 30 giây; hoặc bấm Dựng lại runtime." />
          )}
          <Table size="small" rowKey="itemId" columns={columns} dataSource={data.items} pagination={false}
            scroll={{ x: 'max-content' }} rowClassName={(r) => (r.inSync ? '' : 'sf-cc-row--bad')} />
          {data.invariantViolations.length === 0
            ? <Alert type="success" showIcon title="Bất biến tồn kho đúng: reserved_flash của mọi sản phẩm khớp tổng suất chưa bán." />
            : (
              <Alert type="error" showIcon title="Bất biến tồn kho bị lệch (v_inventory_invariant_check)"
                description={<Table size="small" rowKey="productId" columns={violationColumns}
                  dataSource={data.invariantViolations} pagination={false} scroll={{ x: 'max-content' }} />} />
            )}
        </>
      )}
    </Flex>
  );
}
