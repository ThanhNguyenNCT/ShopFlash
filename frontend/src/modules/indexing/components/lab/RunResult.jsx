import { Card, Descriptions, Flex, Tag, Typography } from 'antd';
import { formatVND, tokens } from '@/shared/design-system';
import { formatMs } from '../../api';

/** Một lần chạy (SearchResponse): nguồn, biến thể, thời gian, số lượt gọi DB, câu lệnh và vài sản phẩm đầu. */
export default function RunResult({ title, response }) {
  if (!response) return null;
  const { data, meta } = response;
  return (
    <Card size="small" title={title ?? (meta.source === 'redis' ? 'Redis' : 'Oracle')}>
      <Flex vertical gap={tokens.space[3]}>
        <Descriptions
          size="small"
          column={{ xs: 1, sm: 2 }}
          items={[
            { key: 'source', label: 'Nguồn', children: <Tag color={meta.source === 'redis' ? 'magenta' : 'blue'}>{meta.source}</Tag> },
            { key: 'route', label: 'Đường đi', children: <span className="sf-code">{meta.route}</span> },
            { key: 'variant', label: 'Biến thể', children: <span className="sf-code">{meta.variant}</span> },
            { key: 'total', label: 'Tổng kết quả', children: <span className="sf-num">{meta.total.toLocaleString('vi-VN')}</span> },
            { key: 'ms', label: 'Thời gian gọi DB', children: <span className="sf-num">{formatMs(meta.elapsedMs)}</span> },
            { key: 'rt', label: 'Lượt gọi DB', children: <span className="sf-num">{meta.roundTrips}</span> },
          ]}
        />
        {meta.note && <Tag color="warning">{meta.note}</Tag>}
        <pre className="sf-idx-pre">{meta.statement}</pre>
        <Flex vertical gap={tokens.space[1]}>
          <Typography.Text type="secondary">Sản phẩm đầu trang:</Typography.Text>
          {data.items.length === 0 && <Typography.Text type="secondary">(không có)</Typography.Text>}
          {data.items.slice(0, 5).map((p) => (
            <Typography.Text key={p.productId} className="sf-num">
              #{p.productId} · {p.name} · {formatVND(p.price)}
            </Typography.Text>
          ))}
        </Flex>
      </Flex>
    </Card>
  );
}
