import dayjs from 'dayjs';
import { DatePicker, Segmented, Select, Tooltip } from 'antd';
import { InfoCircleOutlined } from '@ant-design/icons';
import { JOIN_HINTS, REDIS_MODES, SOURCES } from '../../api';
import { defaultRange } from '../../format';

const SOURCE_OPTIONS = SOURCES.map((s) => ({ value: s, label: s === 'oracle' ? 'Oracle' : 'Redis' }));
const JOIN_HINT_LABEL = { AUTO: 'Tự chọn (AUTO)', NL: 'Nested loops (NL)', HASH: 'Hash join (HASH)', MERGE: 'Sort-merge (MERGE)' };
const REDIS_MODE_LABEL = { AGGREGATE: 'FT.AGGREGATE', CLIENT: 'Tính ở Java' };

const presets = () => {
  const today = dayjs();
  return [
    { label: '12 tháng gần nhất', value: defaultRange() },
    { label: '6 tháng gần nhất', value: [today.subtract(5, 'month').startOf('month'), today] },
    { label: 'Tháng này', value: [today.startOf('month'), today] },
    { label: 'Năm nay', value: [today.startOf('year'), today] },
  ];
};

/** Thanh lọc chung: khoảng ngày, nguồn dữ liệu, và hint join (Oracle) hoặc chế độ (Redis). */
export default function ReportsToolbar({ value, onChange }) {
  const set = (patch) => onChange({ ...value, ...patch });

  return (
    <div className="sf-report-toolbar" role="toolbar" aria-label="Bộ lọc báo cáo">
      <label className="sf-report-toolbar__field">
        <span className="sf-report-toolbar__label">Khoảng ngày</span>
        <DatePicker.RangePicker
          className="sf-report-toolbar__range"
          value={value.range}
          format="DD/MM/YYYY"
          allowClear={false}
          presets={presets()}
          disabledDate={(d) => d.isAfter(dayjs(), 'day')}
          onChange={(range) => range && set({ range })}
        />
      </label>
      <div className="sf-report-toolbar__field">
        <span className="sf-report-toolbar__label">Nguồn dữ liệu</span>
        <Segmented options={SOURCE_OPTIONS} value={value.source} onChange={(source) => set({ source })} />
      </div>
      {value.source === 'oracle' ? (
        <div className="sf-report-toolbar__field">
          <span className="sf-report-toolbar__label">
            Hint join{' '}
            <Tooltip title="Chỉ áp dụng cho báo cáo có phép join: theo danh mục, top sản phẩm, khách VIP.">
              <InfoCircleOutlined />
            </Tooltip>
          </span>
          <Select
            className="sf-report-toolbar__select"
            value={value.joinHint}
            onChange={(joinHint) => set({ joinHint })}
            options={JOIN_HINTS.map((h) => ({ value: h, label: JOIN_HINT_LABEL[h] ?? h }))}
            popupMatchSelectWidth={false}
          />
        </div>
      ) : (
        <div className="sf-report-toolbar__field">
          <span className="sf-report-toolbar__label">
            Chế độ Redis{' '}
            <Tooltip title="AGGREGATE: Redis tự gom nhóm. Tính ở Java: kéo dữ liệu về rồi tự tính, nhiều round-trip hơn.">
              <InfoCircleOutlined />
            </Tooltip>
          </span>
          <Segmented
            value={value.redisMode}
            onChange={(redisMode) => set({ redisMode })}
            options={REDIS_MODES.map((m) => ({ value: m, label: REDIS_MODE_LABEL[m] ?? m }))}
          />
        </div>
      )}
    </div>
  );
}
