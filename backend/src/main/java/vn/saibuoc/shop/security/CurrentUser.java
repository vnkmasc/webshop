package vn.saibuoc.shop.security;

/** Authenticated principal rebuilt from the access token (no DB lookup per request). */
public record CurrentUser(long id, String email, String name, String role) {

    public boolean isAdmin() {
        return "ADMIN".equals(role);
    }
}
