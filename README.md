# 智云客服

企业知识库智能客服：注册登录、多轮对话、文档知识库、RAG 检索增强、SSE 流式回答、引用来源、管理后台。

仓库：https://github.com/marryme1314/zhiyunkefu

## 别人克隆后怎么跑（推荐 Docker）

### 1. 环境准备

| 依赖 | 说明 |
|---|---|
| Docker Desktop | 必须 |
| JDK 17 + Maven 3.9+ | 本机打包后端 jar |
| Node.js 18+（含 npm） | 本机打包前端 |
| 月之暗面 API Key **或** 本机 Ollama | 至少一种能对话 |
| Ollama + `nomic-embed-text`（建议） | 语义检索；没有也能跑（词法回退，效果变弱） |

### 2. 配置

```bash
git clone https://github.com/marryme1314/zhiyunkefu.git
cd zhiyunkefu
cp .env.example .env
```

编辑 `.env`（**必改/必填**）：

1. `MOONSHOT_API_KEY=你的Key`  
   - 若没有 Key：设 `LLM_PROVIDER=ollama`，并安装 Ollama，执行 `ollama pull qwen2`
2. 建议启动 Ollama 后执行：`ollama pull nomic-embed-text`（Windows 也可双击 `启动Ollama.bat`）
3. 首次部署保持 `ADMIN_RESET_PASSWORD=true`；能登录后改回 `false`

`.env.example` 里已带可用的演示用 `JWT_SECRET` / `ADMIN_PASSWORD`，可直接用；上线前请自行更换。

### 3. 一键部署

**Windows：** 双击 `一键部署.bat`  

**命令行（Windows / macOS / Linux）：**

```bash
# 必须先在本机打出产物，再 build 镜像（国内环境默认走 DaoCloud 基础镜像）
cd backend && mvn -DskipTests package && cd ..
cd frontend && npm ci && npm run build && cd ..
docker compose up -d --build
```

打开：

- 网站：http://localhost  
- 健康检查：http://localhost/api/health  
  期望大致为：`mysql=UP`、`qdrant=UP`；有 Ollama 时 `embed=ollama`

管理员：`.env` 中的 `ADMIN_EMAIL` / `ADMIN_PASSWORD`  
（示例默认：`admin@company.com` / `SetAStrongAdminPass1!`）

### 4. 自测问题

- 专业版多少钱？
- 忘记密码怎么办？
- 订阅 7 天内可以退款吗？
- 今天股市怎么样？（应兜底，不编造）

---

## 本机开发（不走 Docker 全栈）

1. 复制 `.env.example` → `.env`，填 `MOONSHOT_API_KEY`（或改用 Ollama）
2. 起 MySQL：Windows 双击 `初始化数据库.bat`（端口 **3307**），或自行建库 `ai_cs`
3. 后端：`cd backend && mvn spring-boot:run`（**不要**开 `prod` profile）
4. 前端：`cd frontend && npm install && npm run dev` → http://localhost:5173  
5. 开发模式可不启 Qdrant；未配置时走 MySQL 向量 + 内存余弦

本地开发若未改管理员密码，首次创建一般为 `admin@company.com` / `.env` 里的 `ADMIN_PASSWORD`。

---

## 常见坑

| 现象 | 原因 / 处理 |
|---|---|
| `docker compose up --build` 报找不到 `target/*.jar` 或 `dist` | 先执行 `mvn package` 与 `npm run build`，或用 `一键部署.bat` |
| prod 启动报 JWT / 管理员口令非法 | 不要用 `replace-this` 开头的 JWT，不要用 `Admin123!` |
| prod 报未设置 MOONSHOT_API_KEY | 填 Key，或改 `LLM_PROVIDER=ollama` |
| `embed=local` | 本机未开 Ollama 或未拉 `nomic-embed-text`；功能可用，近义检索变弱 |
| 登录密码不对 | 首次请 `ADMIN_RESET_PASSWORD=true` 后重启 backend，登录成功再改回 `false` |
| 80 / 8080 / 3307 端口占用 | 先关掉占用进程或改 `docker-compose.yml` 端口映射 |

---

## 目录

| 路径 | 说明 |
|---|---|
| `backend/` | Spring Boot 3 / Java 17 |
| `frontend/` | Vue 3 + TypeScript + Vite |
| `docs/` | API、架构、库表、业务流程与截图 |
| `项目说明.md` | 技术选型与实现说明 |
| `运行指南.md` | 更细的运行说明 |
| `.env.example` | 环境变量模板（不要提交真实 `.env`） |

## 技术说明

- 对话：Moonshot（默认）或 Ollama  
- Embedding：`auto` → OpenAI 兼容 API → Ollama → 词法回退  
- 向量库：Compose 内 **Qdrant**；挂了回退 MySQL 内存余弦  
- Redis：登录失败限流  

## 测试

```bash
cd backend && mvn test
```

## 安全

仓库忽略真实 `.env`、本地 `tools/` 大文件、`node_modules`、`target`。密钥只放在你自己的 `.env`。
