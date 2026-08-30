# API 文档

基地址：`http://localhost:8080`  
除登录注册外，均需 Header：`Authorization: Bearer <token>`

统一响应（非 SSE）：

```json
{ "code": 0, "message": "ok", "data": {} }
```

错误时 `code` 为 HTTP 语义状态码，`message` 为原因。

## 1. 注册

`POST /api/auth/register`

```json
{ "email": "a@test.com", "phone": "13800000000", "password": "123456" }
```

邮箱、手机号至少填一项。响应：

```json
{ "code": 0, "data": { "token": "...", "user": { "id": 1, "email": "...", "phone": "..." } } }
```

## 2. 登录

`POST /api/auth/login`

```json
{ "account": "a@test.com", "password": "123456" }
```

`account` 可以是邮箱或手机号。响应同注册。

## 3. 当前用户

`GET /api/me`

响应示例：

```json
{ "code": 0, "data": { "id": 1, "email": "a@test.com", "phone": null, "role": "USER" } }
```

## 4. 会话历史查询

### 4.1 新建会话

`POST /api/sessions`

响应：

```json
{ "code": 0, "data": { "id": 1, "title": "新会话", "createdAt": "2026-08-30T15:00:00", "updatedAt": "2026-08-30T15:00:00" } }
```

### 4.2 会话列表

`GET /api/sessions`

```json
{ "code": 0, "data": [{ "id": 1, "title": "专业版怎么收费？", "updatedAt": "2026-08-30T15:01:00" }] }
```

### 4.3 会话详情（含完整问答历史）

`GET /api/sessions/{id}`

```json
{
  "code": 0,
  "data": {
    "id": 1,
    "title": "专业版怎么收费？",
    "messages": [
      {
        "id": 10,
        "role": "USER",
        "content": "专业版怎么收费？",
        "intent": "PRODUCT",
        "createdAt": "2026-08-30T15:00:10"
      },
      {
        "id": 11,
        "role": "ASSISTANT",
        "content": "专业版按年订阅，价格以产品介绍文档为准。",
        "sources": [
          { "documentName": "公司产品介绍.txt", "summary": "专业版……", "score": 0.81 }
        ],
        "suggestions": ["专业版和企业版有什么区别？"],
        "createdAt": "2026-08-30T15:00:12"
      }
    ]
  }
}
```

仅能查询当前登录用户自己的会话；管理员走 `/api/admin/sessions/{id}`。

## 5. 上传知识库文档

`POST /api/knowledge/documents`  
`Content-Type: multipart/form-data`

| 字段 | 类型 | 说明 |
|---|---|---|
| `file` | 文件 | 必填，`.txt` / `.md` / `.pdf` / `.docx` / `.doc` |
| `collection` | 字符串 | 可选：`AUTO` / `PRODUCT` / `AFTER_SALES` / `FAQ` / `GENERAL`。未传或 `AUTO` 时按文件名推断分区 |

立即返回：

```json
{
  "code": 0,
  "data": {
    "id": 3,
    "filename": "退换货政策.txt",
    "status": "PROCESSING",
    "collection": "AFTER_SALES",
    "collectionLabel": "售后"
  }
}
```

后台解析并向量化后变为 `READY` 或 `FAILED`。可用 `GET /api/knowledge/documents` 轮询状态。

## 6. 知识库列表 / 删除

- `GET /api/knowledge/documents`
- `DELETE /api/knowledge/documents/{id}`（同步删除切片向量）

## 7. 流式问答（必做）

`POST /api/chat/stream`  
`Accept: text/event-stream`

请求：

```json
{ "sessionId": 1, "question": "专业版怎么收费？" }
```

服务端使用 **SSE**，事件名如下。

| event | data | 说明 |
|---|---|---|
| `meta` | `{"intent":"AFTER_SALES","intentLabel":"售后问题","sources":[{"documentName":"...","summary":"...","score":0.81}]}` | 先返回意图与引用 |
| `token` | `{"text":"专"}` | 逐片 token |
| `done` | `{"messageId":12,"interrupted":false,"suggestions":["专业版和企业版有什么区别？"]}` | 结束；`suggestions` 为追问建议 |
| `error` | `{"message":"..."}` | LLM/超时等错误 |

原始帧示例：

```
event: meta
data: {"sources":[]}

event: token
data: {"text":"抱歉"}

event: done
data: {"messageId":12}
```

空检索时仍会 `token` 推送兜底话术，但不会调用对话模型。

约束：问题 ≤ 500 字；每用户每日提问 ≤ 100 次（可在 `application.yml` 配置）。

检索会按意图优先对应知识库分区，分区无结果再搜全库。

## 8. 反馈提交

`POST /api/messages/{id}/feedback`

请求：

```json
{ "type": "LIKE", "comment": "回答准确" }
```

`type` 为 `LIKE` 或 `DISLIKE`。同一用户对同一条助手消息重复提交会覆盖。

响应：

```json
{ "code": 0, "message": "ok", "data": { "id": 1, "type": "LIKE", "comment": "回答准确" } }
```

## 9. 管理后台（需 ADMIN）

### 9.1 概览统计

`GET /api/admin/overview?days=14`

返回会话数、提问数、今日提问、反馈统计、近 N 日问答量折线数据、意图分布。

### 9.2 全量会话

`GET /api/admin/sessions`  
`GET /api/admin/sessions/{id}`（含消息与意图标注）

### 9.3 反馈列表

`GET /api/admin/feedbacks`

### 9.4 用户账号管理

`GET /api/admin/users`：用户列表（含会话数、提问数、是否可删及原因）

`DELETE /api/admin/users/{id}`：删除用户并级联清理会话 / 消息 / 反馈

保护规则：

- 不可删除当前登录管理员
- 不可删除内置管理员 `admin@company.com`
- 不可删除系统中最后一名管理员

## 10. 意图识别与追问

流式 `meta` 含意图；`done` 含 `suggestions`。用户消息写入 `messages.intent`。会话详情里助手消息带 `sources` 与 `suggestions`。

分类：产品咨询 / 售后问题 / 闲聊 / 投诉 / 转人工 / 超出范围 / 知识问答。

实现：优先 LLM 短超时 JSON；失败则关键词规则。追问同理（LLM 失败则启发式）。
