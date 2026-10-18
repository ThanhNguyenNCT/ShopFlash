import { Descriptions, Tag } from 'antd';
import { HINT_LABEL, formatCount, formatMs } from './labUtils';

// Tóm tắt meta của một response báo cáo: biến thể, thời gian, số round-trip, số dòng.
export default function MetaInfo({ meta }) {
  if (!meta) return null;
  return (
    <Descriptions
      size="small"
      layout="vertical"
      column={{ xs: 2, md: 4 }}
      bordered
      items={[
        { key: 'variant', label: 'Biến thể', children: <Tag className="sf-lab-tag-wrap">{HINT_LABEL[meta.variant] ?? meta.variant}</Tag> },
        { key: 'ms', label: 'Thời gian', children: <span className="sf-num">{formatMs(meta.elapsedMs)}</span> },
        { key: 'rt', label: 'Round-trip', children: <span className="sf-num">{formatCount(meta.roundTrips)}</span> },
        { key: 'rows', label: 'Số dòng', children: <span className="sf-num">{formatCount(meta.rowCount)}</span> },
      ]}
    />
  );
}
