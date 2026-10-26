const TOKEN_KEY = 'shopflash.auth.accessToken';

export function getAccessToken() {
  try { return sessionStorage.getItem(TOKEN_KEY); } catch { return null; }
}

export function saveAccessToken(token) {
  try { sessionStorage.setItem(TOKEN_KEY, token); } catch {
    throw { status: 0, code: 'STORAGE_ERROR', message: 'Trình duyệt không lưu được phiên đăng nhập trong tab này.' };
  }
}

export function clearAccessToken() {
  try { sessionStorage.removeItem(TOKEN_KEY); } catch { /* Phiên đã không còn dùng được. */ }
}
