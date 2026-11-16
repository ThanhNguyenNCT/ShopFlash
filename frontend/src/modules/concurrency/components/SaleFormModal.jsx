import { useState } from 'react';
import { Alert, DatePicker, Form, Input, InputNumber, Modal } from 'antd';
import dayjs from 'dayjs';
import { tokens } from '@/shared/design-system';
import { createSale, updateSale } from '../api';
import { maxBytesRule } from '../format';

const FORMAT = 'DD/MM/YYYY HH:mm';

// Giờ mặc định cho chương trình mới: phút tròn 5 kế tiếp sau 1 giờ nữa, kéo dài 1 giờ.
function defaultStart() {
  const t = dayjs().add(1, 'hour').second(0).millisecond(0);
  return t.minute(Math.ceil(t.minute() / 5) * 5);
}

/**
 * Tạo (sale = null) hoặc sửa chương trình. DRAFT sửa được hết; SCHEDULED/ACTIVE chỉ sửa tên và mô tả, còn giờ và
 * giới hạn gửi lại đúng giá trị cũ (backend từ chối nếu khác).
 */
export default function SaleFormModal({ sale, onCancel, onSaved }) {
  const [form] = Form.useForm();
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState(null);
  const editing = Boolean(sale);
  const locked = editing && sale.status !== 'DRAFT';

  const initialValues = editing
    ? {
      name: sale.name,
      description: sale.description,
      startAt: dayjs(sale.startAt),
      endAt: dayjs(sale.endAt),
      maxQuantityPerUser: sale.config.maxQuantityPerUser,
      reservationTimeoutSeconds: sale.config.reservationTimeoutSeconds,
    }
    : {
      startAt: defaultStart(),
      endAt: defaultStart().add(1, 'hour'),
      maxQuantityPerUser: 1,
      reservationTimeoutSeconds: 60,
    };

  const submit = async () => {
    let values;
    try {
      values = await form.validateFields();
    } catch {
      return; // lỗi đã hiện dưới từng ô
    }
    const body = {
      version: sale?.version,
      name: values.name.trim(),
      description: values.description?.trim() || null,
      startAt: locked ? sale.startAt : values.startAt.second(0).millisecond(0).format(),
      endAt: locked ? sale.endAt : values.endAt.second(0).millisecond(0).format(),
      config: {
        maxQuantityPerUser: locked ? sale.config.maxQuantityPerUser : values.maxQuantityPerUser,
        reservationTimeoutSeconds: locked ? sale.config.reservationTimeoutSeconds : values.reservationTimeoutSeconds,
        allowCancel: false,
      },
    };
    setSaving(true);
    setError(null);
    try {
      const saved = editing ? await updateSale(sale.flashSaleId, body) : await createSale(body);
      onSaved(saved, editing ? 'Đã lưu thay đổi.' : 'Đã tạo chương trình nháp. Thêm sản phẩm rồi lên lịch.');
    } catch (problem) {
      setError(problem.code === 'FLASH_SALE_CHANGED'
        ? 'Chương trình vừa được người khác sửa. Đóng hộp thoại để tải lại rồi sửa lại.'
        : problem.message ?? 'Không lưu được chương trình.');
    } finally {
      setSaving(false);
    }
  };

  return (
    <Modal open title={editing ? `Sửa chương trình #${sale.flashSaleId}` : 'Tạo chương trình flash sale'}
      okText={editing ? 'Lưu thay đổi' : 'Tạo chương trình'} cancelText="Hủy" confirmLoading={saving}
      onOk={submit} onCancel={onCancel} width="min(640px, calc(100vw - 32px))" destroyOnHidden>
      <Form form={form} layout="vertical" initialValues={initialValues} requiredMark="optional">
        {locked && (
          <Alert type="info" showIcon style={{ marginBottom: tokens.space[4] }}
            title="Chương trình đã lên lịch chỉ sửa được tên và mô tả"
            description="Muốn đổi giờ hoặc giới hạn: hủy lịch về Nháp (còn ít nhất 60 giây trước giờ mở bán) rồi sửa." />
        )}
        <Form.Item name="name" label="Tên chương trình"
          rules={[{ required: true, whitespace: true, message: 'Nhập tên chương trình.' },
            maxBytesRule(200, 'Tên quá dài, hãy rút gọn.')]}>
          <Input maxLength={200} />
        </Form.Item>
        <Form.Item name="description" label="Mô tả" rules={[maxBytesRule(1000, 'Mô tả quá dài, hãy rút gọn.')]}>
          <Input.TextArea maxLength={1000} autoSize={{ minRows: 2, maxRows: 5 }} />
        </Form.Item>
        <Form.Item name="startAt" label="Bắt đầu"
          rules={[{ required: true, message: 'Chọn giờ bắt đầu.' },
            ...(locked ? [] : [{
              validator: (_, v) => (!v || v.isAfter(dayjs())
                ? Promise.resolve() : Promise.reject(new Error('Giờ bắt đầu phải sau thời điểm hiện tại.'))),
            }])]}>
          <DatePicker showTime={{ format: 'HH:mm' }} format={FORMAT} disabled={locked} inputReadOnly
            className="sf-cc-field-full" disabledDate={(d) => d.isBefore(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="endAt" label="Kết thúc" dependencies={['startAt']}
          rules={[{ required: true, message: 'Chọn giờ kết thúc.' },
            ({ getFieldValue }) => ({
              validator: (_, v) => {
                const start = getFieldValue('startAt');
                return !v || !start || v.isAfter(start)
                  ? Promise.resolve() : Promise.reject(new Error('Giờ kết thúc phải sau giờ bắt đầu.'));
              },
            })]}>
          <DatePicker showTime={{ format: 'HH:mm' }} format={FORMAT} disabled={locked} inputReadOnly
            className="sf-cc-field-full" disabledDate={(d) => d.isBefore(dayjs(), 'day')} />
        </Form.Item>
        <Form.Item name="maxQuantityPerUser" label="Mỗi khách mua tối đa (sản phẩm cho mỗi mặt hàng)"
          rules={[{ required: true, message: 'Nhập giới hạn mỗi khách.' }]}>
          <InputNumber min={1} max={10} precision={0} disabled={locked} className="sf-cc-field-full" />
        </Form.Item>
        <Form.Item name="reservationTimeoutSeconds" label="Thời gian giữ suất (giây)"
          extra="Khách phải đặt đơn trong khoảng này, quá hạn thì suất được trả lại. Từ 30 đến 1800 giây."
          rules={[{ required: true, message: 'Nhập thời gian giữ suất.' }]}>
          <InputNumber min={30} max={1800} precision={0} disabled={locked} className="sf-cc-field-full" />
        </Form.Item>
        {error && <Alert type="error" showIcon title={error} />}
      </Form>
    </Modal>
  );
}
