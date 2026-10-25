import { useState } from 'react';
import { Alert, Button, Flex } from 'antd';
import { Link } from 'react-router-dom';
import { useAuth } from '@/shared/auth';
import { tokens } from '@/shared/design-system';
import { addProductToCart, getCart } from '../api';

/** Nút TV2 có thể gắn vào trang sản phẩm thật; chỉ nhận productId Oracle. */
export default function AddToCartButton({ productId, quantity = 1, onAdded }) {
  const { user } = useAuth();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const [added, setAdded] = useState(false);

  const add = async () => {
    setBusy(true);
    setError('');
    setAdded(false);
    try {
      const cart = await addProductToCart(user, productId, quantity);
      setAdded(true);
      onAdded?.(cart);
    } catch (problem) {
      if (['CART_CHANGED', 'LOCK_TIMEOUT', 'DEADLOCK', 'TIMEOUT', 'NETWORK_ERROR'].includes(problem.code)) {
        try { await getCart(user); } catch { /* vẫn báo trạng thái chưa rõ */ }
        setError('Giỏ có thể đã đổi. Hãy mở giỏ để kiểm tra trước khi thêm lại.');
      } else {
        setError(problem.message ?? 'Không thêm được sản phẩm vào giỏ.');
      }
    } finally {
      setBusy(false);
    }
  };

  return (
    <Flex vertical gap={tokens.space[2]}>
      <Button onClick={add} loading={busy} disabled={!Number.isInteger(Number(productId)) || Number(productId) <= 0}>
        Thêm vào giỏ
      </Button>
      {added && <Alert type="success" showIcon title={<>Đã thêm vào giỏ. <Link to="/cart">Xem giỏ</Link></>} />}
      {error && <Alert type="error" showIcon title={error} />}
    </Flex>
  );
}
