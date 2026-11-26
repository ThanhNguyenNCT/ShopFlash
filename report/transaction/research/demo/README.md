# Transaction Demo Kit

Bộ công cụ quay video demo Transaction trên Oracle và Redis. Kit chạy lại 8 case nghiên cứu (A1, A3, A4, A5 runtime, B1, B4.2, C2, D1) trên một **môi trường Docker riêng** `shopflashdemo`, từng bước một, hiện câu lệnh và phản hồi thật của DBMS.

Quy trình quay đầy đủ: **[DEMO_RUNBOOK.md](DEMO_RUNBOOK.md)**.

## Bắt đầu nhanh

```bash
cd report/transaction/research/demo
./demo.sh init          # DB riêng (Oracle :1522, Redis :6381), Flyway migrate, fixture P1/P2/P3
./demo.sh dbeaver       # thông số kết nối DBeaver
./demo.sh prepare 1     # reset baseline + hướng dẫn scene 1
./demo.sh help          # mọi lệnh và danh sách scene
```

| Lệnh | Việc làm |
|---|---|
| `prepare <scene>` | Reset baseline của DB liên quan, render SQL từ manifest, in file cần mở và thứ tự STEP |
| `play <scene>` | Chạy từng bước, Enter = bước tiếp. Redis: cách demo chính. Oracle: dự phòng khi không dùng DBeaver |
| `lua <quantity>` | `EVAL` nguyên file `redis/lua/C2-validate.lua`, in BEFORE / COMMAND / RESPONSE / AFTER |
| `show oracle\|redis` | Trạng thái hiện tại, đọc bằng session/client độc lập |
| `reset oracle\|redis\|all` | Về baseline (từ chối nếu còn session giữ transaction) |
| `unlock` | Kill session SHOP còn transaction mở, chỉ trong container demo |
| `cleanup` | Xóa fixture theo manifest: preview → cleanup → verify |
| `selftest` | Kiểm thử cả kit trên DBMS thật |
| `reference` | Chạy lại script nghiên cứu gốc, không sửa, trên DB demo để đối chiếu |

Scene: `1` oracle-lifecycle · `2` redis-lifecycle · `3` oracle-savepoint · `4` oracle-atomicity · `5` redis-atomicity · `6` consistency (oracle-consistency + redis-lua) · `7` oracle-isolation · `8` redis-watch.

## Cấu trúc

```text
demo/
├── demo.sh            Entrypoint: môi trường Docker, xác minh target, fixture, reset, cleanup
├── DEMO_RUNBOOK.md    Quy trình quay video (tiếng Việt)
├── oracle/            Template SQL cho DBeaver, chia STEP n [A|B]; lib/ chứa SQL chỉ đọc
├── redis/             Kịch bản Redis: "A> lệnh" gửi qua một kết nối redis-cli giữ liên tục
├── lib/play.py        Render SQL, chạy scene, show, lua
├── lib/selftest.py    Kiểm thử trên DBMS thật
├── lib/bin/docker     Shim cho script nghiên cứu (xem bên dưới)
└── runtime/           Sinh khi chạy, bị gitignore: demo.env (mật khẩu), manifest, SQL đã render, transcript
```

## An toàn và tái sử dụng

- **Môi trường riêng.** Compose project `shopflashdemo` dùng chính `compose.yaml` và image đã ghim của repo, nhưng có container, network `shopflashdemo_default` và volume `shopflashdemo_*` riêng. Kit gọi Compose qua `env -i ... --env-file runtime/demo.env`, nên `.env` của project chính và biến đã export trong shell không thể đổi target.
- **Kiểm tra target trước mỗi thao tác ghi.** Qua `docker inspect`: label project, volume `shopflashdemo_*` mount đúng chỗ, không có volume `shopflash_*`, port `127.0.0.1:1522` và `127.0.0.1:6381`, network, trạng thái healthy, label của volume. Không chỉ dựa vào tên biến.
- **Script nghiên cứu được dùng nguyên văn.** `setup.sql`, `reset.sql`, `cleanup*.sql`, `verify-absent.sql`, `snapshot.sql`, `run-sql.sh`, `redis/{setup,reset,cleanup,preview,snapshot}.sh`, A4/A5/B4/C2, `two-session.py` đều gọi `docker compose exec -T oracle|redis`. Shim `lib/bin/docker` chỉ chuyển đúng lệnh đó sang container demo vừa xác minh và từ chối mọi lệnh docker khác.
- **Manifest.** `runtime/manifest.env` giữ 6 ID Oracle và 7 key Redis chính xác. Order đã COMMIT được tìm theo user của fixture rồi chuyển cho `reset.sql`/`cleanup.sql`; hai script này đối chiếu chủ sở hữu và mã `TXRESEARCH_DEMO_*` trước khi xóa. Không dùng FLUSH, không xóa theo wildcard. Nếu gặp key lạ, kit dừng lại thay vì xóa.
- **Mật khẩu.** Sinh ngẫu nhiên vào `runtime/demo.env` (chmod 600). Không xuất hiện trên dòng lệnh, trong log hay manifest. Trong container, mật khẩu được đọc từ biến môi trường của container.
- **Flyway.** `./demo.sh migrate` khởi động backend với mọi biến kết nối trỏ vào DB demo. Kit xác nhận trong log rằng Flyway dùng `localhost:1522`, chờ backend khởi động xong rồi dừng nó.
