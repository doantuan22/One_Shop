# V3-BE-07 — Phân tích lỗi baseline và cách xử lý

## Phân loại baseline

| Nhóm baseline | Số lượng | Kết quả cuối |
| --- | ---: | ---: |
| Failure | 49 | 49 PASS |
| Error | 326 | 326 PASS |
| Skipped | 42 | 42 PASS |
| Tổng không PASS | 417 | 417 PASS |

Đây là số identity test, không phải 417 lỗi production. Phần lớn lỗi phát sinh vì assertion/fixture V2 còn giả định Order thuộc một Store, CartItem thuộc StoreProduct, giá nằm ở StoreProduct hoặc enum trạng thái cũ.

## Nhóm nguyên nhân và resolution

| Nhóm | Ví dụ lớp | Nguyên nhân baseline | Resolution V3 |
| --- | --- | --- | --- |
| Cart/catalog V2 | `CartDatabaseIntegrationTest`, `Catalog*` | StoreProduct/giá Store cũ, tập seed cũ | Cart theo Product trung tâm; giá canonical `products.price`; assertion theo dữ liệu seed V3 |
| Checkout/allocation | `Checkout*`, `Delivery*`, `Pickup*` | Một Order/một Store và payload checkout cũ | Fulfillment nguồn, ONLINE một Order nhiều fulfillment, COD split consent, pickup V3 |
| Payment/history | `PaymentDatabaseIntegrationTest`, `Phase12*` | Gọi success/failure primitive và mốc history V2 | Mock ledger server-side, đối soát/refund/idempotence, mapping mốc history sang luồng V3 |
| Staff/admin scope | `Staff*`, `Phase11*` | Order store cũ, queue và trạng thái cũ | Scope theo fulfillment source Store, enum V3, Staff ACTIVE assignment |
| Review | `Review*` | Mỗi user/SKU một Review, reply scope chưa rõ | Một Review/OrderItem, filtered unique migration, Staff source reply; Admin moderation |
| Web/asset | `HomePage*`, `SeedCategoryImages*`, `LargeSeed*` | URL asset/seed large V2 và HTML encoding cũ | 40 WebP demo tồn tại, URL/static assertion V3, unescape HTML đúng ngữ cảnh |
| Disabled legacy tests | 38 Cart + 3 large seed + 1 Catalog HTTP | `@Disabled`/điều kiện môi trường che test V2 | Port cùng method identity sang V3 rồi bật lại; Cloudinary credentials bị blank trong context test để không upload ra ngoài |

## Lỗi ứng dụng thật đã sửa

1. Primitive Order transition có thể vượt qua orchestration Fulfillment.
2. Payment reconciliation có thể tin proof không thuộc Order/sai amount.
3. Một số truy vấn lock quét Payment/Fulfillment gây deadlock hoặc chặn giữa Order độc lập.
4. Note history của Staff có thể hiển thị lại pickup code đã được ẩn ở field riêng.
5. Dashboard Admin đa Store lọc theo `orders.store_id` thay vì nguồn Fulfillment.
6. Refresh checkout làm mất receiver name/phone và khiến fingerprint xác nhận đầu tiên sai.

Các sửa này có test SQL Server/concurrency hoặc browser đi kèm. Không thay schema ngoài migration Review UNIQUE đã được chủ dự án duyệt.

## Bảo toàn identity và trace

`target/be07-evidence/original-tests/` giữ metadata gốc của các test được port. `baseline-cases.json`, `final-cases.json` và `final-comparison.json` cung cấp trace đầy đủ từng identity: không có removal hay regression. Một tên method lịch sử vẫn nhắc V2 nhưng assertion bên trong đã được rebase vào hợp đồng V3; không diễn giải đó là hỗ trợ schema V2 cũ.
