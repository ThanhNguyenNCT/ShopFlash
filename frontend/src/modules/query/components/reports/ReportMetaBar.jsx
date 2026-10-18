import { useState } from 'react';
import { Button, Modal, Tag, Tooltip, Typography } from 'antd';
import { CodeOutlined } from '@ant-design/icons';
import { formatInt, formatMs } from '../../format';

const SOURCE_LABEL = { oracle: 'Oracle', redis: 'Redis' };

/**
 * Dòng meta gọn ở chân mỗi khối báo cáo: nguồn + biến thể, thời gian, round-trip, số dòng,
 * và nút "Câu lệnh" mở câu SQL / lệnh Redis đã chạy.
 */
export default function ReportMetaBar({ meta, title }) {
  const [open, setOpen] = useState(false);
  if (!meta) return null;
  const supported = meta.supported !== false;

  return (
    <div className="sf-report-meta">
      <span className="sf-report-meta__tags">
        <Tag>{SOURCE_LABEL[meta.source] ?? meta.source}</Tag>
        {meta.variant && <Tag className="sf-code">{meta.variant}</Tag>}
        {!supported && <Tag>Không hỗ trợ</Tag>}
      </span>
      {supported && (
        <span className="sf-report-meta__stats sf-num">
          <Tooltip title="Thời gian gọi DB và xử lý kết quả (không tính HTTP)">
            <span>{formatMs(meta.elapsedMs)}</span>
          </Tooltip>
          <span>{formatInt(meta.roundTrips)} round-trip</span>
          <span>{formatInt(meta.rowCount)} dòng</span>
        </span>
      )}
      <Button
        size="small"
        type="link"
        icon={<CodeOutlined />}
        disabled={!meta.statement}
        onClick={() => setOpen(true)}
        className="sf-report-meta__btn"
      >
        Câu lệnh
      </Button>
      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        title={`Câu lệnh: ${title ?? meta.report}`}
        width="min(760px, calc(100vw - 32px))"
      >
        <Typography.Paragraph type="secondary">
          {SOURCE_LABEL[meta.source] ?? meta.source} · {meta.variant} · {formatMs(meta.elapsedMs)}
        </Typography.Paragraph>
        <pre className="sf-code sf-report-statement">{meta.statement}</pre>
        <Typography.Paragraph copyable={{ text: meta.statement ?? '' }} style={{ marginBottom: 0 }}>
          Sao chép câu lệnh
        </Typography.Paragraph>
      </Modal>
    </div>
  );
}
