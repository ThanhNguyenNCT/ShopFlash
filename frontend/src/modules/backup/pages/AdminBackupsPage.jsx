import ComingSoon from '@/shared/components/ComingSoon';

// F11 Sao lưu và phục hồi (TV1). PLACEHOLDER: thay toàn bộ file này bằng màn hình thật, giữ export default.
// Route và menu đã khai báo trong ../routes.jsx.
export default function AdminBackupsPage() {
  return (
    <ComingSoon
      feature="F11"
      title="Sao lưu và phục hồi"
      owner="TV1"
      module="backup"
      file="frontend/src/modules/backup/pages/AdminBackupsPage.jsx"
      description="Tạo bản sao lưu Oracle và Redis, xem lịch sử và phục hồi."
      todo={[
        'Nút tạo bản sao lưu Oracle (RMAN hoặc Data Pump) và Redis (RDB/AOF); hỏi xác nhận, có loading khi đang chạy.',
        'Bảng lịch sử: thời điểm, loại, nguồn, kích thước, thời gian chạy, trạng thái (Tag kèm chữ); phân trang.',
        'Phục hồi từ một bản sao lưu: Popconfirm cảnh báo rõ dữ liệu hiện tại sẽ bị ghi đè.',
        'Hiện số liệu đo được (thời gian sao lưu, thời gian phục hồi, dữ liệu mất) để so sánh Oracle và Redis.',
      ]}
      api={[
        'GET /api/admin/backups?page=&size=',
        'POST /api/admin/backups { source, type }',
        'POST /api/admin/backups/{id}/restore',
      ]}
    />
  );
}
