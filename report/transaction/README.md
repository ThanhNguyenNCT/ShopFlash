# Transaction: thí nghiệm và benchmark

## Markdown hiện hành — Mục 2.4

Cấu trúc chung: [Report/REPORT.md](../../Report/REPORT.md).
Bản dùng để ghép: **[2.4. Transaction](../../Report/Sections/Tasks/Transaction/noi-dung.md#transaction-2-4)**,
gồm 2.4.1 Tổng quan/phương pháp, 2.4.2 Lý thuyết và thực nghiệm ACID,
2.4.3 Hiệu năng và lựa chọn DBMS.

Mục 2.4.1 hiện lấy từ file 3.1 trong bản đầy đủ
`local_docs/Bao_cao_Transaction_Muc_3_1-3_3_MD/`; mục 2.4.2 dùng bản 3.2 cập nhật
trong `local_docs/Bao_cao_Transaction_3_1-3_3_Rut_gon/`; mục 2.4.3 giữ nội dung
Markdown rút gọn local đã chọn trước đó, nối số bảng thành 2.4.17–2.4.22;
riêng mục 2.4.3.4 đã thay bằng nội dung 3.3.4 mới trong cùng nguồn rút gọn.
Chỉ đổi heading, số caption/tham chiếu
và liên kết khi nhập nội dung. Giữ nguyên code,
log, các ô bảng số liệu, kết luận và ba biểu đồ vector đã có. Bảng, hình, mã và
kết quả hiện mang tiền tố `2.4`; tên file artifact giữ nguyên để tránh gãy liên kết.

`REPORT.md` và `SECTION_TRANSACTION.md` trong thư mục này là các bản trước,
có cấu trúc dài hơn/khác; giữ để đối chiếu và đọc minh chứng, không ghép thêm
vào mục 2.4. Các thư mục `Bao_cao*/` vẫn chỉ để local, không là dependency của
bản Markdown hiện hành.

## Bản LaTeX/PDF trước đó — Mục 4

Các số 4.x và thông tin kiểm thử bố cục dưới đây mô tả bản xuất trước.
Báo cáo hiện hành ở [Report/main.tex](../../Report/main.tex) đã dùng ba phần,
có Phần 1, Transaction 2.4 và Phần 3; PDF là `Report/build/main.pdf`. Các hướng dẫn
`report/main.tex` ở phần lịch sử dưới đây không phải bộ báo cáo hiện hành.

`report/main.tex` ghép `transaction/noi-dung.tex` thành một mục riêng
**4. Transaction** trong sáu chủ đề của đề bài, gồm ba phần liên tiếp:

| Mục trong PDF | File LaTeX | Markdown nguồn trong `../../local_docs/Bao_cao_Transaction_3_1-3_3_Rut_gon/` ở gốc repo |
|---|---|---|
| 4.1 Tổng quan và phương pháp nghiên cứu | `sections/01-tong-quan.tex` | `3.1_Tong_quan_va_phuong_phap_nghien_cuu.md` |
| 4.2 Cơ sở lý thuyết và thực nghiệm ACID | `sections/02-thuc-nghiem-acid.tex` | `3.2_Co_so_ly_thuyet_va_thuc_nghiem_ACID.md` |
| 4.3 Đánh giá hiệu năng và lựa chọn DBMS | `sections/03-hieu-nang.tex` | `3.3_Danh_gia_hieu_nang_va_lua_chon_DBMS (1).md` |

Nguồn hiện tại là bản rút gọn ở **gốc repo**. File 3.3 có hậu tố `(1)` là bản
cập nhật mới nhất được dùng, không lấy bản dài hơn trong
`report/Bao_cao_Transaction_3_1-3_3_Rut_gon/` hay bộ Markdown cũ.
Các thư mục `Bao_cao*/` chỉ lưu trên máy local, được gitignore và không commit.
Bản báo cáo chia sẻ trong Git là các file LaTeX ở `sections/` và hình ở `images/`.

Markdown gốc được giữ nguyên. Số mục, bảng, hình, mã nguồn và kết quả trong
bản LaTeX được đánh tự động theo mục 4; các dẫn chiếu dùng `\label`/`\ref`.
Sáu mục lớn là Storage, Indexing, Query processing, Transaction, Concurrency
control và Backup/recovery. Các mục giới thiệu/kiến trúc/đánh giá/kết luận chung
và phụ lục cũ không còn ghép vào PDF. Nội dung chủ đề khác giữ riêng theo phân công.

Ba ghi chú `[Hình 3.x — ...]` trong Markdown được thay bằng biểu đồ thực tế.
Ba hình vector trong `images/4.3-0*.pdf` được xuất từ SVG ở
`report_assets/transaction/`. Chỉ bỏ số hình viết sẵn trong tiêu đề biểu đồ,
để caption LaTeX cung cấp số 4.1–4.3; dữ liệu và các thành phần biểu đồ được giữ nguyên.
`format.tex` khai báo kiểu kết quả thực nghiệm và syntax highlighting của Lua/JavaScript.
Trong khối mã, ký tự tiếng Việt được bọc bằng escape của `listings` để giữ đúng
thứ tự chữ có dấu khi dùng XeLaTeX; bỏ các delimiter này sẽ thu lại nguyên văn mã nguồn.
Bảng dùng lưới đầy đủ và hàng tiêu đề xanh nhạt. Log dùng nền xám xanh, giãn
dòng đơn và bỏ dòng rỗng chỉ để giãn cách; các dòng kết quả giữ nguyên thứ tự.
Code/log dài được phép ngắt qua trang để giảm khoảng trắng cuối trang.

Biên dịch bằng TeX cài sẵn, từ thư mục `report/`:

```bash
latexmk -outdir=build main.tex
```

Hoặc từ gốc repo, dùng image Docker của dự án:

```bash
bash report/build.sh
```

PDF chung nằm tại `report/build/main.pdf`. Build PDF chỉ cần các file LaTeX và
hình đã có, không cần Python, Markdown local hay chạy lại benchmark. Trên máy
có bộ Markdown local, nếu sửa nguồn thì tạo lại ba file LaTeX từ gốc repo bằng:

```bash
python3 report/transaction/import_markdown.py
```

Lệnh chuyển đổi cần `markdown-it-py` (`python3-markdown-it` trên Ubuntu).
Thêm `--figures` khi cần xuất lại hình vector; tùy chọn này dùng PyGObject/Rsvg
và Pycairo (`python3-gi`, `gir1.2-rsvg-2.0`, `python3-cairo`).

Kiểm thử bố cục sáu chủ đề ngày 16/11/2026: XeLaTeX + biber qua image
`shopflash-latex:1` xuất PDF 46 trang (bản ghép sau rebase trước đó: 63 trang).
Riêng Transaction giữ đủ 14 tiêu đề, 15 bảng, 24 đoạn mã, 19 khối kết quả và
3 hình; mã nguồn giữ nguyên, log chỉ bỏ dòng rỗng để giãn cách. Toàn bộ PDF có
20 bảng (thêm 5 bảng Concurrency), đều có lưới và hàng tiêu đề xanh nhạt.
Đã kiểm tra trực quan mục lục, bảng, mã nguồn, WATCH log và các biểu đồ;
không có tràn nội dung, thiếu glyph hoặc tham chiếu chưa xác định. Các mục
chưa có file LaTeX chỉ giữ tiêu đề; thông tin cá nhân chưa được cung cấp trên
trang bìa vẫn để trống.

## Tài liệu nghiên cứu và benchmark

Thư mục này chứa thí nghiệm tính đúng đắn, phép đo cơ chế database và phép đo HTTP của F10 → F7 → F8. Oracle là nguồn dữ liệu nghiệp vụ của ShopFlash. Redis trong phép đo database chỉ là mô phỏng cùng thao tác logic; không thay thế giao dịch đặt hàng thật.

Để đọc theo đúng mục đích, bắt đầu từ [runbook nghiên cứu DBMS-native](research/README.md) và [chỉ mục 14 thí nghiệm](research/evidence/INDEX.md). [SECTION_TRANSACTION.md](SECTION_TRANSACTION.md) là phần diễn giải để đưa vào báo cáo. Các mục Java/JUnit/k6 bên dưới là baseline kiểm thử và benchmark cũ, được giữ riêng với bằng chứng SQL/PLSQL và Redis CLI/Lua.

B2/B4/C2 đã được chỉnh theo [OpenSpec change](../../local_docs/openspec/changes/revise-transaction-b2-b4-c2/proposal.md) và [evidence mới](research/evidence/revision-20261109/README.md). OpenSpec dùng convention hiện có trong `local_docs/openspec` (thư mục local bị gitignore); bản chụp đầy đủ artifact được lưu kèm [evidence](research/evidence/revision-20261109/openspec-plan/proposal.md) để bàn giao.

## 1. Môi trường

- Oracle Database Free và Redis Stack chạy bằng `docker compose` trên `127.0.0.1` theo [`compose.yaml`](../../compose.yaml). Redis bật AOF (`--appendonly yes`). Ghi lại digest image, phiên bản Docker, CPU/RAM và tải nền trong lần chạy báo cáo.
- Backend Spring Boot và frontend Vite chạy trên host. Vite chuyển `/api` tới backend `127.0.0.1:8080`; không cần mở CORS. Backend đọc `../.env` từ thư mục `backend`; cần `ORACLE_*`, `REDIS_*` và `JWT_SECRET_BASE64` đủ mạnh. Không ghi giá trị secret vào báo cáo.
- Java 21, Maven Wrapper, Python 3 và k6 CLI v0.55.2 trong image Docker ghim digest. Chạy các lệnh sau trước khi đo:

```bash
docker compose up -d --wait
cd backend && ./mvnw -version && cd ..
docker pull grafana/k6:0.55.2
docker run --rm grafana/k6@sha256:3cd0ae83b47a29b273193eb998740c3b0e219fc34cdf579b90a8fc1698f5c025 version
```

Trên hai terminal riêng, khởi chạy host nếu chưa có tiến trình tương ứng:

```bash
cd backend && ./mvnw spring-boot:run
```

```bash
cd frontend && npm run dev
```

Cho backend khởi động thành công ít nhất một lần để Flyway áp dụng V1/V2 trước khi gọi các lớp `*IT` (các lớp này tắt Flyway để không đưa migration vào thời gian đo). Profile `dev` chỉ cần khi xem dữ liệu mẫu trên giao diện; thí nghiệm tạo fixture `txlab` riêng, không phụ thuộc seed dev. Không chạy đồng thời hai lần benchmark trên một schema vì chúng sẽ tranh tài nguyên và làm sai số đo.

## 2. Kiểm chứng cơ chế trước khi đo tốc độ

Nghiên cứu trực tiếp trên DBMS đã chạy 14 case bằng SQL/PLSQL và Redis CLI/Lua; mỗi case có lệnh, output và lời giải thích trong [chỉ mục](research/evidence/INDEX.md). Phần JUnit dưới đây là bộ kiểm thử hiện có của ứng dụng, giúp đối chiếu hành vi nhưng không thay cho transcript DBMS-native.

JUnit 5 dùng Oracle/Redis thật; không có `@Transactional` bọc toàn test. Oracle được đọc lại từ kết nối độc lập sau `COMMIT`/`ROLLBACK`, Redis từ client riêng. Bảng chứng cứ gồm trạng thái trước, mong đợi, thực tế và lệnh kiểm chứng.

```bash
cd backend
./mvnw test -Dtest='TransactionSemanticsIT,RedisTransactionIT'
cd ..
```

Đầu ra: `backend/target/transaction-evidence/oracle.csv` và `redis.csv`. Các case Oracle gồm commit, rollback sau khi ghi đơn/dòng/tồn/giỏ, `READ COMMITTED`, lock timeout của giỏ/tồn kho và deadlock có `CyclicBarrier`. Các case Redis gồm `MULTI/EXEC`, `DISCARD`, lỗi runtime lúc `EXEC`, xung đột `WATCH` có `CountDownLatch`, và Lua lỗi sau ghi. `DISCARD` bỏ lệnh chưa thực thi; lỗi ở `EXEC` hoặc Lua không tự hoàn tác ghi đã chạy.

## 3. Database-level: Java runner

Runner là lớp JUnit gọi đích danh, dùng Oracle JDBC và Jedis đã có trong project, đo bằng `System.nanoTime()`; không thêm framework benchmark. Mỗi yêu cầu có fixture riêng: một khách, một giỏ, năm sản phẩm và tồn đầu kỳ 100/sản phẩm. Hai bên dùng giỏ 1 hoặc 5 sản phẩm, số lượng 1, đơn giá 100. Từng thao tác đọc/kiểm tra giỏ và tồn, ghi đầu đơn và dòng, giảm tồn rồi dọn giỏ. Oracle dùng bảng/ràng buộc SQL và `COMMIT`; Redis dùng `WATCH` + `MULTI/EXEC`, các key `txlab:*` và kiểm tra được viết trong Java.

```bash
cd backend
./mvnw test -Dtest='TransactionDbBenchmarkIT'
cd ..
```

Mặc định runner chạy từng tổ hợp **1/5 sản phẩm × đồng thời 1/10**, 50 thao tác làm nóng, sau đó **5 lượt × 200 thao tác/lượt**. Dữ liệu fixture được đặt lại trước warm-up và từng lượt; 200 yêu cầu có giỏ/sản phẩm tách biệt. Khoảng đo latency bắt đầu khi đọc/khóa dữ liệu nghiệp vụ và kết thúc khi nhận kết quả `COMMIT`/`EXEC`; không gồm reset, nhưng gồm lệnh SQL/Redis cần cho giao dịch. Thông lượng chia số thành công cho thời gian thực từ lúc thả các worker đến khi toàn lượt đã trả kết quả và hậu kiểm xong. Cấu hình được lưu trong `db-*-config.json`; kết quả từng mẫu ở `db-*.csv` và `db-*.jsonl`, thời gian thực từng lượt ở `db-*-runs.jsonl`.

Chỉ giảm số mẫu cho lượt thử công cụ, kèm lý do công khai:

```bash
cd backend
./mvnw test -Dtest='TransactionDbBenchmarkIT' -Dtx.bench.warmup=2 -Dtx.bench.rounds=1 -Dtx.bench.samples=10 -Dtx.bench.override.reason=smoke
cd ..
```

Số đo Oracle–Redis không chuẩn hóa hoàn toàn: Oracle kiểm tra khóa ngoại, CHECK, unique, lưu redo và cập nhật nhiều bảng trong giao dịch; Redis dùng key độc lập, điều kiện ứng dụng/`WATCH` và AOF của cấu hình này. Số lệnh và chi phí client/network khác nhau; số liệu Redis không phải hiệu năng đặt đơn của ứng dụng ShopFlash.

## 4. Application-level: k6 qua Vite proxy

Script [`k6/cart-checkout-order.js`](k6/cart-checkout-order.js) đo riêng từng API Cart, Checkout, Order và toàn luồng. Một luồng chuẩn gồm `GET cart`, thêm 1 hoặc 5 sản phẩm, preview, tạo đơn, xem danh sách/chi tiết và hủy đơn để hoàn tồn kho. Mỗi VU dùng khách/giỏ/sản phẩm fixture riêng. Lệnh đăng nhập trong `setup()` của k6 lấy JWT trước lượt đo, có nhãn `phase=setup` và **không** đi vào metric nghiệp vụ. Không ghi JWT hoặc mật khẩu trong JSON thô; không truyền token bằng URL.

Mật khẩu fixture hiện được định nghĩa trong `TransactionFixtures` (chỉ dùng local/test). Đưa vào biến môi trường của phiên shell, tránh ghi vào script và báo cáo:

```bash
read -r -s -p 'Mật khẩu fixture: ' TX_BENCH_PASSWORD
export TX_BENCH_PASSWORD
bash report/transaction/scripts/run-http.sh
unset TX_BENCH_PASSWORD
```

Script kiểm tra k6, tạo mười khách fixture, đặt lại dữ liệu trước mỗi lượt, chạy đủ 4 tổ hợp × (50 warm-up + 5 × 200 lượt), hậu kiểm Oracle sau từng lượt bằng `TransactionHttpAuditIT`, rồi cleanup bằng trap. File `*-audit.json` ghi số đơn/dòng, trạng thái hủy, giỏ và tồn kho trước reset. Có thể thử script ngắn bằng `TX_HTTP_SMOKE=1 bash report/transaction/scripts/run-http.sh`; kết quả ghi `override_reason=smoke`. Nếu phiên bị `SIGKILL`, chạy cleanup thủ công bằng ID fixture trong `backend/target/transaction-http-fixtures.json`:

```bash
cd backend
./mvnw test -Dtest='TransactionHttpFixtureCommandIT' \
  -Dtx.fixture.action=cleanup -Dtx.fixture.ids='<10 ID phân cách bằng dấu phẩy>'
cd ..
```

Có thể gọi `-Dtx.fixture.action=setup` hoặc `reset` với cùng danh sách ID; `target/transaction-http-fixtures.json` chứa email/ID sản phẩm, không chứa mật khẩu/JWT. Mọi thao tác chỉ chạm user, giỏ, đơn, tồn của fixture `txlab-*` và khóa Redis `txlab:*`; không xóa seed nền hay `flash:*`, `product:*`, `order:analytics:*`, `order:line:*`.

JSON thô k6 được nén nguyên vẹn tại `report/transaction/results/http-<thời điểm>/cart<1|5>-c<1|10>-r<0..5>.json.gz`. Round 0 là warm-up. `tx_api_duration_ms` gắn nhãn API/kết quả, `tx_flow_duration_ms` gắn nhãn cả luồng/kết quả/số request; `http_req_duration` tổng hợp không được dùng thay metric Checkout hoặc toàn luồng.

## 5. Tổng hợp và đọc kết quả

```bash
python3 report/transaction/scripts/summarize.py \
  --db-csv report/transaction/results/db-<run_id>.csv \
  --http-dir report/transaction/results/http-<run_id> \
  --output-dir report/transaction/results/summary-<run_id>
```

Chương trình xuất CSV/JSON mẫu chuẩn hóa và hai bảng **database**/**HTTP** tách biệt, thêm `comparison.md`. p50/p95 dùng toàn bộ mẫu `success` hợp lệ của năm lượt. Thông lượng thành công = số `success` / tổng thời gian thực của các lượt (DB: transactions/s; HTTP: requests/s theo API hoặc flows/s toàn luồng). Error rate = `error` / mẫu hoàn tất; rejection rate = `rejected` / mẫu hoàn tất; `expected_failure` và `invalid` đếm riêng. File JSON có số mẫu, p50/p95, trung bình và độ lệch chuẩn giữa các lượt, cùng số vi phạm bất biến. Lượt không có mẫu thành công để trống phân vị. HTTP tính thời gian thực từ dấu thời gian đầu/cuối của metric toàn luồng, không cộng thời gian đăng nhập.

Đối chiếu correctness trước khi kết luận performance: đọc bảng chứng cứ Oracle/Redis, số thành công/từ chối/lỗi, trạng thái sau commit/rollback và hậu kiểm tồn kho/đơn. Không so trực tiếp các trị số latency/throughput của database-level với HTTP. Phần trình bày báo cáo theo [Report/AGENTS.md](../../Report/AGENTS.md): Lý thuyết → Oracle (demo và số đo) → Redis (demo và số đo) → Bảng so sánh → Kết luận theo ShopFlash.

## 6. Lần đo dùng trong báo cáo

Ngày 27/10/2026, lần chạy mặc định dùng file database `results/db-2026-10-27T00-57-08-100088724Z.csv` và thư mục HTTP `results/http-20261027-stable/`. Mỗi nhánh có đủ 5 lượt × 200 mẫu đo; HTTP còn có một lượt làm nóng cho mỗi tổ hợp. Có thể dựng lại bảng tổng hợp từ dữ liệu thô đã lưu:

```bash
python3 -B report/transaction/scripts/summarize.py \
  --db-csv report/transaction/results/db-2026-10-27T00-57-08-100088724Z.csv \
  --http-dir report/transaction/results/http-20261027-stable \
  --output-dir report/transaction/results/summary-20261027
```

Đọc [mục 2.4 hiện hành](../../Report/Sections/Tasks/Transaction/noi-dung.md), [bản tổng hợp trước đó](SECTION_TRANSACTION.md), `results/evidence/oracle.csv`, `results/evidence/redis.csv`, `results/summary-20261027/{database,http}-summary.csv`, `results/summary-20261027/comparison.md` và 24 file `*-audit.json` để kiểm tra từng kết luận. File `results/environment-20261027.json` ghi phiên bản, image digest và hash của runner/script; các mật khẩu và JWT không được đưa vào kết quả. Lần đo này ghi nhận 8.000 giao dịch database và 4.000 luồng HTTP đo chính thức thành công, 0 vi phạm bất biến. Đây là số quan sát tại local, không phải ngưỡng bảo đảm hiệu năng.
