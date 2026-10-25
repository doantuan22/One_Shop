# OneShop — Triển khai lên AWS

Tài liệu này **chuẩn bị** triển khai; chưa có bước nào được thực hiện trên AWS thật. Mỗi mục ghi rõ điều đã được kiểm chứng trên máy local (✔) và điều chưa thể kiểm chứng nếu không có tài khoản AWS (⚠). Không chứa mật khẩu/khóa thật: mọi giá trị trong `< >` là chỗ phải điền từ kho bí mật.

## 1. Kiến trúc đề xuất

```text
Internet ──HTTPS──> ALB (ACM certificate) ──HTTP 8080──> EC2 (Amazon Linux 2023, Java 21, systemd: oneshop.jar)
                                                              │ 1433, TLS            │ HTTPS
                                                              ▼                      ▼
                                              RDS for SQL Server (private subnet)   Cloudinary (ảnh)
```

| Thành phần | Lựa chọn | Lý do / ghi chú |
|---|---|---|
| Ứng dụng | **1 EC2** chạy `oneshop.jar` bằng systemd (`deploy/oneshop.service`) | Ứng dụng stateless (JWT cookie, không session, không ghi file), một jar tự chứa. Đơn giản nhất, dễ rollback bằng cách đổi jar |
| TLS ra Internet | **ALB + chứng chỉ ACM**; hoặc nginx + Let's Encrypt trên chính EC2 (`deploy/nginx-oneshop.conf`) nếu muốn bỏ chi phí ALB | Profile `prod` đã bật `server.forward-headers-strategy=framework` để tin `X-Forwarded-*` |
| CSDL | **RDS for SQL Server** (Express hoặc Web/Standard), Single-AZ cho demo, Multi-AZ cho thật | Dự án dùng T-SQL, filtered unique index, `THROW`, collation `Vietnamese_100_CI_AI`: cần SQL Server thật (không thay bằng Aurora/PostgreSQL) |
| Ảnh | Cloudinary (giữ nguyên) | Database chỉ lưu URL + public id; không cần S3 cho ảnh |
| Bí mật | SSM Parameter Store (SecureString) hoặc Secrets Manager → ghi vào `/etc/oneshop/oneshop.env` lúc khởi động | Không đặt trong AMI, user-data thô, hay repo |

Phương án khác: **Elastic Beanstalk** (Corretto 21, nền tảng Java SE, upload jar), hoặc **ECS Fargate / App Runner** dùng `Dockerfile` đã có trong repo (multi-stage, JRE 21, user không phải root, `EXPOSE 8080`). Cả hai đều chạy được với cùng biến môi trường; không tự thêm công nghệ mới nếu không cần.

**Chi phí / giấy phép (⚠ chưa tra bảng giá, hãy dùng AWS Pricing Calculator):** RDS SQL Server tính phí theo instance-giờ + lưu trữ, giá đã gồm license cho Standard/Web; **Express** không tính license nhưng giới hạn 10 GB mỗi database và RAM sử dụng thấp. Kiểm tra Free Tier hiện hành và danh sách instance class được hỗ trợ trong region trước khi chọn. SQL Server tự cài trên EC2 làm bạn phải tự lo license, vá lỗi, backup, nên không được khuyến nghị.

## 2. Yêu cầu runtime (đã xác minh từ mã nguồn và chạy thử)

| Mục | Giá trị |
|---|---|
| Java | **21** (Temurin/Corretto). `pom.xml: java.version=21` |
| Artifact | `./mvnw -B -DskipTests package` → `target/oneshop.jar` (Spring Boot fat jar, ≈ 64 MB, không chứa devtools) ✔ |
| Cổng | `PORT` (mặc định 8080) ✔ |
| Khởi động | ≈ 15 giây với profile `prod` trên SQL Server local ✔ |
| Health check | `GET /health` → `200 {"status":"UP"}`, không cần đăng nhập, **không chạm database** (chỉ là liveness) ✔ |
| Profile | **`SPRING_PROFILES_ACTIVE=prod`** — mặc định của ứng dụng là `dev`, nên bắt buộc đặt biến này (systemd unit và Dockerfile đã đặt) |
| Ghi file | Không. Upload ảnh đi thẳng sang Cloudinary; log ra stdout |
| RAM/CPU | ⚠ ước lượng: EC2 ≥ 2 GB RAM (`t3.small`), JVM `-XX:MaxRAMPercentage=70`; chưa đo tải thật |
| Mạng ra | Cloudinary (HTTPS 443), SMTP nếu bật `MAIL_*`, RDS 1433 |

Cấu hình: toàn bộ qua biến môi trường, danh sách ở `.env.example` / `deploy/oneshop.env.example` và bảng trong `README.md`. Production **không** tự tạo/sửa schema (`ddl-auto=validate`), **không** seed dữ liệu, **không** bật cổng thanh toán mô phỏng, ONLINE checkout đóng, worker hủy đơn tắt (xem §9).

## 3. Mạng và Security Group

| Security Group | Inbound | Outbound |
|---|---|---|
| `sg-alb` | 443 (và 80 → redirect) từ `0.0.0.0/0` | 8080 tới `sg-app` |
| `sg-app` | 8080 **chỉ** từ `sg-alb`; 22 chỉ từ IP quản trị (hoặc dùng SSM Session Manager, không mở 22) | 443 ra Internet; 1433 tới `sg-db` |
| `sg-db` | 1433 **chỉ** từ `sg-app` (và từ máy quản trị qua bastion/SSM tunnel khi chạy script khởi tạo) | – |

RDS đặt ở private subnet, **Publicly accessible = No**. Bật *Force SSL* (`rds.force_ssl=1`) trong parameter group và mã hóa lưu trữ (KMS).

## 4. Khởi tạo SQL Server (database mới)

Chạy từ một máy có `sqlcmd` (mssql-tools18) và đường tới RDS (EC2 bastion, SSM port-forward hoặc VPN). **Không chạy trên database đã có dữ liệu.**

1. Tạo login/user riêng cho ứng dụng sau khi có database (không dùng master user của RDS làm `DB_USERNAME`). Quyền tối thiểu ứng dụng cần: `db_datareader`, `db_datawriter` (không cần DDL vì `ddl-auto=validate`; các script migration chạy bằng tài khoản quản trị).
2. Sinh BCrypt hash cho mật khẩu admin (không để mật khẩu rõ trong lịch sử shell):
   ```bash
   ./mvnw -B -DskipTests package
   ONESHOP_PASSWORD='<mật khẩu admin>' scripts/db/new-password-hash.sh      # in hash $2a$10$...
   ```
3. Tạo database + schema + 3 role + admin đầu tiên (**chế độ production: không có tài khoản demo, không có đơn thử**):
   ```bash
   export SQLCMDPASSWORD='<mật khẩu master RDS>'
   scripts/db/initialize-database.sh <rds-endpoint> <master-user> oneshop production owner@<domain> admin "Họ tên chủ shop" '<hash>'
   ```
   Cần `SQLCMD_EXTRA=-C` chỉ khi máy chạy sqlcmd chưa tin CA của RDS (xem §5). Windows: `scripts/db/Initialize-OneShopDatabase.ps1 -Mode Production ...`.
4. Đăng nhập `/admin` bằng tài khoản vừa tạo, rồi tạo chi nhánh, nhân viên, thương hiệu, danh mục, sản phẩm, tồn kho từ giao diện quản trị. Ảnh tải lên đi qua Cloudinary.
5. (Chỉ cho môi trường demo/UAT) thay `production` bằng `demo '<hash>'` để nạp catalog 40 SKU, 5 chi nhánh, 12 tài khoản demo. **Không dùng chế độ demo cho site thật công khai.** Đơn hàng demo (profile `demo-seed`) chỉ chạy được với SQL Server local Developer/Express nên không dùng trên RDS.

✔ Đã chạy thật trên SQL Server local: chế độ `production`, `demo`, `test` và `empty` bằng cả script `.sh` và `.ps1`; schema mới giống hệt (480 đối tượng) database `oneshop` hiện hành; chạy lại các script không tạo trùng. ⚠ Chưa chạy trên RDS: cần xác nhận `CREATE DATABASE … COLLATE Vietnamese_100_CI_AI` và `sqlcmd` qua TLS với master user của RDS (RDS cho phép, nhưng chưa thử).

## 5. Kết nối TLS tới RDS

`DB_TRUST_SERVER_CERTIFICATE=false` (mặc định, giữ nguyên). Chứng chỉ RDS do Amazon RDS CA ký nên Java mặc định chưa tin. ⚠ (chưa thử trên RDS thật) tạo trust store:

```bash
curl -o global-bundle.pem https://truststore.pki.rds.amazonaws.com/global/global-bundle.pem
# keytool chỉ nhập chứng chỉ đầu tiên của một bundle: tách từng chứng chỉ
csplit -s -z -f rds-ca- global-bundle.pem '/-----BEGIN CERTIFICATE-----/' '{*}'
i=0; for f in rds-ca-*; do keytool -importcert -noprompt -alias "rds-$((i++))" -file "$f" \
   -keystore /etc/oneshop/rds-truststore.jks -storepass '<mật khẩu trust store>'; done
```
Rồi đặt trong `oneshop.env`:
```
DB_JDBC_OPTIONS=;trustStore=/etc/oneshop/rds-truststore.jks;trustStorePassword=<…>;hostNameInCertificate=*.<id>.<region>.rds.amazonaws.com
```
(`DB_JDBC_OPTIONS` được nối vào JDBC URL). Không dùng `trustServerCertificate=true` cho production.

## 6. Cài đặt và chạy ứng dụng (EC2)

```bash
sudo dnf install -y java-21-amazon-corretto-headless
sudo useradd --system --home /opt/oneshop --shell /sbin/nologin oneshop
sudo mkdir -p /opt/oneshop /etc/oneshop /var/log/oneshop && sudo chown oneshop: /var/log/oneshop
# build ở máy CI/dev:  ./mvnw -B -DskipTests package   rồi chép target/oneshop.jar lên
sudo cp oneshop.jar /opt/oneshop/oneshop.jar
sudo cp deploy/oneshop.env.example /etc/oneshop/oneshop.env && sudo chown root:oneshop /etc/oneshop/oneshop.env && sudo chmod 640 /etc/oneshop/oneshop.env
sudoedit /etc/oneshop/oneshop.env          # điền giá trị thật từ kho bí mật
sudo cp deploy/oneshop.service /etc/systemd/system/ && sudo systemctl daemon-reload && sudo systemctl enable --now oneshop
journalctl -u oneshop -f                   # chờ dòng "Started OneShopApplication"
```
`JWT_SECRET` ≥ 32 byte (nếu thiếu/ngắn ứng dụng **từ chối khởi động**). Tạo bằng `openssl rand -base64 48`.

## 7. Cloudinary, HTTPS, CSRF/cookie

- Giữ nguyên tài khoản Cloudinary; chỉ đặt `CLOUDINARY_*` ở server (không bao giờ ở client). Ảnh seed demo trỏ tới cloud name của dự án; site mới nên tải ảnh của mình. Khi ảnh lỗi giao diện đã có fallback placeholder.
- HTTPS bắt buộc: cookie JWT là `HttpOnly; Secure; SameSite=Lax` (`JWT_COOKIE_SECURE=true` mặc định ở `prod`). Nếu truy cập bằng HTTP thuần, đăng nhập sẽ không giữ được phiên.
- `GET /health` cho ALB target group (matcher 200, interval 30 s).
- Không có endpoint debug/actuator công khai (đã kiểm tra: `/actuator/**` yêu cầu đăng nhập, lỗi không lộ stack trace).

## 8. Smoke test sau khi deploy

1. `curl -s https://<domain>/health` → `{"status":"UP"}`.
2. Mở `/`, `/products`, `/stores`, `/login` (200, không lỗi console).
3. Đăng nhập admin → `/admin` mở được; `/staff`, `/cart` với admin trả về 403/redirect (phân quyền).
4. Tạo chi nhánh + 1 sản phẩm + tồn kho; xem ở trang khách.
5. Đăng ký khách, đặt đơn **COD** → đơn xuất hiện ở `/orders`; nhân viên của chi nhánh xử lý Fulfillment.
6. Ảnh sản phẩm hiển thị (Cloudinary) và upload ảnh ở admin thành công.
7. `journalctl -u oneshop` không có `ERROR`; có dòng "Payment timeout worker is OFF and ONLINE checkout is closed".
Các kịch bản 1–2 và COD/Pickup/Online đã chạy bằng Chrome thật trên bản `prod` + SQL Server local (xem `docs/audit/PRE_AWS_DEPLOY_AUDIT.md`).

## 9. Thanh toán ONLINE và worker

Hệ thống chỉ có cổng thanh toán **mô phỏng** (không tiền thật). Vì vậy `prod` mặc định đóng ONLINE checkout và tắt cổng mô phỏng; chỉ mua COD / nhận tại cửa hàng. Chỉ cho một deployment **demo** mới đặt `ONESHOP_CHECKOUT_ONLINE_ENABLED=true` và `ONESHOP_PAYMENT_MOCK_GATEWAY_ENABLED=true` (đã chạy thử luồng ONLINE/FAILED/REFUND ✔). Nếu mở ONLINE, cân nhắc `ONESHOP_PAYMENT_TIMEOUT_WORKER_ENABLED=true` để đơn chưa thanh toán sau 15 phút tự hủy và hoàn tồn; chỉ chạy **một** instance có worker (nhiều instance cùng bật worker chưa được kiểm chứng).

## 10. Nâng cấp schema và dữ liệu có sẵn

- Schema mới đủ cho cài đặt mới; **không** chạy lại `01_schema.sql` trên database đã có.
- Database cũ (V3) lên bản hiện tại: sao lưu → chạy `database/migrations/V001…V008` theo thứ tự (chạy lại được, không phá dữ liệu) → khởi động ứng dụng (tự kiểm tra schema, từ chối chạy nếu thiếu).
- Đưa dữ liệu thật từ máy local lên RDS: (1) backup `.bak` database local, (2) khôi phục thành **bản sao**, chạy migration trên bản sao và kiểm tra, (3) đưa lên RDS bằng *native backup/restore* qua S3 (cần option group `SQLSERVER_BACKUP_RESTORE`, bucket S3 và IAM role) — ⚠ chưa thử; hoặc xuất/nhập bằng công cụ khác. **Database `oneshop` hiện tại chứa tài khoản demo dùng mật khẩu đã biết: không đưa nó lên một site công khai** (hoặc đổi/vô hiệu hóa toàn bộ tài khoản demo trước).

## 11. Backup, rollback

- RDS: bật automated backup (≥ 7 ngày) và Point-in-Time Recovery; snapshot thủ công **trước mỗi lần chạy migration**.
- Rollback ứng dụng: giữ jar cũ (`oneshop.jar.prev`), `systemctl restart`; vì `ddl-auto=validate` và migration chỉ thêm, jar cũ chạy được trên schema mới trừ khi migration đã làm thay đổi ràng buộc mà jar cũ không biết.
- Rollback schema: restore snapshot/PITR về trước migration. Hai script hạ cấp có phê duyệt riêng ở `database/migrations/rollback/` (R001 reviews, R002 refund ledger) chỉ dùng khi có quyết định rõ ràng.

## 12. Còn phải làm khi deploy thật

1. Tạo VPC/subnet/SG, RDS, EC2 (hoặc EB/ECS), ALB + ACM, DNS.
2. Tạo trust store RDS, user DB riêng cho ứng dụng, lưu bí mật vào SSM/Secrets Manager.
3. Chạy khởi tạo database (§4), kiểm tra bằng smoke test (§8).
4. Cấu hình SMTP thật nếu muốn quên mật khẩu (nếu `MAIL_HOST` trống thì tính năng tắt, người dùng được thông báo).
5. Giám sát: CloudWatch Logs (stdout của systemd), cảnh báo CPU/RAM/ổ đĩa, `ERROR` trong log, alarm Target unhealthy.
6. Đo tải để chọn kích thước instance; quyết định Multi-AZ và chi phí.
