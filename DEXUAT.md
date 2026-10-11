# Đề xuất: thêm `order_id` vào `reviews` để cửa hàng trả lời đánh giá của khách

Trạng thái: **ĐÃ TRIỂN KHAI** (xem mục 12 về kết quả và các điểm lệch so với đề xuất).

Mức ưu tiên: **P2** (chỉ làm sau khi TC-01 đến TC-18 của Roadmap V2 đã đạt, trước Giai đoạn 13 Deploy/demo). Đây là điều chỉnh ngoài Roadmap V2, xem mục 11.

## 1. Mục tiêu

Cho phép trả lời đánh giá sản phẩm của khách:

| Vai trò | Được trả lời |
| --- | --- |
| Nhân viên | Đánh giá **thuộc chi nhánh của mình** (chi nhánh đang được phân công) |
| Admin | **Toàn bộ** đánh giá |
| Khách | Chỉ xem phản hồi, không trả lời |

Không thêm bảng mới. Không đổi luồng đơn hàng, giỏ hàng, thanh toán, tồn kho.

## 2. Vấn đề hiện tại

- Bảng `reviews` chỉ có `user_id` và `product_id`, không có thông tin đơn hay chi nhánh.
- Một sản phẩm được bán ở nhiều chi nhánh (dữ liệu seed: sản phẩm 3 bán ở cả 5 chi nhánh). Chỉ nhìn sản phẩm thì không biết khách đã mua ở chi nhánh nào.
- Vì vậy chưa thể xác định "đánh giá nào thuộc chi nhánh của nhân viên".

## 3. Giải pháp

Lưu **đơn hàng** (`order_id`) mà khách dùng để đủ điều kiện đánh giá, ngay **lúc khách gửi đánh giá lần đầu**. Chi nhánh được **suy ra từ đơn** (`orders.store_id`), không lưu trùng.

### 3.1. Chọn `order_id` khi khách gửi đánh giá lần đầu

| Khách gửi từ | `order_id` được lưu |
| --- | --- |
| Trang chi tiết đơn hàng (form đã có `orderId`) | Chính đơn đó |
| Trang sản phẩm (form không có đơn) | Đơn **COMPLETED gần nhất** của khách có chứa sản phẩm |

Server luôn tự kiểm tra: đơn thuộc đúng khách, trạng thái COMPLETED, có chứa sản phẩm. Khách không thể tự khai đơn hay chi nhánh.

### 3.2. Định nghĩa "COMPLETED gần nhất"

- **Mốc thời gian hoàn tất** của một đơn: `changed_at` lớn nhất của dòng `order_status_history` có `new_status = 'COMPLETED'` của đơn đó. Nếu đơn (dữ liệu cũ) không có dòng lịch sử này thì dùng `orders.updated_at`.
- **Gần nhất** = mốc hoàn tất lớn nhất; nếu bằng nhau thì lấy `order_id` lớn hơn.
- Khi điền dữ liệu cho đánh giá đã có (mục 4.3), chỉ xét các đơn có mốc hoàn tất **không muộn hơn thời điểm tạo đánh giá**, để không gắn đánh giá vào một đơn hoàn tất sau khi khách đã đánh giá.

### 3.3. Khi khách sửa hoặc gửi lại đánh giá

`UQ_reviews_user_product` giữ nguyên nên mỗi khách chỉ có một đánh giá cho mỗi sản phẩm. Hệ quả:

- `order_id` được gán **một lần**, lúc tạo đánh giá. Khách sửa nội dung hoặc gửi lại từ trang của đơn khác **không làm đổi** `order_id`.
- Ngoại lệ: đánh giá cũ đang có `order_id = NULL` thì được gán theo quy tắc ở 3.1 ở lần sửa đầu tiên (nếu tra được đơn hợp lệ).
- Nếu đánh giá đã có phản hồi mà khách sửa nội dung: **giữ nguyên phản hồi** (không xóa dữ liệu), nhân viên hoặc admin có thể sửa lại phản hồi nếu cần.

### 3.4. Phản hồi

- Mỗi đánh giá có **một** phản hồi, được tạo hoặc sửa, không xóa.
- Nội dung được `trim`, độ dài từ 1 đến 1000 ký tự. Không chấp nhận chuỗi rỗng hoặc toàn khoảng trắng (kiểm tra ở Service và ở CHECK của DB).
- Mỗi lần tạo hoặc sửa, hệ thống ghi đè `replied_by` và `replied_at` bằng người và thời điểm **gần nhất**. Chấp nhận có chủ đích việc không giữ người trả lời gốc khi admin sửa phản hồi của nhân viên (không thêm cột hay bảng audit mới).

### 3.5. Vì sao chọn `order_id` thay vì `store_id`

- Chi nhánh của một đơn không đổi sau khi tạo (Roadmap V2 mục 6.6, không có chức năng chuyển đơn, không xóa cứng đơn có lịch sử), nên suy ra từ đơn luôn ổn định.
- Chỉ có một nguồn sự thật, không thể lệch giữa chi nhánh ghi trong đánh giá và chi nhánh của đơn.
- Nhân viên mở được đúng đơn khi trả lời; dùng lại được cho các chức năng xử lý phàn nàn sau này.

### 3.6. Tra chi nhánh của đánh giá

```sql
SELECT r.*, o.store_id
FROM reviews r
JOIN orders o ON o.order_id = r.order_id
WHERE o.store_id IN (<các chi nhánh nhân viên được phân công>)
```

INNER JOIN tự loại đánh giá có `order_id = NULL` khỏi danh sách của nhân viên (chỉ admin thấy).

## 4. Thay đổi database

Một script migration mới `database/migration_003_review_order_and_reply.sql` (idempotent, không xóa dữ liệu), đồng thời cập nhật `02_create_tables.sql`, `05_verify.sql`, `database/README.md`.

### 4.1. Cột thêm vào `reviews`

| Cột | Kiểu | Ý nghĩa |
| --- | --- | --- |
| `order_id` | BIGINT NULL, FK -> `orders` | Đơn hoàn tất mà khách dùng để đánh giá; gán một lần lúc tạo |
| `reply` | NVARCHAR(1000) NULL | Nội dung phản hồi |
| `replied_by` | BIGINT NULL, FK -> `users` | Người trả lời hoặc sửa phản hồi gần nhất |
| `replied_at` | DATETIME2(0) NULL | Thời điểm trả lời hoặc sửa gần nhất |

### 4.2. Ràng buộc và index

- CHECK `reply`, `replied_by`, `replied_at` cùng NULL hoặc cùng có giá trị, và `reply` không rỗng sau khi trim:

```sql
CHECK (
  (reply IS NULL AND replied_by IS NULL AND replied_at IS NULL)
  OR
  (reply IS NOT NULL AND LEN(LTRIM(RTRIM(reply))) > 0
   AND replied_by IS NOT NULL AND replied_at IS NOT NULL)
)
```

- Index `IX_reviews_order` trên `(order_id)` cho phép nối nhanh với `orders`.
- `order_id` cho phép NULL để không phá đánh giá cũ.
- `UQ_reviews_user_product` giữ nguyên. Đánh giá chỉ gắn với một đơn.
- Lưu ý: DB không thể tự bảo đảm `order_id` thuộc đúng `user_id` và có chứa `product_id` (CHECK không tham chiếu bảng khác). Hai điều kiện này do Service kiểm tra và được bao phủ bằng test (mục 8).

### 4.3. Điền dữ liệu cho đánh giá đã có

Với mỗi đánh giá có `order_id IS NULL`, chọn đơn thỏa **tất cả**:

1. Thuộc đúng khách của đánh giá.
2. Trạng thái COMPLETED.
3. Có chứa sản phẩm của đánh giá.
4. Mốc hoàn tất (theo 3.2) không muộn hơn thời điểm tạo đánh giá.

Trong các đơn thỏa, lấy đơn có mốc hoàn tất lớn nhất (bằng nhau thì `order_id` lớn nhất). Không tra được thì để `order_id = NULL` (chỉ admin thấy và trả lời được). Dữ liệu seed: đánh giá của khách 5 cho sản phẩm 3 sẽ gắn với đơn #5 (chi nhánh 5).

Hình dạng truy vấn (minh họa, cần đối chiếu tên cột thật của `order_items`, `reviews.created_at` trong schema hiện tại):

```sql
UPDATE r
SET r.order_id = x.order_id
FROM reviews r
OUTER APPLY (
    SELECT TOP 1 o.order_id
    FROM orders o
    JOIN order_items oi ON oi.order_id = o.order_id
    OUTER APPLY (
        SELECT MAX(h.changed_at) AS completed_at
        FROM order_status_history h
        WHERE h.order_id = o.order_id AND h.new_status = 'COMPLETED'
    ) c
    WHERE o.user_id = r.user_id
      AND o.order_status = 'COMPLETED'
      AND oi.product_id = r.product_id                       -- đối chiếu cột thật
      AND COALESCE(c.completed_at, o.updated_at) <= r.created_at
    ORDER BY COALESCE(c.completed_at, o.updated_at) DESC, o.order_id DESC
) x
WHERE r.order_id IS NULL AND x.order_id IS NOT NULL;
```

Script phải chạy lại nhiều lần an toàn (điều kiện `r.order_id IS NULL` và kiểm tra cột/ràng buộc đã tồn tại trước khi tạo).

## 5. Phân quyền

- **Nhân viên:** chỉ đánh giá có đơn thuộc chi nhánh nằm trong phạm vi do `StaffStoreScopeService` trả về (assignment ACTIVE). Lấy danh sách này từ service, không nhận từ request. Dùng đúng phạm vi hiện có của service này, không viết thêm logic riêng cho trạng thái Store.
- **Admin:** mọi đánh giá, kể cả `order_id = NULL`; sửa được phản hồi của nhân viên.
- Kiểm tra quyền thực hiện ở service, trên bản ghi đã đọc từ DB, trước khi ghi. Không tin bất kỳ giá trị chi nhánh hay đơn nào từ form.
- Đánh giá của chi nhánh khác hoặc không có đơn: nhân viên nhận 404 (không lộ sự tồn tại).

## 6. Thay đổi mã nguồn (dự kiến)

| Phần | Thay đổi |
| --- | --- |
| `entity/Review` | Thêm quan hệ `order` (ManyToOne, lazy) và 3 trường phản hồi, getter/setter |
| `ReviewServiceImpl.submit` | Chọn và lưu `order_id` theo mục 3.1 đến 3.3 (đã có sẵn bước kiểm tra đơn hoàn tất) |
| `ReviewServiceImpl.reply` (thêm vào `ReviewService` hiện có) | `reply(reviewId, text)`; kiểm tra phạm vi theo vai trò; trim và validate độ dài. **Không tạo service mới**, để giữ đúng danh sách Service của Roadmap V2 |
| `ReviewRequest` / `ReviewController` | Truyền `orderId` (đã có sẵn ở form trang đơn) xuống service để chọn đơn |
| `OrderItemRepository` | Truy vấn "đơn COMPLETED gần nhất của khách có chứa sản phẩm" (theo 3.2) và kiểm tra đơn cụ thể |
| `ReviewRepository` | Truy vấn đánh giá theo danh sách chi nhánh qua `order.store.id`, có phân trang |
| `StaffReviewController` (mới) | `GET /staff/reviews`, `POST /staff/reviews/{id}/reply` |
| `AdminReviewController` | Thêm `POST /admin/reviews/{id}/reply` |
| `ProductReviewsResponse` / `ReviewResponse` | Thêm phản hồi và tên chi nhánh để hiển thị |
| Template | `staff/reviews.html` (mới), `admin/reviews.html` (thêm ô trả lời và liên kết đơn), `product/detail.html` (hiển thị phản hồi), menu nhân viên |

Hiển thị cho khách: "Phản hồi từ OneShop <tên chi nhánh của đơn>". **Không hiện tên nhân viên.** Nếu admin trả lời đánh giá không có đơn: "Phản hồi từ OneShop".

Nhân viên và admin có liên kết sang đúng đơn hàng khi trả lời (nhân viên chỉ mở được đơn thuộc chi nhánh mình, theo phân quyền hiện có). Nội dung phản hồi hiển thị bằng `th:text` (escape), không dùng `th:utext`.

## 7. Phạm vi KHÔNG làm

- Bảng mới, hệ thống phiếu hỗ trợ, chat.
- Thông báo cho khách (email, SMS, trong hệ thống).
- Đính kèm ảnh vào phản hồi.
- Trả lời lồng nhiều cấp: mỗi đánh giá có **một** phản hồi.
- Xóa phản hồi (chỉ sửa), thống nhất nguyên tắc không xóa dữ liệu.
- Lưu thêm `store_id` trùng lặp trong `reviews`.
- Lưu lịch sử các lần sửa phản hồi hay người trả lời gốc.

## 8. Quy mô và kiểm thử

Ước lượng: **12–15 file mới hoặc sửa, 600–900 dòng kể cả test**. Không đụng luồng đơn hàng, thanh toán, tồn kho.

Test cần có:

- Gửi đánh giá từ trang đơn lưu đúng đơn đó; từ trang sản phẩm lưu đơn hoàn tất gần nhất theo 3.2 (kể cả trường hợp bằng mốc thời gian); khách không tự đặt được `order_id` của đơn người khác, đơn chưa hoàn tất, hoặc đơn không chứa sản phẩm.
- Khách sửa đánh giá: `order_id` giữ nguyên; gửi lại từ đơn khác không đổi `order_id`; đánh giá cũ `order_id = NULL` được gán ở lần sửa đầu nếu tra được; sửa nội dung đánh giá thì phản hồi được giữ nguyên.
- Nhân viên chỉ thấy và trả lời được đánh giá của chi nhánh mình; chi nhánh khác 404; nhân viên đang bị thu hồi phân công không trả lời được.
- Admin trả lời được mọi đánh giá, kể cả không có đơn; admin sửa phản hồi của nhân viên thì `replied_by`, `replied_at` đổi theo người sửa.
- Khách và khách vãng lai không gọi được API trả lời; thiếu CSRF bị 403.
- Độ dài phản hồi (rỗng, toàn khoảng trắng, 1, 1000, 1001), nội dung được escape khi hiển thị (XSS).
- CHECK của DB chặn bản ghi phản hồi thiếu `replied_by` hoặc `replied_at`, và chặn `reply` rỗng.
- Migration chạy lại nhiều lần an toàn; điền `order_id` đúng cho dữ liệu seed, và không gắn vào đơn hoàn tất sau thời điểm tạo đánh giá.
- Chạy lại toàn bộ bộ test hiện có (958 test) để bảo đảm không hồi quy.

## 9. Quyết định cần chủ dự án xác nhận

### 9.1. Đã chốt trong bản này

| # | Nội dung | Quyết định |
| --- | --- | --- |
| 1 | Lưu `store_id` hay `order_id`? | `order_id`; chi nhánh suy ra từ đơn. |
| 2 | Cho sửa phản hồi đã gửi? | Có, không cho xóa. |
| 4 | Admin có sửa được phản hồi của nhân viên? | Có. |
| 5 | Khách không nhận thông báo, chỉ thấy phản hồi khi mở trang sản phẩm? | Chấp nhận. Roadmap V2 chỉ cấm SMS/email ngoài hệ thống cho pickup; ở đây không làm thông báo để giữ phạm vi nhỏ. |
| 6 | Khách mua cùng sản phẩm ở nhiều chi nhánh chỉ có một đánh giá, gắn một đơn? | Chấp nhận (mỗi khách một đánh giá cho mỗi sản phẩm). |
| 7 | "COMPLETED gần nhất" tính theo mốc nào? | Theo 3.2: `order_status_history` dòng COMPLETED, dự phòng `orders.updated_at`, tiêu chí phụ `order_id DESC`. |
| 8 | Khách sửa hoặc gửi lại đánh giá thì `order_id` ra sao? | Gán một lần lúc tạo, giữ nguyên khi sửa (ngoại lệ: đang NULL thì gán ở lần sửa đầu). |
| 9 | Sửa nội dung đánh giá đã có phản hồi? | Giữ nguyên phản hồi. |
| 10 | Người sửa phản hồi? | Ghi đè bằng người và thời điểm gần nhất; không lưu người gốc. |
| 11 | Phản hồi rỗng? | Không chấp nhận; chặn ở Service và CHECK. |
| 12 | Service trả lời | Thêm `reply()` vào `ReviewService`, không tạo `ReviewReplyService`. |

### 9.2. Cần xác nhận trước khi làm (đối chiếu code và schema thật)

| # | Câu hỏi | Đề xuất |
| --- | --- | --- |
| 3 | Nhân viên có trả lời được đánh giá đã bị ẩn? Cần kiểm tra `reviews` hiện có cột trạng thái ẩn hay không (Roadmap V2 không liệt kê cột này). | Không. Admin trả lời được mọi đánh giá. Nếu `reviews` không có cột trạng thái thì bỏ quy tắc này. |
| 13 | `order_items` có `product_id` hoặc truy được product qua `store_product_id` không? | Cần có để truy vấn "đơn chứa sản phẩm" và backfill. Xác nhận tên cột thật. |
| 14 | `reviews` có `created_at`, `orders` có `updated_at` không? | Cần cho quy tắc 3.2 và 4.3. |
| 15 | Nhân viên có assignment ACTIVE nhưng Store INACTIVE: xem/trả lời được không? | Theo đúng kết quả `StaffStoreScopeService` hiện có. Nếu service loại Store INACTIVE thì nhân viên không thấy, admin vẫn xử lý; không sửa service chỉ vì tính năng này. |

## 10. Rủi ro

- **Đánh giá cũ không tra được đơn** sẽ chỉ admin trả lời được. Ảnh hưởng thấp vì dữ liệu seed chỉ có một đánh giá.
- **Lệch phạm vi Roadmap:** thêm cột vào `reviews` và quyền trả lời cho nhân viên là thay đổi nhỏ nhưng cần ghi nhận vào tài liệu (mục 11).
- **Phân quyền theo chi nhánh** là phần dễ sai nhất nên được ưu tiên test (đặc biệt truy vấn nối qua `orders`).
- **Ràng buộc chỉ ở tầng Service:** DB không bảo đảm đơn thuộc đúng khách và chứa đúng sản phẩm; nếu Service sai thì dữ liệu sai không bị chặn. Chấp nhận ở quy mô đồ án, bù bằng test.
- **Mất dấu người trả lời gốc** khi admin sửa phản hồi của nhân viên; đã chấp nhận có chủ đích (mục 3.4).
- **Chi phí nối bảng:** mỗi lần nhân viên xem danh sách phải nối `reviews` với `orders`; với index `IX_reviews_order` và khóa chính của `orders` thì không đáng kể ở quy mô hiện tại.
- Hai lỗi upload ảnh (Cloudinary, BUG-01 và CODE-01 trong `note.md`) vẫn mở; không liên quan nhưng nên xử lý riêng.

## 11. Ghi nhận điều chỉnh so với Roadmap V2 (gọi là V2.1)

Tính năng này **không có trong Roadmap V2**, nên khi làm phải ghi vào tài liệu thay vì để xuất hiện lặng lẽ:

- Mục 5 (Tác nhân và chức năng): thêm cho Nhân viên "xem và trả lời đánh giá của Store được phân công".
- Mục 7.3 (Bảng/trường): thêm `reviews.order_id`, `reply`, `replied_by`, `replied_at`.
- Mục 8.1 (Service): `ReviewService` thêm trách nhiệm trả lời đánh giá và gắn đơn.
- Mục 11 (Ưu tiên): xếp P2.
- Mục 13 (Kiểm thử): thêm TC-19 trở đi cho các test ở mục 8 của đề xuất này, không bỏ TC-01 đến TC-18.
- Không thuộc danh sách OUT OF SCOPE; không đổi stack, không thêm bảng.
## 12. Kết quả triển khai

Đã làm đúng phạm vi mục 1 đến 8; không thêm bảng, không đổi luồng đơn hàng, giỏ hàng, thanh toán, tồn kho.

### 12.1. Đã thực hiện

- **Database:** `migration_003_review_order_and_reply.sql` (idempotent, tự điền `order_id` cho đánh giá cũ theo 4.3); cập nhật `02_create_tables.sql`, `03_create_indexes.sql` (`IX_reviews_order`), `04_seed_data.sql` (đánh giá seed gắn đơn #5), `05_verify.sql` (kiểm tra cấu trúc và kiểm tra dữ liệu đơn thuộc đúng khách, COMPLETED, chứa sản phẩm), `database/README.md`.
- **Mã nguồn:** `Review` (quan hệ `order`, phản hồi), `ReviewService`/`ReviewServiceImpl` (chọn `order_id`, `reply`, `getStaffReviews`; không tạo service mới), `OrderItemRepository` và `ReviewRepository` (truy vấn theo 3.2 và theo chi nhánh), `ReviewController` (truyền `orderId`), `StaffReviewController` (mới), `AdminReviewController` (thêm trả lời), `SecurityConfig` (form `/staff/reviews/**` luôn yêu cầu CSRF), các DTO phản hồi.
- **Giao diện:** `staff/reviews/index.html` (mới) và mục menu "Đánh giá" cho nhân viên; `admin/reviews.html` (ô trả lời, liên kết đơn, chi nhánh); `product/detail.html` (hiển thị phản hồi bằng `th:text`).
- **Kiểm thử:** thêm test cho chọn đơn (kể cả bằng mốc thời gian và dự phòng `updated_at`), phân quyền theo chi nhánh, đánh giá bị ẩn, đánh giá không có đơn, độ dài và khoảng trắng của phản hồi, CHECK của DB, CSRF, escape XSS. Toàn bộ 982 test đạt.

### 12.2. Giải quyết các mục "cần xác nhận" (9.2) bằng đối chiếu code và schema thật

| # | Kết quả đối chiếu |
| --- | --- |
| 3 | `reviews.status` có (ACTIVE/HIDDEN): nhân viên chỉ thấy và trả lời đánh giá ACTIVE; admin trả lời mọi đánh giá. |
| 13 | `order_items` không có `product_id`; truy sản phẩm qua `store_product_id` -> `store_products.product_id` (đã dùng trong truy vấn và backfill). |
| 14 | `reviews.created_at` và `orders.updated_at` đều có. |
| 15 | `StaffStoreScopeService` chỉ tính assignment ACTIVE và Store ACTIVE; giữ nguyên. Đánh giá thuộc đơn của Store INACTIVE chỉ admin xử lý. |

### 12.3. Điểm lệch so với đề xuất

- **Mục 3.3 (khách sửa đánh giá):** hệ thống hiện **không có chức năng cho khách sửa đánh giá** (gửi lần hai bị từ chối "đã đánh giá"). Vì không thêm chức năng ngoài phạm vi, `order_id` chỉ được gán lúc tạo, và test xác nhận lần gửi thứ hai không đổi `order_id`. Đánh giá cũ có `order_id = NULL` được điền bởi migration thay vì ở "lần sửa đầu tiên".
- **Ghi nhận vào Roadmap V2 (mục 11):** chưa chỉnh sửa tài liệu Roadmap; cần chủ dự án quyết định có ghi nhận V2.1 hay không.

### 12.4. Lỗi phát hiện và sửa trong lúc làm

Phương thức mặc định `submit(email, request)` (2 tham số) gọi nội bộ sang bản 3 tham số nên **bỏ qua `@PreAuthorize`** khi đi qua proxy. Đã bỏ phương thức này; chỉ còn `submit(email, request, orderId)`.
