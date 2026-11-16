# Khung viết phần chủ đề

Mỗi người viết một tiểu mục 3.x của báo cáo cho chủ đề mình phụ trách. Demo gì, đo gì, dùng công cụ nào là do người làm tự chọn. Khung này chỉ quy định **những phần phải có** và **cách ghi số liệu**, để lúc ghép báo cáo và lúc so sánh ở mục 5 các phần đặt cạnh nhau được.

| Tiểu mục | Chủ đề | Thư mục |
|---|---|---|
| 3.1 | Data storage & management | `report/storage/` |
| 3.2 | Indexing | `report/indexing/` |
| 3.3 | Query processing | `report/query/` |
| 3.4 | Transaction | `report/transaction/` |
| 3.5 | Concurrency control | `report/concurrency/` |
| 3.6 | Data backup and recovery | `report/backup/` |

## 1. Năm phần bắt buộc

| Phần | Nội dung | Gợi ý độ dài |
|---|---|---|
| **3.x.1 Lý thuyết** | Khái niệm của chủ đề theo môn học. Chỉ nêu những gì cần để hiểu các demo phía sau | 0,5–1 trang |
| **3.x.2 Trên Oracle** | Cơ chế Oracle dùng cho chủ đề này, kèm demo và kết quả | Tự do |
| **3.x.3 Trên Redis** | Cơ chế Redis dùng cho chủ đề này, kèm demo và kết quả | Tự do |
| **3.x.4 So sánh** | Bảng tiêu chí, hai DBMS đặt cạnh nhau. Mỗi ô phải có bằng chứng: số đo, ảnh chụp hoặc tham chiếu tới demo | 1 bảng + nhận xét |
| **3.x.5 Kết luận cho ShopFlash** | Loại dữ liệu nào nên đặt ở DBMS nào, vì sao, và chức năng nào của app đang áp dụng điều đó | 1–2 đoạn |

Thứ tự và tên các tiểu mục con trong 3.x.2, 3.x.3 do người viết tự đặt.

## 2. Mỗi demo viết theo 4 bước

1. **Mục đích:** demo này muốn cho thấy điều gì. Viết thành một câu.
2. **Cách làm:** dữ liệu dùng (bao nhiêu dòng/key), câu lệnh hoặc script chính, công cụ đo.
3. **Kết quả:** số đo (bảng hoặc biểu đồ) và/hoặc ảnh chụp màn hình.
4. **Nhận xét:** kết quả nói lên điều gì về cơ chế của DBMS.

Demo nào không chứng minh được điều ghi ở bước 1 thì bỏ, đừng để cho đủ số lượng.

## 3. Quy ước đo (bắt buộc)

Phần này giống nhau cho cả nhóm, vì mục 5 của báo cáo đặt số liệu của 6 chủ đề cạnh nhau.

- **Ghi môi trường** ở đầu phần của mình, theo bảng:

  | Thành phần | Giá trị |
  |---|---|
  | Máy | CPU, RAM, ổ SSD/HDD |
  | Hệ điều hành, Docker | Phiên bản; RAM/CPU cấp cho Docker |
  | Oracle | Phiên bản image (digest) |
  | Redis | Phiên bản image (digest) |
  | Dữ liệu | Số dòng / số key dùng khi đo |

- **Lặp lại:** chạy khởi động (warm-up) một lần, không tính. Sau đó đo ít nhất 5 lần.
- **Cách báo cáo số:**
  - Thời gian chạy, thông lượng: **trung bình ± độ lệch chuẩn**.
  - Độ trễ khi chịu tải: thêm **p50 và p95**.
- **Đơn vị:** thời gian theo `ms`, thông lượng theo `ops/s` hoặc `req/s`, dung lượng theo `MB`.
- **Số liệu thô** lưu thành file `.csv` trong `results/`, để người khác vẽ lại hoặc kiểm tra.
- **Giới hạn:** ghi rõ Oracle Free bị giới hạn 2 CPU, 2 GB RAM, 12 GB dữ liệu khi so sánh hiệu năng.

## 4. Cách để file

```
report/<chủ-đề>/
├── README.md        cách chạy lại từng demo, từ đầu đến khi ra số
├── noi-dung.md      bản nháp phần 3.x (sẽ ghép vào báo cáo chung)
├── oracle/          script SQL của demo
├── redis/           script / lệnh Redis của demo
├── results/         số liệu thô .csv
└── images/          ảnh chụp, biểu đồ (đặt tên theo thứ tự: 3.2-01-explain-plan.png)
```

Câu SQL và lệnh Redis dùng trong demo sẽ được gom vào Phụ lục C, nên để trong `oracle/` và `redis/` thay vì chỉ dán trong bài.

## 5. Mẫu để chép

```markdown
# 3.x <Tên chủ đề>

Người viết: <tên>

## Môi trường đo
| Thành phần | Giá trị |
|---|---|
| Máy | |
| Hệ điều hành, Docker | |
| Oracle | |
| Redis | |
| Dữ liệu | |

## 3.x.1 Lý thuyết

## 3.x.2 Trên Oracle
### Demo 1: <tên>
- Mục đích:
- Cách làm:
- Kết quả:
- Nhận xét:

## 3.x.3 Trên Redis
### Demo 1: <tên>
- Mục đích:
- Cách làm:
- Kết quả:
- Nhận xét:

## 3.x.4 So sánh
| Tiêu chí | Oracle | Redis | Bằng chứng |
|---|---|---|---|
| | | | Demo … |

## 3.x.5 Kết luận cho ShopFlash
```

## 6. Kiểm tra trước khi gửi ghép

- [ ] Đủ 5 phần bắt buộc.
- [ ] Mỗi demo có đủ mục đích, cách làm, kết quả, nhận xét.
- [ ] Có bảng môi trường đo, số đo theo đúng quy ước ở mục 3.
- [ ] Người khác làm theo `README.md` chạy lại được và ra số gần giống.
- [ ] Mỗi ô trong bảng so sánh đều có bằng chứng.
- [ ] Tài liệu tham khảo ghi theo IEEE.
