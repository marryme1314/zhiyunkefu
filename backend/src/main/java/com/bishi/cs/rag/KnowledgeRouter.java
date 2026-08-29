package com.bishi.cs.rag;

import java.util.Locale;
import java.util.Set;

/**
 * Lightweight multi-KB routing: documents belong to PRODUCT / AFTER_SALES / FAQ / GENERAL.
 * Retrieval prefers the collection that matches intent, then falls back to the full corpus.
 */
public final class KnowledgeRouter {
    public static final String PRODUCT = "PRODUCT";
    public static final String AFTER_SALES = "AFTER_SALES";
    public static final String FAQ = "FAQ";
    public static final String GENERAL = "GENERAL";

    private KnowledgeRouter() {
    }

    public static String normalize(String raw) {
        if (raw == null || raw.isBlank()) {
            return GENERAL;
        }
        String code = raw.trim().toUpperCase(Locale.ROOT);
        if (PRODUCT.equals(code) || AFTER_SALES.equals(code) || FAQ.equals(code) || GENERAL.equals(code)) {
            return code;
        }
        return GENERAL;
    }

    public static String infer(String filename) {
        String n = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (n.contains("产品") || n.contains("套餐") || n.contains("定价") || n.contains("product")) {
            return PRODUCT;
        }
        if (n.contains("退换") || n.contains("售后") || n.contains("退款") || n.contains("政策")) {
            return AFTER_SALES;
        }
        if (n.contains("faq") || n.contains("常见问题") || n.contains("账号")) {
            return FAQ;
        }
        return GENERAL;
    }

    public static String label(String collection) {
        return switch (normalize(collection)) {
            case PRODUCT -> "产品";
            case AFTER_SALES -> "售后政策";
            case FAQ -> "常见问题";
            default -> "通用";
        };
    }

    public static Set<String> preferred(IntentRecognizer.Intent intent) {
        if (intent == null) {
            return Set.of();
        }
        return switch (intent) {
            case PRODUCT_INQUIRY -> Set.of(PRODUCT, FAQ);
            case AFTER_SALES, COMPLAINT -> Set.of(AFTER_SALES, FAQ);
            default -> Set.of();
        };
    }
}
