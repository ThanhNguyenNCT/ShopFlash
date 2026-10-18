import { useState } from 'react';

/**
 * Trang/size cho bảng phân trang phía server. Khi bộ lọc (resetKey) đổi thì tự quay về trang 1
 * mà không cần effect, nên không gọi API thừa với trang cũ.
 */
export default function usePager(resetKey, defaultSize = 20) {
  const [state, setState] = useState({ key: resetKey, page: 1, size: defaultSize });
  const page = state.key === resetKey ? state.page : 1;
  const size = state.size;
  const onChange = (nextPage, nextSize) => {
    setState({ key: resetKey, page: nextSize !== size ? 1 : nextPage, size: nextSize });
  };
  return { page, size, onChange };
}
