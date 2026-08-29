package com.bishi.cs.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FollowUpSuggesterTest {
    private final FollowUpSuggester suggester = new FollowUpSuggester(null, new ObjectMapper());

    @Test
    void heuristicReturnsUpToThreeRelatedQuestions() {
        List<String> tips = suggester.suggestByHeuristic("专业版怎么收费？", List.of(
                new RetrievedChunk(1L, "公司产品介绍.txt", "专业版每月 99 元", 0.8)
        ));
        assertFalse(tips.isEmpty());
        assertTrue(tips.size() <= 3);
        assertTrue(tips.stream().noneMatch(s -> s.equals("专业版怎么收费？")));
    }
}
