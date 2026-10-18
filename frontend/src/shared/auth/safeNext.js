// Chỉ nhận đường dẫn nội bộ ("/..."), bỏ "//host" hay URL ngoài để tránh chuyển hướng ra trang lạ.
export default function safeNext(next) {
  return typeof next === 'string' && next.startsWith('/') && !next.startsWith('//') ? next : null;
}
