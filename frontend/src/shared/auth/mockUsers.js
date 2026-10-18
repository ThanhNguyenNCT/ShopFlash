// Tài khoản giả, khớp dev seed (backend/src/main/resources/db/dev-seed/R__00_core_seed.sql).
// Chỉ dùng cho bản F0 tạm. Khi TV5 làm đăng nhập thật, user lấy từ backend và file này bị xóa.
export const MOCK_USERS = {
  CUSTOMER: { userId: 2, email: 'customer@shopflash.local', fullName: 'ShopFlash Customer', role: 'CUSTOMER' },
  ADMIN: { userId: 1, email: 'admin@shopflash.local', fullName: 'ShopFlash Admin', role: 'ADMIN' },
};
