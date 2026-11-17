import { useState } from 'react';
import { PictureOutlined } from '@ant-design/icons';
import { tokens } from '@/shared/design-system';

export default function ProductThumbnail({ thumbnailUrl, name }) {
  const [failedUrl, setFailedUrl] = useState(null);
  const hasImage = thumbnailUrl && thumbnailUrl !== failedUrl;

  return (
    <div className="sf-tx-product-thumb">
      {hasImage ? (
        <img src={thumbnailUrl} alt={name} loading="lazy" decoding="async"
          width={tokens.space[8] * 3} height={tokens.space[8] * 3}
          onError={() => setFailedUrl(thumbnailUrl)} />
      ) : (
        <span className="sf-tx-product-thumb__fallback" role="img"
          aria-label={`${thumbnailUrl ? 'Không tải được ảnh' : 'Chưa có ảnh'} sản phẩm ${name}`}>
          <PictureOutlined aria-hidden="true" />
          <span>{thumbnailUrl ? 'Lỗi ảnh' : 'Chưa có ảnh'}</span>
        </span>
      )}
    </div>
  );
}
