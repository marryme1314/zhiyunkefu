package com.bishi.cs.health;

import com.bishi.cs.common.ApiResponse;
import com.bishi.cs.llm.LlmGateway;
import com.bishi.cs.rag.QdrantClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {
    private final DataSource dataSource;
    private final LlmGateway llm;
    private final QdrantClient qdrant;

    public HealthController(DataSource dataSource, LlmGateway llm, QdrantClient qdrant) {
        this.dataSource = dataSource;
        this.llm = llm;
        this.qdrant = qdrant;
    }

    @GetMapping("/health")
    public ApiResponse<Map<String, Object>> health() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("status", "UP");
        data.put("mysql", pingMysql() ? "UP" : "DOWN");
        data.put("embed", llm.peekEmbedBackend());
        data.put("qdrant", qdrant.enabled() ? (qdrant.healthy() ? "UP" : "DOWN") : "DISABLED");
        return ApiResponse.ok(data);
    }

    private boolean pingMysql() {
        try (Connection ignored = dataSource.getConnection()) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
