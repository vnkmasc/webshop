package vn.saibuoc.shop.auth;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import vn.saibuoc.shop.common.ApiResponse;
import vn.saibuoc.shop.security.SecurityUtils;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final AuthService auth;

    @PostMapping("/api/auth/login")
    public ApiResponse<AuthService.TokenResponse> login(@Valid @RequestBody AuthService.LoginRequest req) {
        return ApiResponse.ok(auth.login(req));
    }

    @PostMapping("/api/auth/register")
    public ApiResponse<AuthService.TokenResponse> register(@Valid @RequestBody AuthService.RegisterRequest req) {
        return ApiResponse.ok(auth.register(req), "Đăng ký thành công");
    }

    @GetMapping("/api/auth/me")
    public ApiResponse<AuthService.UserDto> me() {
        return ApiResponse.ok(auth.me(SecurityUtils.currentUser()));
    }

    @PutMapping("/api/account/profile")
    public ApiResponse<AuthService.UserDto> profile(@Valid @RequestBody AuthService.ProfileRequest req) {
        return ApiResponse.ok(auth.updateProfile(SecurityUtils.currentUser(), req), "Đã lưu thông tin");
    }
}
