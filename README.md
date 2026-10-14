# Sải Bước - cửa hàng giày trực tuyến

Ứng dụng web bán giày gồm trang mua sắm cho khách, trang quản trị cho cửa hàng và một dịch vụ cổng thanh toán thẻ giả lập. Repo này chỉ chứa **mã nguồn ứng dụng**; cách triển khai lên từng loại hạ tầng, tải giả lập và kịch bản demo nằm ở repo `webshop-demo`.

## Kiến trúc

```
Trình duyệt ──> frontend (React, file tĩnh) ──/api──> backend (Spring Boot) ──JDBC──> PostgreSQL: shop
                                                            │
                                                            └──HTTP──> payment (Spring Boot) ──JDBC──> PostgreSQL: payments
```

| Thành phần | Công nghệ | Cổng mặc định | Ghi chú |
|---|---|---|---|
| `frontend/` | React 18, Vite, antd, TanStack Query, recharts | (file tĩnh) | Phục vụ bằng web server bất kỳ; mọi đường dẫn không phải file → `index.html`; `/api/` chuyển sang backend |
| `backend/` | Spring Boot 3.4, Java 21, JPA + JDBC, Flyway, JWT | 8080 (API), 8081 (actuator) | Database `shop` |
| `payment/` | Spring Boot 3.4, Java 21, JDBC, Flyway | 8090 (API + actuator) | Database `payments`; có `Containerfile` |
| PostgreSQL | 16 | 5432 | Hai database riêng; bảng do Flyway tạo khi khởi động |

## Tính năng

**Trang khách hàng** (`/`)
- Trang chủ, danh mục, tìm kiếm không cần gõ dấu, lọc theo size, giá, thương hiệu, đối tượng.
- Trang sản phẩm có chọn size và báo tồn kho theo từng size.
- Giỏ hàng, mã giảm giá, phí giao hàng (miễn phí từ 1.000.000₫).
- Đặt hàng không cần tài khoản; thanh toán khi nhận hàng hoặc bằng thẻ.
- Tài khoản: lịch sử đơn, hủy đơn đang chờ, tra cứu đơn bằng mã và số điện thoại.

**Trang quản trị** (`/admin`)
- Tổng quan: doanh thu, số đơn, tỷ lệ thanh toán thẻ thất bại, biểu đồ theo ngày, bán chạy, sắp hết hàng.
- Đơn hàng: xác nhận, giao, hoàn tất, hủy (hoàn tiền thẻ tự động).
- Sản phẩm và tồn kho theo size, khách hàng, mã giảm giá.
- Cổng thanh toán: chỉnh độ trễ, tỷ lệ lỗi, tỷ lệ từ chối, ngừng hoạt động.
- Mô phỏng sự cố (khi bật `APP_SIMULATION_ENABLED`).

**Quy tắc chính**
- Đặt hàng khóa tồn kho theo thứ tự (không bán quá số lượng), thanh toán thẻ gọi cổng thanh toán **ngoài** transaction; thanh toán thất bại hoặc cổng không phản hồi (timeout 8 giây) thì đơn chuyển "Thanh toán thất bại" và hoàn kho, hoàn lượt mã giảm giá.
- Vòng đời đơn: Chờ xác nhận → Đã xác nhận → Đang giao → Đã giao; hủy được ở hai bước đầu. COD được tính đã thanh toán khi giao xong.

## Cấu hình (biến môi trường)

### backend

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `local` | `prod`: log JSON (ECS) ra file `LOG_FILE`, tắt dữ liệu mẫu và mô phỏng mặc định |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` | `localhost`, `5432`, `shop`, `shop`, - | Kết nối PostgreSQL |
| `DB_POOL_SIZE` | `20` | Kích thước connection pool |
| `JWT_SECRET` | - (bắt buộc, ≥ 32 byte) | Ký token đăng nhập |
| `PAYMENT_URL`, `PAYMENT_API_KEY` | `http://127.0.0.1:8090`, - | Địa chỉ và khóa dùng chung với dịch vụ payment |
| `PAYMENT_READ_TIMEOUT` | `PT8S` | Thời gian chờ cổng thanh toán |
| `APP_ADMIN_EMAIL`, `APP_ADMIN_PASSWORD` | `admin@saibuoc.vn`, - | Tài khoản quản trị tạo ở lần khởi động đầu |
| `APP_SEED_ENABLED` | `false` (`true` ở profile `local`) | Nạp dữ liệu mẫu khi database trống: 96 sản phẩm, ~250 khách, 90 ngày đơn hàng, mã giảm giá |
| `APP_SIMULATION_ENABLED` | `false` (`true` ở profile `local`) | Bật API và trang mô phỏng sự cố |
| `APP_TRUSTED_PROXY` | IP nội bộ | Regex địa chỉ reverse proxy được tin cho `X-Forwarded-*` |
| `APP_RELEASE_VERSION`, `APP_RELEASE_STAGE` | `dev`, `local` | Hiện ở `/actuator/info` |
| `BACKEND_PORT`, `MANAGEMENT_PORT` | `8080`, `8081` | |
| `LOG_FILE` | `/var/log/shop/backend.log` | Chỉ với profile `prod` |

### payment

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DB_HOST`, `DB_PORT` | `localhost`, `5432` | |
| `PAYMENT_DB_NAME`, `PAYMENT_DB_USER`, `PAYMENT_DB_PASSWORD` | `payments`, `payments`, - | |
| `PAYMENT_API_KEY` | - | Nếu đặt, mọi request `/api/**` phải có `Authorization: Bearer <key>` |
| `PAYMENT_BIND_ADDRESS`, `PAYMENT_PORT` | `127.0.0.1`, `8090` | Trong container đặt `0.0.0.0` |
| `PAYMENT_LOG_FILE` | `/var/log/shop/payment.log` | Chỉ với profile `prod` |
| `APP_RELEASE_VERSION`, `APP_RELEASE_STAGE` | `dev`, `local` | |

Container: `JAVA_OPTS_APPEND` cho tham số JVM (image nền `ubi9/openjdk-21-runtime`).

## Tài khoản và dữ liệu mẫu

| Email | Mật khẩu | Vai trò |
|---|---|---|
| `admin@saibuoc.vn` | `APP_ADMIN_PASSWORD` (khi trống và bật dữ liệu mẫu: `SaiBuoc@2026`) | Quản trị |
| `khach@saibuoc.vn` | `SaiBuoc@2026` | Khách hàng có sẵn lịch sử đơn |
| `load01..20@saibuoc.vn` | `SaiBuoc@2026` | Dành cho công cụ tạo tải |

Thẻ thử: `4111 1111 1111 1111` thành công, `4000 0000 0000 0002` bị từ chối, `4000 0000 0000 9995` không đủ số dư (hạn bất kỳ trong tương lai, CVV 3 số).

## Mô phỏng sự cố (tính năng)

Khi `APP_SIMULATION_ENABLED=true`, quản trị viên bật/tắt các kịch bản tại **Quản trị → Mô phỏng sự cố** hoặc qua API; mỗi kịch bản tự tắt khi hết thời lượng và không sửa dữ liệu bán hàng.

| Kịch bản | Tác động |
|---|---|
| `LATENCY` | Độ trễ ngẫu nhiên cho một nhóm API (`minMs`, `maxMs`, `target`) |
| `HTTP_ERROR` | Một tỷ lệ request ném exception, HTTP 500 (`ratePercent`, `target`) |
| `SLOW_SQL` | Danh sách/tìm kiếm sản phẩm chạy thêm câu SQL `pg_sleep` (`sleepMs`) |
| `N_PLUS_ONE` | Danh sách sản phẩm truy vấn size từng sản phẩm một |
| `POOL_EXHAUSTION` | Request giữ kết nối DB lâu (`holdMs`, `ratePercent`, `target`) |
| `HIGH_CPU` | Trang sản phẩm tốn CPU khi tính gợi ý (`cpuMs`) |
| `MEMORY_LEAK` | Mỗi request giữ lại bộ nhớ, có giới hạn (`kbPerRequest`, `capMb`) |
| `FRONTEND_ERROR` | Nút "Thêm vào giỏ" ném `TypeError` trong trình duyệt |
| `PAYMENT_BUSINESS_ERROR` | Cổng thanh toán trả HTTP 200 với `responseCode` khác `00` trong body và header `X-Response-Code` (`code` 99/68/12, `percent`); backend coi là thanh toán thất bại |
| `ODD_STATUS` | Một tỷ lệ request trả 423/402/451/429 kèm body `error`, `message`; 423 và 429 có `Retry-After: 30` (`code`, `percent`, `target`) |
| `READINESS_FAIL` | `/actuator/health/readiness` báo DOWN, liveness vẫn UP |
| `EMIT_PII` | Thanh toán thẻ đính dữ liệu giả cố định (thẻ test `4111111111111111`, `demo@example.com`, CVV `000`) vào URL, header của lệnh gọi sang payment và vào log |
| `METRIC_CARDINALITY` | Counter `shop.request.tagged` có tag duy nhất theo request, tối đa 5.000 series, xoá hết khi tắt (`perRequest`, `maxSeries`) |

Nhóm API (`target`): `ALL`, `CATALOG`, `CART`, `CHECKOUT`, `ACCOUNT`, `ADMIN`.

```
GET  /api/admin/simulation
PUT  /api/admin/simulation/{SCENARIO}   {"active": true, "params": {...}, "durationMinutes": 15}
POST /api/admin/simulation/release-memory
GET  /api/admin/payment-gateway        PUT cùng đường dẫn để đổi hành vi cổng thanh toán
```

## Quan sát

- Mọi response có `X-Request-Id`; backend chuyển tiếp header này sang payment. Log có `requestId`.
- Actuator (cổng quản trị): `/actuator/health` (kèm `liveness`, `readiness`), `/actuator/info`, `/actuator/prometheus` không cần đăng nhập; `/actuator/metrics` cần token quản trị.
- Payment: mọi kết quả charge có `responseCode` (`00` = đã xử lý) trong body và header `X-Response-Code`.
- Metric nghiệp vụ: `shop.orders.placed{payment}`, `shop.order.value`, `shop.payments{result}` (`approved`, `declined`, `business_error`, `gateway_error`), `gateway.charges{result}`, `gateway.charge.duration`.

## Build và chạy

```bash
# Kiểm thử
(cd backend && ./mvnw test) && (cd payment && ./mvnw test) && (cd frontend && npm ci && npm test)

# Build
(cd backend && ./mvnw -q package -DskipTests)    # backend/target/shop-backend.jar
(cd payment && ./mvnw -q package -DskipTests)    # payment/target/payment-gateway.jar
(cd frontend && npm ci && npm run build)          # frontend/dist/
docker build -t shop-payment:dev -f payment/Containerfile payment/target

# Chạy cục bộ (PostgreSQL có sẵn database shop và payments)
(cd payment && PAYMENT_DB_PASSWORD=... ./mvnw spring-boot:run)
(cd backend && DB_PASSWORD=... JWT_SECRET=$(openssl rand -base64 48) ./mvnw spring-boot:run)
(cd frontend && VITE_DEV_PROXY_TARGET=http://localhost:8080 npm run dev)
```

Ảnh sản phẩm (`frontend/public/images/shoes/*.svg`) được sinh bởi `node scripts/gen-shoe-images.mjs`.
