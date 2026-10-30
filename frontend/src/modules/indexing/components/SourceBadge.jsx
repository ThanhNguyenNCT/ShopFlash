import { Flex, Tag, Typography } from 'antd';
import { tokens } from '@/shared/design-system';
import { formatMs } from '../api';

/**
 * Cho biết kết quả lấy từ đâu (phục vụ demo chủ đề Indexing): RediSearch khi có từ khóa, Oracle khi duyệt/lọc.
 * Thời gian là thời gian backend gọi DB (meta.elapsedMs), không gồm mạng tới trình duyệt.
 */
export default function SourceBadge({ meta }) {
  if (!meta) return null;
  const isRedis = meta.source === 'redis';
  return (
    <Flex wrap gap={tokens.space[2]} align="center">
      <Tag color={isRedis ? 'magenta' : 'blue'}>{isRedis ? 'RediSearch' : 'Oracle'}</Tag>
      <Typography.Text type="secondary" className="sf-num">
        {meta.route === 'KEYWORD' ? 'Tìm theo từ khóa' : 'Duyệt và lọc'} · {formatMs(meta.elapsedMs)} ·{' '}
        {meta.roundTrips} lượt gọi DB
      </Typography.Text>
      {meta.note && <Tag color="warning">{meta.note}</Tag>}
    </Flex>
  );
}
