# Kết quả Transaction đo được

Chỉ tính p50/p95 từ mẫu `success` hợp lệ; thông lượng = số `success` / tổng thời gian thực của các lượt.

## Cơ chế database: Oracle–Redis

| Kịch bản | Hệ/API | Giỏ | Đồng thời | p50 ms | p95 ms | Thành công/s | Lỗi % | Từ chối % | Vi phạm |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| oracle-cart1-c1 | oracle/checkout | 1 | 1 | 0.8712 | 1.3416 | 847.3643 | 0.0 | 0.0 | 0 |
| oracle-cart1-c10 | oracle/checkout | 1 | 10 | 2.2116 | 3.011 | 3503.1546 | 0.0 | 0.0 | 0 |
| oracle-cart5-c1 | oracle/checkout | 5 | 1 | 2.0081 | 2.6394 | 404.7405 | 0.0 | 0.0 | 0 |
| oracle-cart5-c10 | oracle/checkout | 5 | 10 | 4.5749 | 6.1164 | 1753.7399 | 0.0 | 0.0 | 0 |
| redis-cart1-c1 | redis/checkout | 1 | 1 | 0.1303 | 0.2109 | 4517.0791 | 0.0 | 0.0 | 0 |
| redis-cart1-c10 | redis/checkout | 1 | 10 | 0.5125 | 0.697 | 12288.9375 | 0.0 | 0.0 | 0 |
| redis-cart5-c1 | redis/checkout | 5 | 1 | 0.2995 | 0.459 | 1787.8341 | 0.0 | 0.0 | 0 |
| redis-cart5-c10 | redis/checkout | 5 | 10 | 0.9906 | 1.4019 | 5438.8618 | 0.0 | 0.0 | 0 |

Độ lệch chuẩn giữa các lượt, số nhóm kết quả và thời gian từng lượt nằm trong JSON/CSV cùng tên.

## Ứng dụng HTTP: từng API và toàn luồng

| Kịch bản | Hệ/API | Giỏ | Đồng thời | p50 ms | p95 ms | Thành công/s | Lỗi % | Từ chối % | Vi phạm |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|
| http-cart1-c1 | http/cart.add | 1 | 1 | 2.171 | 3.0604 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/cart.read | 1 | 1 | 1.0291 | 1.5505 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/checkout.place | 1 | 1 | 2.9084 | 4.0406 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/checkout.preview | 1 | 1 | 1.0719 | 1.6016 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/full_flow | 1 | 1 | 14.0 | 18.0 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/orders.cancel | 1 | 1 | 2.1862 | 3.0928 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/orders.detail | 1 | 1 | 1.2752 | 1.9104 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c1 | http/orders.list | 1 | 1 | 1.8844 | 2.7289 | 68.3352 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/cart.add | 1 | 10 | 5.0315 | 7.5302 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/cart.read | 1 | 10 | 2.9731 | 4.8129 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/checkout.place | 1 | 10 | 6.5273 | 8.9241 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/checkout.preview | 1 | 10 | 2.9938 | 4.727 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/full_flow | 1 | 10 | 33.0 | 42.0 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/orders.cancel | 1 | 10 | 5.3285 | 7.1802 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/orders.detail | 1 | 10 | 3.2484 | 5.271 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart1-c10 | http/orders.list | 1 | 10 | 3.7345 | 5.3287 | 296.0777 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/cart.add | 5 | 1 | 2.447 | 5.4287 | 138.6366 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/cart.read | 5 | 1 | 1.2787 | 3.3743 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/checkout.place | 5 | 1 | 4.9161 | 10.2897 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/checkout.preview | 5 | 1 | 1.3654 | 3.422 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/full_flow | 5 | 1 | 30.0 | 62.0 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/orders.cancel | 5 | 1 | 3.2553 | 7.0641 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/orders.detail | 5 | 1 | 1.548 | 3.6672 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c1 | http/orders.list | 5 | 1 | 2.4136 | 5.1005 | 27.7273 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/cart.add | 5 | 10 | 5.1143 | 6.9829 | 787.8445 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/cart.read | 5 | 10 | 3.0349 | 4.5542 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/checkout.place | 5 | 10 | 11.1444 | 14.7888 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/checkout.preview | 5 | 10 | 3.0639 | 4.5663 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/full_flow | 5 | 10 | 61.0 | 74.0 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/orders.cancel | 5 | 10 | 7.2742 | 9.6239 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/orders.detail | 5 | 10 | 3.375 | 4.8415 | 157.5689 | 0.0 | 0.0 | 0 |
| http-cart5-c10 | http/orders.list | 5 | 10 | 3.8973 | 5.4685 | 157.5689 | 0.0 | 0.0 | 0 |

Độ lệch chuẩn giữa các lượt, số nhóm kết quả và thời gian từng lượt nằm trong JSON/CSV cùng tên.

Hai bảng có ranh giới đo khác nhau; không đối chiếu trực tiếp trị số database với HTTP.
