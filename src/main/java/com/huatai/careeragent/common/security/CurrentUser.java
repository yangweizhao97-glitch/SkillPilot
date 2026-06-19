package com.huatai.careeragent.common.security;

import com.huatai.careeragent.user.UserRole;

public record CurrentUser(
        Long userId,
        String email,
        UserRole role
) {
}
