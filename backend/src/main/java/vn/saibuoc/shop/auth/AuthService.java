package vn.saibuoc.shop.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.saibuoc.shop.common.BusinessException;
import vn.saibuoc.shop.security.CurrentUser;
import vn.saibuoc.shop.security.JwtService;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final Clock clock;

    public record LoginRequest(@NotBlank @Email String email, @NotBlank String password) {
    }

    public record RegisterRequest(
            @NotBlank @Email @Size(max = 160) String email,
            @NotBlank @Size(min = 8, max = 72, message = "Mật khẩu từ 8 đến 72 ký tự") String password,
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0") String phone) {
    }

    public record ProfileRequest(
            @NotBlank @Size(max = 120) String fullName,
            @Pattern(regexp = "^$|^0\\d{9}$", message = "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0") String phone,
            @Size(max = 255) String address,
            @Size(max = 80) String province) {
    }

    public record UserDto(long id, String email, String fullName, String phone, String address, String province,
                          String role) {
        static UserDto of(User u) {
            return new UserDto(u.getId(), u.getEmail(), u.getFullName(), u.getPhone(), u.getAddress(), u.getProvince(),
                    u.getRole());
        }
    }

    public record TokenResponse(String accessToken, long expiresIn, UserDto user) {
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User u = users.findByEmail(req.email().trim())
                .filter(User::isActive)
                .filter(x -> passwordEncoder.matches(req.password(), x.getPasswordHash()))
                .orElseThrow(() -> {
                    log.info("Failed login for {}", req.email());
                    return new BusinessException(HttpStatus.UNAUTHORIZED, "BAD_CREDENTIALS", "Email hoặc mật khẩu không đúng");
                });
        u.setLastLoginAt(clock.instant());
        return token(u);
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        String email = req.email().trim().toLowerCase();
        if (users.existsByEmail(email)) {
            throw BusinessException.conflict("EMAIL_TAKEN", "Email này đã được đăng ký");
        }
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(req.password()));
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setRole(User.CUSTOMER);
        u.setCreatedAt(clock.instant());
        u.setLastLoginAt(clock.instant());
        users.save(u);
        log.info("New customer registered id={}", u.getId());
        return token(u);
    }

    @Transactional(readOnly = true)
    public UserDto me(CurrentUser cu) {
        return UserDto.of(users.findById(cu.id()).orElseThrow(() -> BusinessException.notFound("tài khoản")));
    }

    @Transactional
    public UserDto updateProfile(CurrentUser cu, ProfileRequest req) {
        User u = users.findById(cu.id()).orElseThrow(() -> BusinessException.notFound("tài khoản"));
        u.setFullName(req.fullName().trim());
        u.setPhone(blankToNull(req.phone()));
        u.setAddress(blankToNull(req.address()));
        u.setProvince(blankToNull(req.province()));
        return UserDto.of(u);
    }

    private TokenResponse token(User u) {
        CurrentUser cu = new CurrentUser(u.getId(), u.getEmail(), u.getFullName(), u.getRole());
        return new TokenResponse(jwtService.issue(cu), jwtService.ttlSeconds(), UserDto.of(u));
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
