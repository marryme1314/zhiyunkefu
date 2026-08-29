package com.bishi.cs.admin;

import com.bishi.cs.common.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminDashboardController {
    private final AdminDashboardService dashboard;

    public AdminDashboardController(AdminDashboardService dashboard) {
        this.dashboard = dashboard;
    }

    @GetMapping("/overview")
    public ApiResponse<Map<String, Object>> overview(@RequestParam(defaultValue = "14") int days) {
        return ApiResponse.ok(dashboard.overview(days));
    }

    @GetMapping("/sessions")
    public ApiResponse<List<Map<String, Object>>> sessions() {
        return ApiResponse.ok(dashboard.listSessions());
    }

    @GetMapping("/sessions/{id}")
    public ApiResponse<Map<String, Object>> sessionDetail(@PathVariable Long id) {
        return ApiResponse.ok(dashboard.sessionDetail(id));
    }
}
