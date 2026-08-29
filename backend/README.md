# 后端

Spring Boot 3 / Java 17。表结构由 Flyway 迁移（`src/main/resources/db/migration`）。

## 依赖

- JDK 17、Maven 3.9+
- MySQL 8（默认 Docker 端口 3307，库 `ai_cs`）
- 对话：Moonshot API **或** 本机 Ollama
- 向量：优先 Ollama `nomic-embed-text`，不可用则本机词法向量

## 配置

仓库根目录复制 `.env.example` → `.env`，至少配置：

- `MYSQL_*` / `JWT_SECRET`
- `LLM_PROVIDER=moonshot` 时设置 `MOONSHOT_API_KEY`
- `EMBED_PROVIDER=auto`（推荐）

## 启动

```bash
cd backend
mvn spring-boot:run
```

Windows 也可双击根目录 `启动后端.bat`。

## 测试

```bash
mvn test
```

种子知识库在 `src/main/resources/seed/`，首次启动自动导入。
