import { useCallback, useEffect, useState } from 'react';
import { Alert, App, Button, Descriptions, Drawer, Empty, Flex, Popconfirm, Skeleton, Table, Tabs, Tag, Typography } from 'antd';
import { PriceTag, StockBar, tokens } from '@/shared/design-system';
import { changeSaleStatus, deleteSale, getSale } from '../api';
import { SALE_STATUS, formatDateTime, formatNumber } from '../format';
import RuntimePanel from './RuntimePanel';
import SaleFormModal from './SaleFormModal';
import SaleItemsModal from './SaleItemsModal';

// Thao tác đổi trạng thái theo từng trạng thái hiện tại. confirm: nội dung hỏi lại trước khi chạy.
const ACTIONS = {
  schedule: {
    label: 'Lên lịch', done: 'Đã lên lịch. Hàng đã được giữ trong kho cho chương trình.', primary: true,
    confirm: 'Lên lịch sẽ giữ hàng trong kho cho mọi sản phẩm (đủ hết hoặc không giữ gì). Sau đó chỉ sửa được tên và mô tả.',
  },
  unschedule: {
    label: 'Hủy lịch', done: 'Đã hủy lịch, chương trình về Nháp và trả hàng về kho.',
    confirm: 'Chương trình về Nháp và trả hàng đã giữ về kho. Chỉ làm được khi còn ít nhất 60 giây trước giờ mở bán.',
  },
  cancel: {
    label: 'Hủy chương trình', done: 'Đã hủy chương trình.', danger: true,
    confirm: 'Hủy hẳn chương trình (không khôi phục được). Hàng đã giữ được trả về kho.',
  },
  stop: {
    label: 'Dừng sớm', done: 'Đã dừng bán. Suất chưa bán sẽ về kho khi job kết thúc chương trình.', danger: true,
    confirm: 'Dừng bán ngay: khách không giữ suất được nữa. Suất đang giữ vẫn được đặt đơn tới khi hết hạn.',
  },
};

const ACTIONS_BY_STATUS = {
  DRAFT: ['schedule', 'cancel'],
  SCHEDULED: ['unschedule', 'cancel'],
  ACTIVE: ['stop'],
};

function actionError(problem, detail) {
  if (problem.code === 'INSUFFICIENT_STOCK') {
    const productId = problem.details?.productId;
    const item = detail?.items.find((i) => i.productId === productId);
    return `${problem.message ?? 'Không đủ hàng'}${item ? `: ${item.productName} (còn ${item.available} có thể bán, cần ${item.allocated})` : ''}. Không sản phẩm nào bị giữ hàng.`;
  }
  if (problem.code === 'FLASH_SALE_CHANGED') return 'Chương trình vừa được người khác sửa. Đã tải lại, kiểm tra rồi thử lại.';
  return problem.message ?? 'Không thực hiện được thao tác.';
}

/** Chi tiết một chương trình: thông tin, sản phẩm, thao tác theo trạng thái và đối chiếu runtime. */
export default function SaleDetailDrawer({ saleId, onClose, onChanged }) {
  const { message } = App.useApp();
  const [detail, setDetail] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);
  const [actionErrorText, setActionErrorText] = useState(null);
  const [busy, setBusy] = useState(null);
  const [modal, setModal] = useState(null); // 'edit' | 'items'

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setDetail(await getSale(saleId));
      setError(null);
    } catch (problem) {
      setError(problem);
    } finally {
      setLoading(false);
    }
  }, [saleId]);

  useEffect(() => { load(); }, [load]);

  const run = async (action) => {
    setBusy(action);
    setActionErrorText(null);
    try {
      setDetail(await changeSaleStatus(saleId, action));
      message.success(ACTIONS[action].done);
      onChanged();
    } catch (problem) {
      setActionErrorText(actionError(problem, detail));
      load();
    } finally {
      setBusy(null);
    }
  };

  const remove = async () => {
    setBusy('delete');
    try {
      await deleteSale(saleId);
      message.success('Đã xóa chương trình nháp.');
      onChanged();
      onClose();
    } catch (problem) {
      setActionErrorText(actionError(problem, detail));
      load();
    } finally {
      setBusy(null);
    }
  };

  const saved = (next, text) => {
    setModal(null);
    setDetail(next);
    message.success(text);
    onChanged();
  };

  const closeModal = () => {
    setModal(null);
    load(); // hộp thoại có thể đóng sau xung đột version: tải lại để có version mới
  };

  const status = detail?.status;
  const isDraft = status === 'DRAFT';
  const hasRuntime = status === 'SCHEDULED' || status === 'ACTIVE';

  const itemColumns = [
    {
      title: 'Sản phẩm', key: 'product',
      render: (_, r) => (
        <div className="sf-cc-name">
          <strong>{r.productName}</strong>
          <span className="sf-cc-code">{r.sku}</span>
          {r.productStatus !== 'ACTIVE' && <Tag color="warning">Sản phẩm ngừng bán</Tag>}
        </div>
      ),
    },
    { title: 'Giá', key: 'price', render: (_, r) => <PriceTag price={r.salePrice} originalPrice={r.basePrice} size="sm" /> },
    isDraft
      ? {
        title: 'Suất', key: 'quota', align: 'right',
        render: (_, r) => (
          <div className="sf-cc-name">
            <span className="sf-num">{formatNumber(r.allocated)}</span>
            <Typography.Text type={r.allocated > r.available ? 'danger' : 'secondary'} className="sf-cc-hint">
              Có thể bán: {formatNumber(r.available)}
            </Typography.Text>
          </div>
        ),
      }
      : { title: 'Suất', key: 'quota', render: (_, r) => <div className="sf-cc-stock"><StockBar sold={r.sold} quota={r.allocated} /></div> },
  ];

  const info = detail && (
    <Flex vertical gap={tokens.space[4]}>
      <Descriptions size="small" column={{ xs: 1, sm: 2 }} bordered items={[
        { key: 'status', label: 'Trạng thái', children: <Tag color={SALE_STATUS[status]?.color}>{SALE_STATUS[status]?.label ?? status}</Tag> },
        { key: 'version', label: 'Version', children: <span className="sf-num">{detail.version}</span> },
        { key: 'start', label: 'Bắt đầu', children: formatDateTime(detail.startAt) },
        { key: 'end', label: 'Kết thúc', children: formatDateTime(detail.endAt) },
        { key: 'max', label: 'Mỗi khách tối đa', children: `${detail.config.maxQuantityPerUser} sản phẩm mỗi mặt hàng` },
        { key: 'ttl', label: 'Giữ suất', children: `${detail.config.reservationTimeoutSeconds} giây` },
        { key: 'desc', label: 'Mô tả', span: 'filled', children: detail.description || <Typography.Text type="secondary">Không có</Typography.Text> },
      ]} />
      <Flex justify="space-between" align="center" wrap gap={tokens.space[2]}>
        <Typography.Title level={5} style={{ margin: 0 }}>Sản phẩm ({detail.items.length})</Typography.Title>
        {isDraft && <Button onClick={() => setModal('items')}>Chọn sản phẩm</Button>}
      </Flex>
      <Table size="small" rowKey="itemId" columns={itemColumns} dataSource={detail.items} pagination={false}
        scroll={{ x: 'max-content' }}
        locale={{ emptyText: <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description={isDraft ? 'Chưa có sản phẩm. Bấm "Chọn sản phẩm".' : 'Không có sản phẩm'} /> }} />
    </Flex>
  );

  const actions = detail && (
    <div className="sf-cc-actions">
      {(isDraft || hasRuntime) && (
        <Button onClick={() => setModal('edit')}>{isDraft ? 'Sửa' : 'Sửa tên, mô tả'}</Button>
      )}
      {(ACTIONS_BY_STATUS[status] ?? []).map((key) => {
        const a = ACTIONS[key];
        return (
          <Popconfirm key={key} title={`${a.label}?`} description={a.confirm} okText={a.label} cancelText="Không"
            okButtonProps={{ danger: a.danger }} onConfirm={() => run(key)}>
            <Button type={a.primary ? 'primary' : 'default'} danger={a.danger} loading={busy === key}
              disabled={Boolean(busy) || (key === 'schedule' && detail.items.length === 0)}>
              {a.label}
            </Button>
          </Popconfirm>
        );
      })}
      {isDraft && (
        <Popconfirm title="Xóa chương trình nháp?" description="Xóa hẳn chương trình và danh sách sản phẩm của nó."
          okText="Xóa" cancelText="Không" okButtonProps={{ danger: true }} onConfirm={remove}>
          <Button danger type="text" loading={busy === 'delete'} disabled={Boolean(busy)}>Xóa</Button>
        </Popconfirm>
      )}
    </div>
  );

  return (
    <Drawer open size="min(920px, 100vw)" onClose={onClose} destroyOnHidden
      title={detail ? <span>{detail.name} <span className="sf-cc-code">#{detail.flashSaleId}</span></span> : `Chương trình #${saleId}`}>
      {loading && !detail && <Skeleton active />}
      {error && !detail && (
        <Alert type="error" showIcon title={error.message ?? 'Không tải được chương trình.'}
          action={<Button size="small" onClick={load}>Thử lại</Button>} />
      )}
      {detail && (
        <Flex vertical gap={tokens.space[4]}>
          {actions}
          {isDraft && detail.items.length === 0 && (
            <Alert type="info" showIcon title="Thêm ít nhất một sản phẩm trước khi lên lịch." />
          )}
          {actionErrorText && (
            <Alert type="error" showIcon closable title={actionErrorText} onClose={() => setActionErrorText(null)} />
          )}
          {hasRuntime ? (
            <Tabs items={[
              { key: 'info', label: 'Thông tin', children: info },
              { key: 'runtime', label: 'Đối chiếu Oracle – Redis', children: <RuntimePanel saleId={saleId} status={status} /> },
            ]} />
          ) : info}
        </Flex>
      )}
      {modal === 'edit' && detail && <SaleFormModal sale={detail} onCancel={closeModal} onSaved={saved} />}
      {modal === 'items' && detail && <SaleItemsModal sale={detail} onCancel={closeModal} onSaved={saved} />}
    </Drawer>
  );
}
