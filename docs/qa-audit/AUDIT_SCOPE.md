# Audit đang thực hiện

Chỉ test/fixture/cấu hình test/tài liệu. Không sửa production. Database mới duy nhất: `oneshop_qa_20261024_test`. Không xóa database sau audit để giữ bằng chứng.

Source inventory: `evidence/inventory.json`. Đây là structural scan có giới hạn heuristic, không phải chứng minh đã review mọi nhánh. Các literal trong annotation cần đối chiếu runtime; `/logout` do Security tạo, `/error` do Boot tạo phải bổ sung ngoài Controller.

Mỗi route: kiểm tra actor hợp lệ, thiếu JWT, sai role, ID không tồn tại/khác chủ, input validation, thành công, rollback nếu ghi. Mỗi UI candidate: render, thao tác, xác nhận/hủy, điều kiện hiển thị, validation và reload; chưa có execution evidence phải giữ NOT_RUN, không suy diễn PASS.

Ưu tiên sâu: checkout (COD/ONLINE/pickup/split/consent/stale total), payment retry/cancel/refund, inventory concurrency, state transitions và scope. Tận dụng suite hiện có nhưng phân biệt real SQL, mock-backed HTTP và Chrome E2E.

Môi trường SQL Server local khả dụng. Tài liệu README trỏ nhiều file đặc tả/runbook không tồn tại trong checkout này: chưa đủ bằng chứng độc lập cho mọi DEC. Không coi V3 là đề xuất chưa triển khai: source checkout và canonical schema đã có Product.price, cart_items.product_id và order_fulfillments.
