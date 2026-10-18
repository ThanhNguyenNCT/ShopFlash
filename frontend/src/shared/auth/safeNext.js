// Chỉ nhận đường dẫn nội bộ ("/..."), để tham số ?next= không chuyển hướng người dùng ra trang lạ.
// Trình duyệt coi "\" như "/" và bỏ qua tab/xuống dòng, nên "/\evil.com" hay "/\t/evil.com" cũng thành "//evil.com".
// Vì vậy ngoài kiểm tra chuỗi còn phân giải thử URL và bắt buộc cùng origin.
export default function safeNext(next) {
  if (typeof next !== 'string' || !next.startsWith('/') || next.startsWith('//')) return null;
  if (/[\\\u0000-\u001f\u007f]/.test(next)) return null;
  try {
    const origin = window.location.origin;
    const url = new URL(next, origin);
    return url.origin === origin ? url.pathname + url.search + url.hash : null;
  } catch {
    return null;
  }
}
