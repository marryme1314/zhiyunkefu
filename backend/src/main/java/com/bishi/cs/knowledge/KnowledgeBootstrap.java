package com.bishi.cs.knowledge;

import com.bishi.cs.config.AppProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBootstrap implements ApplicationRunner {
    private final KnowledgeService knowledgeService;
    private final AppProperties props;

    public KnowledgeBootstrap(KnowledgeService knowledgeService, AppProperties props) {
        this.knowledgeService = knowledgeService;
        this.props = props;
    }

    @Override
    public void run(ApplicationArguments args) {
        System.out.println("[INFO] 对话=" + props.getLlm().getProvider()
                + " 向量=" + knowledgeService.activeEmbedName());
        try {
            knowledgeService.importSeedIfEmpty();
            knowledgeService.reembedIfDimensionMismatch();
        } catch (Exception e) {
            System.err.println("[WARN] 启动时未能自动向量化示例知识库: " + e.getMessage());
            System.err.println("[WARN] 本机模式请先运行 启动Ollama.bat");
        }
    }
}
