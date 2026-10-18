import ComingSoon from '@/shared/components/ComingSoon';

// F0 (TV5): thay file này bằng form đăng ký thật.
export default function RegisterPage() {
  return (
    <ComingSoon
      feature="F0"
      title="Đăng ký tài khoản"
      owner="TV5"
      module="shared/auth"
      file="frontend/src/shared/auth/RegisterPage.jsx"
      description="Khách tạo tài khoản mới để mua hàng. Đăng nhập/đăng ký không tính vào 10 chức năng nhưng cần có để demo."
      todo={[
        'Form họ tên, email, số điện thoại, mật khẩu, nhập lại mật khẩu; kiểm tra dữ liệu ngay trên form (email hợp lệ, mật khẩu đủ dài, hai lần nhập khớp).',
        'Gọi API đăng ký; backend băm mật khẩu bằng bcrypt rồi lưu vào USERS (role CUSTOMER, status ACTIVE).',
        'Báo lỗi rõ ràng khi email đã tồn tại; nút có trạng thái loading khi đang gửi.',
        'Đăng ký xong thì đăng nhập luôn (hoặc chuyển về /login) và quay lại trang ?next nếu có.',
      ]}
      api={['POST /api/auth/register { fullName, email, phone, password }', 'POST /api/auth/login, POST /api/auth/logout, GET /api/auth/me']}
    />
  );
}
