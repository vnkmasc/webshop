# Vận hành: những gì đang chạy và cách bật/tắt

Tài liệu này liệt kê **mọi thành phần chạy nền** của hệ thống demo, nằm ở máy nào, và lệnh để kiểm tra, tắt, bật lại. Mọi lệnh chạy bằng `root` trên máy tương ứng, trừ phần tải giả lập.

Địa chỉ trong môi trường demo hiện tại:

| Máy | Hostname | IP | Vai trò |
|---|---|---|---|
| Web | `svfrt` | 192.168.50.64 | nginx + giao diện |
| Ứng dụng | `svbe` | 192.168.50.65 | Backend + cổng thanh toán |
| CSDL | `svdb` | 192.168.50.66 | PostgreSQL |
| Máy điều khiển | `control-plane` | 192.168.50.46 | Tải giả lập (k6), build, deploy |

## 1. Danh sách thành phần

| Máy | Thành phần | Kiểu chạy | Tên unit | Cổng | Tự chạy khi khởi động máy |
|---|---|---|---|---|---|
| svfrt | nginx | systemd service | `nginx` | 80 | Có |
| svbe | Backend cửa hàng | systemd service (Java) | `shop-backend` | 8080, 8081 (health) | Có |
| svbe | Cổng thanh toán | **container Podman**, quản lý bằng systemd (Quadlet) | `shop-payment` (container cùng tên) | 127.0.0.1:8090 | Có |
| svdb | PostgreSQL 16 (`shop`, `payments`) | systemd service | `postgresql` | 5432 | Có |
| svdb | Backup database 02:30 hằng ngày | cron | `/etc/cron.d/shop-db-backup` | - | Có |
| control-plane | Tải giả lập k6 (10 người dùng ảo) | systemd **user** service của `cp` | `shop-load` | - | Có (linger bật) |

Không có thành phần nào khác của hệ thống chạy nền.

## 2. Kiểm tra nhanh toàn hệ thống

```bash
# Từ control-plane
for h in 192.168.50.64 192.168.50.65 192.168.50.66; do
  ssh root@$h 'echo "== $(hostname)"; systemctl is-active nginx shop-backend shop-payment postgresql 2>/dev/null | paste -sd" "'
done
systemctl --user is-active shop-load
curl -s -o /dev/null -w '%{http_code}\n' http://192.168.50.64/api/site-config    # 200 là chạy
```

## 3. Từng thành phần

### nginx (svfrt)

```bash
systemctl status nginx
systemctl stop nginx        # tắt: trang web không truy cập được
systemctl start nginx
systemctl disable --now nginx   # tắt và không tự chạy khi khởi động lại máy
systemctl enable --now nginx
```

Cấu hình: `/etc/shop/web.env`, `/etc/nginx/conf.d/shop.conf` (sinh bởi `shop-render-nginx`). Log: `/var/log/nginx/`.

### shop-backend (svbe)

```bash
systemctl status shop-backend
systemctl stop shop-backend      # nginx sẽ trả 502 cho /api
systemctl start shop-backend     # mất khoảng 40-60 giây để sẵn sàng
curl -s http://127.0.0.1:8081/actuator/health
```

Cấu hình: `/etc/shop/app.env`. Log JSON: `/var/log/shop/backend.log`; log dạng chữ: `journalctl -u shop-backend -f`.

### shop-payment (svbe, container)

Cổng thanh toán chạy trong container Podman (`localhost/shop-payment:current`, nền `ubi9/openjdk-21-runtime`). systemd quản lý container qua Quadlet: file `/etc/containers/systemd/shop-payment.container` được systemd chuyển thành service `shop-payment`, nên vẫn dùng `systemctl`.

```bash
systemctl status shop-payment
systemctl stop shop-payment      # dừng và xóa container; đơn thẻ thất bại, COD vẫn chạy
systemctl start shop-payment     # tạo lại container từ image :current
curl -s http://127.0.0.1:8090/actuator/health
podman ps                        # container shop-payment
podman logs -f shop-payment      # hoặc: journalctl -u shop-payment -f
podman images localhost/shop-payment   # :current (đang chạy), :prev (bản trước), các tag thời gian
```

Không tự chạy khi khởi động máy: Quadlet không dùng `systemctl disable`, mà phải chuyển file unit ra chỗ khác:

```bash
systemctl stop shop-payment
mv /etc/containers/systemd/shop-payment.container /root/ && systemctl daemon-reload
# bật lại:
mv /root/shop-payment.container /etc/containers/systemd/ && systemctl daemon-reload && systemctl start shop-payment
```

| File | Nội dung |
|---|---|
| `/etc/containers/systemd/shop-payment.container` | Unit Quadlet: image, cổng, volume log, biến môi trường |
| `/etc/shop/payment.env` | Biến môi trường của container, **sinh từ** `/etc/shop/app.env` bằng `shop-payment-env`. Sửa `app.env` rồi chạy `shop-payment-env && systemctl restart shop-payment` |
| `/var/log/shop-payment/payment.log` | Log JSON (thư mục mount vào container) |

### PostgreSQL (svdb)

```bash
systemctl status postgresql
systemctl stop postgresql        # toàn bộ API lỗi
systemctl start postgresql
runuser -u postgres -- psql -l
```

Cấu hình: `/etc/shop/db.env`, `/var/lib/pgsql/data/conf.d/shop.conf`. Log: `/var/lib/pgsql/data/log/`.

Backup: lịch ở `/etc/cron.d/shop-db-backup`, file ở `/var/lib/pgsql/backups/shop`. Tắt lịch backup: `mv /etc/cron.d/shop-db-backup /root/`. Chạy backup ngay: `ENV_FILE=/etc/shop/db.env /usr/local/bin/shop-db-backup`.

### Tải giả lập k6 (control-plane, user `cp`)

Chạy dưới dạng systemd user service để có lưu lượng liên tục vào shop (xem các nhóm hành vi ở [demo.md](demo.md)).

```bash
systemctl --user status shop-load
systemctl --user stop shop-load        # dừng tải
systemctl --user start shop-load
systemctl --user disable --now shop-load   # dừng hẳn, không tự chạy lại
journalctl --user -u shop-load -f
```

| File | Nội dung |
|---|---|
| `~/.config/systemd/user/shop-load.service` | Unit chạy `k6 run scripts/load/shop.js` |
| `~/.config/shop-load/env` | `BASE_URL`, `PROFILE`, `VUS` (số người dùng ảo), `DURATION`, `ADMIN_PASSWORD` (chmod 600) |
| `~/.local/bin/k6` | k6 (bản tải từ GitHub releases) |

Đổi số người dùng ảo: sửa `VUS` trong `~/.config/shop-load/env` rồi `systemctl --user restart shop-load`.

`loginctl enable-linger cp` đã được bật để service chạy cả khi `cp` không đăng nhập; tắt bằng `loginctl disable-linger cp`.

Tải giả lập liên tục tạo đơn hàng thật trong database. Nhóm quản trị trong kịch bản tự xử lý đơn và nhập thêm hàng cho size đã hết, nên có thể chạy nhiều ngày.

## 4. Tắt toàn bộ demo

```bash
systemctl --user disable --now shop-load                                 # control-plane
ssh root@192.168.50.64 'systemctl disable --now nginx'
ssh root@192.168.50.65 'systemctl disable --now shop-backend; systemctl stop shop-payment; mv /etc/containers/systemd/shop-payment.container /root/; systemctl daemon-reload'
ssh root@192.168.50.66 'systemctl disable --now postgresql; mv /etc/cron.d/shop-db-backup /root/ 2>/dev/null'
```

Bật lại theo thứ tự CSDL → ứng dụng → web → tải:

```bash
ssh root@192.168.50.66 'systemctl enable --now postgresql; mv /root/shop-db-backup /etc/cron.d/ 2>/dev/null'
ssh root@192.168.50.65 'mv /root/shop-payment.container /etc/containers/systemd/; systemctl daemon-reload; systemctl start shop-payment; systemctl enable --now shop-backend'
ssh root@192.168.50.64 'systemctl enable --now nginx'
systemctl --user enable --now shop-load
```

Dữ liệu, cấu hình và firewall không bị đụng tới khi tắt.

## 5. Thành phần cũ (logistics) đã tắt

Trước Sải Bước, các máy chạy một ứng dụng logistics. Đã tắt và tắt tự khởi động, dữ liệu vẫn còn:

| Máy | Còn lại | Ghi chú |
|---|---|---|
| svfrt | `/root/logistics-nginx-backup/logistics.conf`, `/var/www/logistics` | Không còn được nginx nạp |
| svbe | Unit `logistics-backend` (disabled), `/opt/logistics` | |
| svdb | Database `logistics`, unit `minio` (disabled), `/var/lib/minio`, `/root/logistics-db-backup.cron.disabled` | Firewall còn rule cổng 9000 từ svbe, không có gì nghe ở cổng này |
