import { useCallback, useEffect, useRef, useState } from 'react';
import { Alert, App, Button, Card, Form, Input, Popconfirm, Result, Spin, Typography } from 'antd';
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { FlashCountdown, PriceTag, formatVND, tokens } from '@/shared/design-system';
import { cancelReservation, checkoutReservation, getReservation } from '../api';
import { maxBytesRule, nextOffset, offsetFrom, toClientTime, useServerNow } from '../format';
import '../concurrency.css';

// F9 Đặt đơn flash sale (TV5): đếm ngược tới expiresAt (theo giờ server), nhập thông tin nhận hàng, đặt đơn.
// Gửi lại cùng reservationId là an toàn: backend trả lại đơn đã tạo, không tạo đơn trùng.

const BACK = <Link to="/flash-sale">Quay lại trang flash sale</Link>;

function Spinner({ text }) {
  return <Spin description={text}><div style={{ minHeight: tokens.space[8] * 4 }} /></Spin>;
}

function ReservationLine({ reservation }) {
  return (
    <div className="sf-cc-line">
      <div className="sf-cc-line__thumb">
        {reservation.thumbnailUrl ? <img src={reservation.thumbnailUrl} alt="" /> : 'Chưa có ảnh'}
      </div>
      <div className="sf-cc-line__body">
        <strong>{reservation.productName}</strong>
        <span className="sf-cc-muted">{reservation.quantity} × {formatVND(reservation.salePrice)}</span>
      </div>
    </div>
  );
}

export default function FlashCheckoutPage() {
  const { reservationId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  const { message } = App.useApp();
  const { user, loading: authLoading } = useAuth();
  const [form] = Form.useForm();
  const passed = location.state?.reservation?.reservationId === reservationId ? location.state.reservation : null;
  const [reservation, setReservation] = useState(passed);
  // Reservation truyền qua state (từ trang flash sale) chỉ để hiện ngay; sau đó vẫn đọc lại từ server.
  const [offset, setOffset] = useState(() => (passed ? offsetFrom(passed.serverTime) : 0));
  const [loadError, setLoadError] = useState(null);
  const [order, setOrder] = useState(null);
  const [outcome, setOutcome] = useState(null); // lỗi đặt đơn: { type, title, description, final }
  const [submitting, setSubmitting] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const busyRef = useRef(false);
  const now = useServerNow(offset);
  const isCustomer = user?.role === 'CUSTOMER';

  const load = useCallback(async () => {
    setLoadError(null);
    try {
      const data = await getReservation(reservationId);
      setOffset((current) => nextOffset(current, data.serverTime));
      setReservation(data);
    } catch (problem) {
      setLoadError(problem);
    }
  }, [reservationId]);

  useEffect(() => {
    if (!authLoading && isCustomer) load();
  }, [authLoading, isCustomer, load]);

  const submit = async (values) => {
    if (busyRef.current) return;
    busyRef.current = true;
    setSubmitting(true);
    setOutcome(null);
    try {
      const result = await checkoutReservation(reservationId, {
        recipientName: values.recipientName.trim(),
        recipientPhone: values.recipientPhone.trim(),
        shippingAddress: values.shippingAddress.trim(),
      });
      setOrder(result.order);
    } catch (problem) {
      switch (problem.code) {
        case 'RESERVATION_EXPIRED':
          setOutcome({ type: 'error', final: true, title: 'Suất giữ đã hết hạn',
            description: 'Suất đã được trả lại cho người khác. Quay lại trang flash sale để giữ suất mới nếu còn.' });
          break;
        case 'SOLD_OUT':
          setOutcome({ type: 'error', final: true, title: 'Không đặt được đơn: sản phẩm vừa hết hàng',
            description: 'Đơn chưa được tạo và suất của bạn đã được trả lại.' });
          break;
        case 'CHECKOUT_BUSY':
          setOutcome({ type: 'error', final: true, title: 'Hệ thống đang quá tải, chưa đặt được đơn',
            description: 'Đơn chưa được tạo và suất của bạn đã được trả lại. Quay lại trang flash sale để giữ suất lại.' });
          break;
        case 'RESERVATION_NOT_FOUND':
          setOutcome({ type: 'error', final: true, title: 'Không tìm thấy suất đã giữ',
            description: 'Suất này không còn, hoặc thuộc tài khoản khác.' });
          break;
        case 'RESERVATION_ALREADY_USED':
          setOutcome({ type: 'warning', title: 'Suất này đang được đặt đơn hoặc đã đặt đơn',
            description: 'Có thể bạn đã gửi ở một tab khác. Kiểm tra trang Đơn hàng, hoặc đợi vài giây rồi bấm "Đặt đơn" lại.' });
          load();
          break;
        case 'VALIDATION_ERROR':
          setOutcome({ type: 'error', title: problem.message ?? 'Thông tin nhận hàng không hợp lệ',
            description: 'Kiểm tra lại họ tên, số điện thoại và địa chỉ.' });
          break;
        case 'AUTH_REQUIRED':
          setOutcome({ type: 'error', title: 'Phiên đăng nhập đã hết hạn',
            description: 'Đăng nhập lại rồi quay lại trang này trước khi suất hết hạn.' });
          break;
        default:
          // Mạng lỗi, hết giờ chờ, lỗi máy chủ: không biết đơn đã tạo hay chưa. Gửi lại cùng suất là an toàn.
          setOutcome({ type: 'warning', title: 'Chưa rõ đơn đã được tạo hay chưa',
            description: 'Bấm "Đặt đơn" lần nữa để kiểm tra. Hệ thống không tạo hai đơn cho cùng một suất.' });
      }
    } finally {
      busyRef.current = false;
      setSubmitting(false);
    }
  };

  const cancel = async () => {
    setCancelling(true);
    try {
      await cancelReservation(reservationId);
      message.success('Đã hủy giữ suất. Suất được trả lại cho người khác.');
      navigate('/flash-sale');
    } catch (problem) {
      setOutcome({ type: 'error', title: problem.message ?? 'Không hủy được giữ suất' });
      load();
    } finally {
      setCancelling(false);
    }
  };

  if (authLoading) return <Spinner text="Đang kiểm tra phiên đăng nhập" />;

  if (!isCustomer) {
    const next = encodeURIComponent(location.pathname);
    return (
      <div className="sf-cc-page">
        <Alert type="info" showIcon title="Cần tài khoản Khách"
          description={<Link to={`/login?next=${next}`}>Đăng nhập bằng tài khoản Khách để đặt đơn flash sale</Link>} />
      </div>
    );
  }

  if (order) {
    return (
      <Result status="success" title="Đặt đơn thành công"
        subTitle={<>Mã đơn <span className="sf-code">{order.orderCode}</span> · Tổng tiền {formatVND(order.totalAmount)}</>}
        extra={[
          <Button type="primary" key="order" onClick={() => navigate(`/orders/${order.orderId}`)}>Xem đơn hàng</Button>,
          <Button key="back" onClick={() => navigate('/flash-sale')}>Tiếp tục xem flash sale</Button>,
        ]} />
    );
  }

  if (outcome?.final) {
    return <Result status="warning" title={outcome.title} subTitle={outcome.description} extra={BACK} />;
  }

  if (loadError) {
    if (loadError.code === 'RESERVATION_NOT_FOUND') {
      return <Result status="404" title="Không tìm thấy suất đã giữ"
        subTitle="Suất này không còn, hoặc thuộc tài khoản khác." extra={BACK} />;
    }
    return (
      <div className="sf-cc-page">
        <Alert type="error" showIcon title="Không tải được suất đã giữ" description={loadError.message}
          action={<Button onClick={load}>Thử lại</Button>} />
      </div>
    );
  }

  if (!reservation) return <Spinner text="Đang tải suất đã giữ" />;

  const expiresAt = Date.parse(reservation.expiresAt);
  const expired = reservation.status === 'PENDING' && now >= expiresAt;

  if (reservation.status === 'CONFIRMED') {
    return <Result status="success" title="Suất này đã được đặt đơn"
      subTitle="Xem đơn trong trang Đơn hàng." extra={<Link to="/orders">Đến trang Đơn hàng</Link>} />;
  }
  if (reservation.status === 'RELEASED' || expired) {
    return <Result status="warning" title="Suất giữ đã hết hạn hoặc đã hủy"
      subTitle="Suất đã được trả lại cho người khác. Quay lại trang flash sale để giữ suất mới nếu còn." extra={BACK} />;
  }

  const claimed = reservation.status === 'CLAIMED';
  return (
    <div className="sf-cc-page">
      <header className="sf-cc-page__heading">
        <Typography.Title level={1}>Đặt đơn flash sale</Typography.Title>
        <p className="sf-cc-muted">Suất chỉ được giữ trong thời gian đếm ngược. Đặt đơn trước khi hết giờ.</p>
      </header>
      {location.state?.resumed && !outcome && (
        <Alert type="info" showIcon title="Bạn đang giữ suất này từ trước, tiếp tục đặt đơn." />
      )}
      {claimed && (
        <Alert type="info" showIcon title="Đơn của suất này đang được xử lý"
          description="Đợi vài giây rồi kiểm tra lại, hoặc xem trang Đơn hàng."
          action={<Button onClick={load}>Kiểm tra lại</Button>} />
      )}
      {outcome && <Alert type={outcome.type} showIcon title={outcome.title} description={outcome.description} />}

      <div className="sf-cc-checkout">
        <Card title={<h2 className="sf-cc-card__heading">Suất đã giữ</h2>}>
          <div className="sf-cc-stack">
            <FlashCountdown title="GIỮ SUẤT CÒN" endsAt={toClientTime(reservation.expiresAt, offset)} />
            <ReservationLine reservation={reservation} />
            <div className="sf-cc-total"><strong>Tổng tiền</strong><PriceTag price={reservation.totalAmount} /></div>
            <Popconfirm title="Hủy giữ suất?" description="Suất sẽ được trả lại cho người khác."
              okText="Hủy giữ suất" cancelText="Không" okButtonProps={{ danger: true }} onConfirm={cancel}
              disabled={claimed || submitting}>
              <Button danger loading={cancelling} disabled={claimed || submitting}>Hủy giữ suất</Button>
            </Popconfirm>
          </div>
        </Card>

        <Card title={<h2 className="sf-cc-card__heading">Thông tin nhận hàng</h2>}>
          <Form form={form} layout="vertical" requiredMark={false} scrollToFirstError={{ focus: true }}
            onFinish={submit} initialValues={{ recipientName: user.fullName }}>
            <Form.Item name="recipientName" label="Họ tên người nhận"
              rules={[{ required: true, whitespace: true, message: 'Nhập họ tên người nhận.' },
                maxBytesRule(150, 'Họ tên quá dài, hãy rút gọn.')]}>
              <Input maxLength={150} autoComplete="name" />
            </Form.Item>
            <Form.Item name="recipientPhone" label="Số điện thoại"
              rules={[{ required: true, whitespace: true, message: 'Nhập số điện thoại.' },
                maxBytesRule(30, 'Số điện thoại quá dài.')]}>
              <Input maxLength={30} autoComplete="tel" inputMode="tel" />
            </Form.Item>
            <Form.Item name="shippingAddress" label="Địa chỉ giao hàng"
              rules={[{ required: true, whitespace: true, message: 'Nhập địa chỉ giao hàng.' },
                maxBytesRule(500, 'Địa chỉ quá dài, hãy rút gọn.')]}>
              <Input.TextArea maxLength={500} rows={3} autoComplete="street-address" />
            </Form.Item>
            <Button type="primary" htmlType="submit" loading={submitting} block>Đặt đơn</Button>
            <p className="sf-cc-muted" style={{ marginTop: tokens.space[2] }}>
              Đơn flash sale không hủy được sau khi đặt.
            </p>
          </Form>
        </Card>
      </div>
    </div>
  );
}
