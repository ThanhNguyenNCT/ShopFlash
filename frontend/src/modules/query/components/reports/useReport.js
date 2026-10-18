import { useCallback, useEffect, useRef, useState } from 'react';
import { getReport } from '../../api';

/**
 * Gọi một báo cáo và giữ { data, meta, loading, error } cho riêng khối đó.
 * Gọi lại khi params đổi (so theo giá trị). Response cũ về sau response mới thì bị bỏ qua.
 */
export default function useReport(report, params) {
  const key = JSON.stringify(params);
  const [state, setState] = useState({ data: null, meta: null, loading: true, error: null });
  const latest = useRef(0);
  const [tick, setTick] = useState(0);

  useEffect(() => {
    const id = ++latest.current;
    setState((s) => ({ ...s, loading: true, error: null }));
    getReport(report, JSON.parse(key))
      .then((res) => {
        if (id === latest.current) setState({ data: res?.data ?? null, meta: res?.meta ?? null, loading: false, error: null });
      })
      .catch((err) => {
        if (id !== latest.current) return;
        // Lỗi đã được client chuẩn hóa thành { status, code, message, details }; lỗi khác (vd. mock) thì bọc lại.
        const error = err && typeof err === 'object' && 'code' in err
          ? err
          : { status: 0, code: 'CLIENT_ERROR', message: err?.message ?? String(err), details: null };
        setState((s) => ({ ...s, loading: false, error }));
      });
  }, [report, key, tick]);

  const reload = useCallback(() => setTick((t) => t + 1), []);
  return { ...state, reload };
}
