import { Alert, Button, Card, Skeleton, Spin, Tag } from 'antd';
import { ReloadOutlined } from '@ant-design/icons';
import ReportMetaBar from './ReportMetaBar';

/**
 * Khung chung của một khối báo cáo: tiêu đề + mã chức năng, trạng thái loading/lỗi riêng,
 * thân (children) và dòng meta ở chân.
 * `state` là kết quả của useReport. Khi đang tải lại mà đã có dữ liệu cũ thì giữ dữ liệu, chỉ mờ đi.
 */
export default function ReportCard({ title, feature, extra, state, children, skeletonRows = 4 }) {
  const { meta, loading, error, reload } = state;
  const hasContent = !!meta;

  let body;
  if (error) {
    body = (
      <Alert
        type="error"
        showIcon
        title={error.message || 'Không tải được báo cáo.'}
        description={
          <span>
            Mã lỗi: <span className="sf-code">{error.code}</span>
            {error.status ? <> · HTTP <span className="sf-num">{error.status}</span></> : null}
          </span>
        }
        action={<Button size="small" icon={<ReloadOutlined />} onClick={reload}>Thử lại</Button>}
      />
    );
  } else if (!hasContent) {
    body = <Skeleton active paragraph={{ rows: skeletonRows }} />;
  } else {
    body = (
      <Spin spinning={loading} delay={150}>
        <div aria-busy={loading}>{children}</div>
      </Spin>
    );
  }

  return (
    <Card
      className="sf-report-card"
      title={
        <span className="sf-report-card__title">
          {title}
          {feature && <Tag className="sf-report-card__feature">{feature}</Tag>}
        </span>
      }
      extra={extra}
    >
      {body}
      {!error && hasContent && (
        <div className="sf-report-card__footer">
          <ReportMetaBar meta={meta} title={title} />
        </div>
      )}
    </Card>
  );
}
