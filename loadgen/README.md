# loadgen - tải giả lập (k6)

Tạo lưu lượng giống người dùng thật vào cửa hàng: xem hàng, tìm kiếm, mua hàng, tra cứu đơn, xử lý đơn ở trang quản trị. Chỉ cần URL của cửa hàng, không phụ thuộc hạ tầng hay hệ thống giám sát nào. Không dùng trên hệ thống có dữ liệu thật.

Ứng dụng phải chạy với `APP_SEED_ENABLED=true` (tài khoản `loadNN@saibuoc.vn`, sản phẩm). Mọi đơn do tải tạo có ghi chú `k6` để phân biệt và dọn.

| File | Nội dung |
|---|---|
| `shop.js` | Kịch bản k6 |
| `run.sh` | Chạy một lần bằng k6 cài trên máy |
| `Containerfile` | Image `shop-loadgen`: k6 2.3.0 + `shop.js` |
| `k8s/` | Chạy liên tục trên Kubernetes: `shop-loadgen.yaml` (namespace, Deployment 2 pod), `config.yaml` (mức tải), `deploy.sh` |
| `systemd/` | Chạy liên tục bằng systemd (hệ thống hoặc user) trên một máy có k6 |

## Hành vi

Hai luồng chạy song song.

**Người dùng ảo** (`VUS` người, mỗi người lặp lại một trong các nhóm):

| Nhóm | Tỷ lệ | Hành vi |
|---|---|---|
| Xem hàng | phần còn lại | Trang chủ → danh mục / tìm kiếm / lọc size → 1-3 trang sản phẩm |
| Mua hàng | `BUY_PERCENT` (25) | Xem hàng → báo giá giỏ (đôi khi có mã giảm giá) → 70% đặt hàng: COD hoặc thẻ (một số thẻ bị từ chối) |
| Khách quen | `CUSTOMER_PERCENT` (10) | Đăng nhập `loadNN@saibuoc.vn` → lịch sử đơn → chi tiết đơn |
| Tra cứu | `TRACK_PERCENT` (5) | Tra cứu đơn bằng mã và số điện thoại |
| Quản trị | `ADMIN_PERCENT` (5) | Tổng quan → xác nhận / giao / hoàn tất đơn → nhập thêm hàng cho size đã hết |

Luồng này chủ yếu xem và tìm sản phẩm: đặt hàng chỉ chiếm vài phần trăm số request.

**Đặt hàng đều đặn**: `ORDERS_PER_MIN` lượt mỗi phút (0 = tắt), không phụ thuộc người dùng ảo. Mỗi lượt: xem sản phẩm → báo giá giỏ → đặt hàng (`CARD_PERCENT`% trả bằng thẻ, 30% đặt khi đã đăng nhập) → tra cứu lại đơn vừa đặt. Đây là luồng giữ cho thanh toán, chấm điểm rủi ro và hàng đợi luôn có việc; muốn dồn tải vào đặt hàng thì chỉ tăng số này.

## Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `BASE_URL` | `http://localhost` | URL cửa hàng |
| `ADMIN_PASSWORD` | | `APP_ADMIN_PASSWORD` của backend (nhóm quản trị) |
| `PROFILE` | `smoke` | `smoke` (1 người, 1 phút), `steady` (`VUS` người trong `DURATION`), `spike` (tăng lên 3×`VUS` rồi giảm) |
| `VUS`, `DURATION` | `10`, `30m` | Số người dùng ảo, thời lượng |
| `BUY_PERCENT`, `CUSTOMER_PERCENT`, `TRACK_PERCENT`, `ADMIN_PERCENT` | 25, 10, 5, 5 | Tỷ lệ các nhóm |
| `ORDERS_PER_MIN` | `6` | Số lượt đặt hàng đều đặn mỗi phút |
| `CARD_PERCENT` | `50` | Tỷ lệ đơn trả bằng thẻ trong luồng đặt hàng đều đặn |
| `CUSTOMER_PASSWORD` | `SaiBuoc@2026` | Mật khẩu tài khoản `loadNN` |

Chỉ số riêng của k6: `orders_placed`, `orders_payment_failed`, `checkout_duration`.

## Chạy một lần

Chạy từ một máy riêng, không đặt trên máy của cửa hàng.

```bash
ADMIN_PASSWORD='...' ./run.sh http://shop.example.com smoke          # 1 người dùng, 1 phút
ADMIN_PASSWORD='...' ./run.sh http://shop.example.com steady 10 2h    # 10 người dùng, 2 giờ
ADMIN_PASSWORD='...' ./run.sh http://shop.example.com spike 10        # tăng đột biến lên 30
```

## Chạy liên tục trên Kubernetes

1. **Build và đẩy image** lên registry mà các node kéo được (đăng nhập registry trước):

   ```bash
   scripts/build-images.sh -p registry.example.com/shop loadgen   # -> registry.example.com/shop/shop-loadgen:<commit>
   ```

2. **Triển khai** (cần `kubectl` trỏ vào cụm):

   ```bash
   ADMIN_PASSWORD='...' loadgen/k8s/deploy.sh http://shop.example.com registry.example.com/shop/shop-loadgen:<commit>
   ```

   Deployment có 2 pod, rải mỗi node một pod (`topologySpreadConstraints`). Node có taint (thường là control-plane) không nhận pod. Registry là project riêng tư thì thêm `imagePullSecrets` vào Deployment.

3. **Đổi mức tải**: giá trị trong ConfigMap `shop-loadgen` là **cho mỗi pod**, tổng tải bằng giá trị × số pod.

   ```bash
   kubectl -n shop-loadgen patch cm shop-loadgen --type merge -p '{"data":{"VUS":"5","BUY_PERCENT":"25","ORDERS_PER_MIN":"5"}}'
   kubectl -n shop-loadgen rollout restart deploy/shop-loadgen
   ```

   `deploy.sh` chỉ tạo ConfigMap khi chưa có, nên mức tải đã chỉnh được giữ qua các lần triển khai lại.

| Mức (2 pod) | Mỗi pod | Tổng | Đơn hàng |
|---|---|---|---|
| Nền | `VUS=1 BUY_PERCENT=2 ORDERS_PER_MIN=1` | 2 người dùng ảo, 2 đơn/phút | ~130/giờ |
| Demo | `VUS=5 BUY_PERCENT=25 ORDERS_PER_MIN=5` | 10 người dùng ảo, 10 đơn/phút | ~1000/giờ |

```bash
kubectl -n shop-loadgen get pods -o wide                        # trạng thái, pod nằm trên node nào
kubectl -n shop-loadgen logs -f deploy/shop-loadgen             # chỉ in lỗi (k6 chạy --quiet)
kubectl -n shop-loadgen scale deploy/shop-loadgen --replicas=0  # dừng tải (=2 để chạy lại)
kubectl delete ns shop-loadgen                                  # gỡ hẳn
```

Sửa `shop.js` thì build lại image (tag mới theo commit) và chạy lại `deploy.sh` với tag đó.

## Chạy liên tục bằng systemd

Trên một máy có k6: `systemd/shop-load.service` (systemd hệ thống) hoặc `systemd/shop-load.user.service` (systemd của một user, không cần root; cấu hình theo `systemd/shop-load.env.example`). Hướng dẫn cài nằm trong từng file. Không chạy cùng lúc với bản trên Kubernetes, tải sẽ bị cộng dồn.
