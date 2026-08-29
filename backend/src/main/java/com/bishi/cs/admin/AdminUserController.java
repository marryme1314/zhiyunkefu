package com.bishi.cs.admin;

import com.bishi.cs.common.ApiResponse;
import com.bishi.cs.security.AuthUser;
import com.bishi.cs.user.AuthService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {
    private final AdminUserService userAdmin;
    private final AuthService authService;

    public AdminUserController(AdminUserService userAdmin, AuthService authService) {
        this.userAdmin = userAdmin;
        this.authService = authService;
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> list(@AuthenticationPrincipal AuthUser user) {
        return ApiResponse.ok(userAdmin.listUsers(authService.requireUser(user)));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Map<String, Object>> delete(@AuthenticationPrincipal AuthUser user,
                                                   @PathVariable Long id) {
        return ApiResponse.ok(userAdmin.deleteUser(authService.requireUser(user), id));
    }
}
