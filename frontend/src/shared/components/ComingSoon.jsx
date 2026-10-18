import { Card, Descriptions, Result, Tag, Typography } from 'antd';
import { ToolOutlined } from '@ant-design/icons';
import './ComingSoon.css';

// Thư mục chứa AGENTS.md của chỗ cần làm. module: tên thư mục trong src/modules, hoặc 'shared/auth'.
const moduleDir = (module) => (module.startsWith('shared/') ? `frontend/src/${module}` : `frontend/src/modules/${module}`);

/**
 * Trang chờ cho màn hình chưa làm: nói rõ ai làm, sửa file nào, màn hình phải làm gì.
 * Người phụ trách thay toàn bộ nội dung file `file` bằng màn hình thật; route và menu đã nối sẵn.
 *
 * props: feature ('F7'), title, owner ('TV4'), module ('transaction'), file (đường dẫn file trang),
 *        description, todo: string[], api?: string[], compact? (bản gọn để nhúng trong trang khác).
 */
export default function ComingSoon({ feature, title, owner, module, file, description, todo = [], api = [], compact = false }) {
  const dir = moduleDir(module);
  const heading = (
    <span className="sf-soon__title">
      {title}
      {feature && <Tag>{feature}</Tag>}
      <Tag color="warning">Đang làm</Tag>
    </span>
  );

  return (
    <Card className="sf-soon" aria-label={`${title}: đang được xây dựng`}>
      {compact ? (
        <div className="sf-soon__compact-head">
          <ToolOutlined />
          <Typography.Text strong>{heading}</Typography.Text>
        </div>
      ) : (
        <Result
          icon={<ToolOutlined />}
          title={heading}
          subTitle={description ?? `Màn hình này chưa được xây dựng. ${owner} phụ trách.`}
        />
      )}
      {compact && description && <Typography.Paragraph style={{ margin: 0 }}>{description}</Typography.Paragraph>}

      <Descriptions
        className="sf-soon__where"
        size="small"
        bordered
        column={1}
        items={[
          { key: 'owner', label: 'Người phụ trách', children: <Typography.Text strong>{owner}</Typography.Text> },
          { key: 'module', label: 'Module', children: <span className="sf-code">{dir}</span> },
          { key: 'file', label: 'File cần thay', children: <span className="sf-code">{file}</span> },
          { key: 'docs', label: 'Tài liệu', children: <span className="sf-code">{dir}/AGENTS.md</span> },
        ]}
      />

      {todo.length > 0 && (
        <section className="sf-soon__section">
          <h4>Màn hình cần làm</h4>
          <ul className="sf-soon__list">{todo.map((t) => <li key={t}>{t}</li>)}</ul>
        </section>
      )}

      {api.length > 0 && (
        <section className="sf-soon__section">
          <h4>API gợi ý (chủ module chốt và ghi vào AGENTS.md backend)</h4>
          <ul className="sf-soon__list">{api.map((a) => <li key={a} className="sf-code">{a}</li>)}</ul>
        </section>
      )}

      <div className="sf-soon__hint">
        Cách thay: sửa <span className="sf-code">{file}</span> thành màn hình thật (giữ <span className="sf-code">export default</span>).
        Route, menu và layout đã nối sẵn trong <span className="sf-code">{dir}/routes.jsx</span>; khi xong, bỏ{' '}
        <span className="sf-code">status: &apos;todo&apos;</span> trong menu (nếu có).
      </div>
    </Card>
  );
}
