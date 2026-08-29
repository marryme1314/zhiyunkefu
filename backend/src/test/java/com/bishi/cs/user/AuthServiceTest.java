package com.bishi.cs.user;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.security.JwtService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {
    @Mock
    UserAccountRepository users;
    @Mock
    PasswordEncoder encoder;
    @Mock
    JwtService jwtService;
    @InjectMocks
    AuthService authService;

    @Test
    void registerRequiresEmailOrPhone() {
        ApiException ex = assertThrows(ApiException.class, () ->
                authService.register(new AuthService.RegisterRequest("  ", "", "123456")));
        assertTrue(ex.getMessage().contains("邮箱或手机号"));
    }
}
