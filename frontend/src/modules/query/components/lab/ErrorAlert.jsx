import { Alert, Typography } from 'antd';

const HINTS = {
  REDIS_NOT_BUILT: 'Read model Redis chưa được dựng. Mở tab "Nhất quán" và bấm "Dựng lại read model Redis".',
  REDIS_UNAVAILABLE: 'Không kết nối được Redis. Kiểm tra container Redis đã chạy chưa.',
  NETWORK_ERROR: 'Không gọi được backend. Kiểm tra Spring Boot đang chạy ở cổng 8080, hoặc chạy chế độ mock.',
  ORACLE_ERROR: 'Oracle báo lỗi. Xem chi tiết bên dưới.',
};

// Hiện lỗi đã chuẩn hóa { status, code, message, details } của shared/api/client.
export default function ErrorAlert({ error, title = 'Không chạy được' }) {
  if (!error) return null;
  const code = error.code ?? 'ERROR';
  const hint = HINTS[code];
  return (
    <Alert
      type="error"
      showIcon
      title={`${title}: ${error.message ?? String(error)}`}
      description={
        <>
          <Typography.Text type="secondary" className="sf-code">
            {error.status ? `HTTP ${error.status} · ` : ''}{code}
          </Typography.Text>
          {hint && <div>{hint}</div>}
          {error.details && (
            <pre className="sf-lab-mono sf-lab-mono--wrap">{JSON.stringify(error.details, null, 2)}</pre>
          )}
        </>
      }
    />
  );
}
