import { useEffect, useRef, useState } from 'react';
import { splitDuration } from '../format';
import './FlashCountdown.css';

const secondsLeft = (endsAt) => Math.max(0, (new Date(endsAt).getTime() - Date.now()) / 1000);

// Dải "FLASH SALE" nền ink với đồng hồ đếm ngược. endsAt: Date | ISO string | timestamp.
export default function FlashCountdown({ endsAt, title = 'FLASH SALE', onEnd }) {
  const [left, setLeft] = useState(() => secondsLeft(endsAt));
  const onEndRef = useRef(onEnd);
  onEndRef.current = onEnd;

  useEffect(() => {
    setLeft(secondsLeft(endsAt));
    const id = setInterval(() => {
      const s = secondsLeft(endsAt);
      setLeft(s);
      if (s <= 0) {
        clearInterval(id);
        onEndRef.current?.();
      }
    }, 1000);
    return () => clearInterval(id);
  }, [endsAt]);

  // Còn từ 1 ngày trở lên thì tách ô ngày, để giờ luôn dưới 24 (tránh "9757:44:16").
  const days = Math.floor(left / 86400);
  const [hh, mm, ss] = splitDuration(left % 86400);
  return (
    <div className="sf-flash">
      <strong className="sf-flash__title">{title}</strong>
      {left > 0 ? (
        <span className="sf-flash__clock sf-num" role="timer"
          aria-label={`Còn ${days > 0 ? `${days} ngày ` : ''}${hh} giờ ${mm} phút ${ss} giây`}>
          {days > 0 && <span className="sf-flash__days">{days} ngày</span>}
          <span>{hh}</span>:<span>{mm}</span>:<span>{ss}</span>
        </span>
      ) : (
        <span className="sf-flash__ended">Đã kết thúc</span>
      )}
    </div>
  );
}
