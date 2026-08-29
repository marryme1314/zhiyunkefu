package com.bishi.cs.admin;

import com.bishi.cs.common.ApiException;
import com.bishi.cs.session.ChatMessageRepository;
import com.bishi.cs.session.ChatSessionRepository;
import com.bishi.cs.session.MessageFeedbackRepository;
import com.bishi.cs.user.AdminBootstrap;
import com.bishi.cs.user.UserAccount;
import com.bishi.cs.user.UserAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserServiceTest {
    @Mock
    UserAccountRepository users;
    @Mock
    ChatSessionRepository sessions;
    @Mock
    ChatMessageRepository messages;
    @Mock
    MessageFeedbackRepository feedbacks;

    AdminUserService service;
    UserAccount operator;
    UserAccount customer;

    @BeforeEach
    void setUp() {
        service = new AdminUserService(users, sessions, messages, feedbacks);
        operator = account(1L, "op@test.com", "ADMIN");
        customer = account(2L, "u@test.com", "USER");
    }

    @Test
    void refusesToDeleteSelf() {
        when(users.findById(1L)).thenReturn(Optional.of(operator));
        ApiException ex = assertThrows(ApiException.class, () -> service.deleteUser(operator, 1L));
        assertTrue(ex.getMessage().contains("当前登录"));
        verify(users, never()).delete(operator);
    }

    @Test
    void refusesToDeleteBuiltInAdmin() {
        UserAccount builtin = account(6L, AdminBootstrap.ADMIN_EMAIL, "ADMIN");
        when(users.findById(6L)).thenReturn(Optional.of(builtin));
        ApiException ex = assertThrows(ApiException.class, () -> service.deleteUser(operator, 6L));
        assertTrue(ex.getMessage().contains("内置"));
    }

    @Test
    void cascadeDeletesCustomerWithoutSessions() {
        when(users.findById(2L)).thenReturn(Optional.of(customer));
        when(users.countByRoleIgnoreCase("ADMIN")).thenReturn(1L);
        when(sessions.findByUserOrderByUpdatedAtDesc(customer)).thenReturn(List.of());
        when(feedbacks.countByUser(customer)).thenReturn(0L);

        var result = service.deleteUser(operator, 2L);
        assertEquals("u@test.com", result.get("account"));
        assertEquals(0, result.get("deletedSessions"));
        verify(users).delete(customer);
    }

    private static UserAccount account(Long id, String email, String role) {
        UserAccount user = new UserAccount();
        user.setId(id);
        user.setEmail(email);
        user.setRole(role);
        return user;
    }
}
