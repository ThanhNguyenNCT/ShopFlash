# Manifest fixture p1trial01

Nguồn: `p1trial01-oracle-setup.txt`, `p1trial01-oracle-rerun.txt`, `p1trial01-oracle-snapshot.txt`.

| Đối tượng | ID/giá trị |
|---|---|
| run ID | `p1trial01` |
| Oracle user | `10526` |
| Oracle category | `10528` |
| Oracle cart | `10518` |
| Oracle product 1 | `12935` |
| Oracle product 2 | `12936` |
| Oracle product 3 | `12937` |
| Oracle order/order items | Chưa tạo |
| Redis cart | `txresearch:p1trial01:cart` |
| Redis inventory 1 | `txresearch:p1trial01:inventory:12935` |
| Redis inventory 2 | `txresearch:p1trial01:inventory:12936` |
| Redis inventory 3 | `txresearch:p1trial01:inventory:12937` |
| Redis quota | `txresearch:p1trial01:quota` |
| Redis order | Chưa tạo; key dự kiến `txresearch:p1trial01:order` |

Fixture này chỉ dùng thử setup/reset/cleanup P1. Cleanup phải đối chiếu từng ID với manifest trước khi `COMMIT`.
