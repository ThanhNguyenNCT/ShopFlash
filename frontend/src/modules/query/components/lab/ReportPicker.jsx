import { Flex, InputNumber, Select } from 'antd';
import { REPORTS } from '../../api';
import { tokens } from '@/shared/design-system';

export const DEFAULT_PICK = { report: 'top-products', limit: 10, page: 1, size: 20 };

// Chọn báo cáo + limit (top-products) / trang, cỡ trang (báo cáo phân trang).
// reports: danh sách được phép chọn (mặc định tất cả). value/onChange: { report, limit, page, size }.
export default function ReportPicker({ value, onChange, reports = REPORTS, showPaging = true }) {
  const meta = REPORTS.find((r) => r.slug === value.report);
  const set = (patch) => onChange({ ...value, ...patch });
  // InputNumber trả null khi xóa trắng: giữ giá trị cũ để luôn gửi tham số hợp lệ (1–100, trang ≥ 1).
  const num = (field) => (v) => { if (v !== null) set({ [field]: v }); };
  return (
    <Flex wrap gap={tokens.space[3]} align="center">
      <Select
        aria-label="Báo cáo"
        className="sf-lab-full"
        style={{ minWidth: tokens.space[8] * 7, maxWidth: tokens.space[8] * 10 }}
        value={value.report}
        onChange={(report) => set({ report, page: 1 })}
        options={reports.map((r) => ({ value: r.slug, label: `${r.feature} · ${r.label}` }))}
      />
      {value.report === 'top-products' && (
        <InputNumber prefix="Top" min={1} max={100} precision={0} value={value.limit} onChange={num('limit')} />
      )}
      {showPaging && meta?.paged && (
        <>
          <InputNumber prefix="Trang" min={1} precision={0} value={value.page} onChange={num('page')} />
          <InputNumber prefix="Cỡ trang" min={1} max={100} precision={0} value={value.size} onChange={num('size')} />
        </>
      )}
    </Flex>
  );
}
