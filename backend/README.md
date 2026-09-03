# 后端

Spring Boot 3 / Java 17。表结构由 Flyway 迁移（`src/main/resources/db/migration`）。

## 依赖

- JDK 17、Maven 3.9+
- MySQL 8（默认 Docker 端口 3307，库 `ai_cs`）
- 对话：Moonshot API **或** 本机 Ollama
- 向量：OpenAI 兼容 Embedding API / Ollama `nomic-embed-text` / 本机词法回退
- 向量库：生产 Qdrant；开发可仅用 MySQL + 内存余弦

生产启动请使用 `SPRING_PROFILES_ACTIVE=prod`（Docker Compose 已设置），并配置足够强的 `JWT_SECRET` 与 `ADMIN_PASSWORD`。

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
