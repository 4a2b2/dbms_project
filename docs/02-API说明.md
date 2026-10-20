# API 说明（阶段一）

所有 API 以 `/api` 开头，请求与响应均为 JSON（`Content-Type: application/json`）。
错误统一返回 `{"error": "错误信息"}`。

| HTTP 状态码 | 含义 | 触发场景 |
|---|---|---|
| 200 | 成功 | 正常业务 |
| 400 | 参数/校验错误 | `@Valid` 失败、题型约束不满足、必答题未答、跳转规则非法 |
| 401 | 未登录或登录失效 | 缺少/无效 token、需登录的问卷匿名访问 |
| 403 | 无权访问 | 操作他人问卷 |
| 404 | 资源不存在 | 问卷/答卷不存在 |
| 409 | 冲突 | 用户名已注册、已提交过且不允许重复提交 |
| 500 | 服务器内部错误 | 未预期异常 |

## 1. 认证

### POST /api/auth/register
注册并直接返回 token。

请求：`{"username": "alice", "password": "Test1234!"}`（用户名 3-32 位，密码 6-64 位）

响应 200：`{"token": "<jwt>", "userId": "66f...", "username": "alice"}`

错误：400 长度不合规；409 `用户名已被注册`

### POST /api/auth/login
请求：`{"username": "alice", "password": "Test1234!"}`

响应 200：同注册。错误：401 `用户名或密码错误`

### 认证方式
除 `/api/auth/**` 外的接口需带请求头：`Authorization: Bearer <token>`。
token 无效时拦截器直接返回 401 `{"error":"登录状态无效或已过期"}`；不带 token 的请求会放行到具体接口，由接口自身决定是否要求登录（如 `/api/surveys` 一律要求，填写端接口允许匿名）。

## 2. 问卷管理（均需登录，且只能操作自己的问卷）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/surveys` | 我创建的问卷列表（按创建时间倒序） |
| POST | `/api/surveys` | 创建问卷（草稿） |
| GET | `/api/surveys/{id}` | 问卷详情（含题目与逻辑） |
| PUT | `/api/surveys/{id}` | 更新问卷 |
| DELETE | `/api/surveys/{id}` | 删除问卷（级联删除其答卷） |
| POST | `/api/surveys/{id}/publish` | 发布（校验结构并生成 shareCode） |
| POST | `/api/surveys/{id}/close` | 关闭已发布问卷 |
| GET | `/api/surveys/{id}/stats` | 统计结果 |

### POST /api/surveys
请求：`{"title": "期末调查", "description": "可选"}`

响应 200：完整问卷文档，`status=DRAFT`，`shareCode=null`。

### PUT /api/surveys/{id}
字段为 `null` 表示不修改。草稿可改全部字段；**已发布问卷只允许改 `title`/`description`/`settings`**，改 `questions`/`logic` 返回 400 `发布后题目与逻辑结构不可修改`。

请求示例（含四种题型与一条跳转规则）：

```json
{
  "title": "期末调查",
  "description": "课程反馈",
  "settings": { "allowAnonymous": true, "allowMultiple": false, "deadline": "2026-12-31T16:00:00Z" },
  "questions": [
    { "qid": "q1", "type": "single", "title": "你的性别", "required": true,
      "options": [{ "oid": "q1-o1", "text": "男" }, { "oid": "q1-o2", "text": "女" }] },
    { "qid": "q2", "type": "multiple", "title": "喜欢的水果", "required": false,
      "options": [{ "oid": "q2-o1", "text": "苹果" }, { "oid": "q2-o2", "text": "香蕉" }, { "oid": "q2-o3", "text": "橙子" }],
      "constraints": { "minSelect": 1, "maxSelect": 2 } },
    { "qid": "q3", "type": "text", "title": "你的建议", "required": false,
      "constraints": { "minLength": 0, "maxLength": 200 } },
    { "qid": "q4", "type": "number", "title": "你的年龄", "required": false,
      "constraints": { "min": 0, "max": 120, "integerOnly": true } }
  ],
  "logic": [
    { "id": "r1",
      "when": { "qid": "q1", "op": "eq", "value": "q1-o1" },
      "then": { "action": "jumpTo", "targetQid": "q3" } }
  ]
}
```

约束参数（`constraints`）按题型：

| 题型 | 键 | 含义 |
|---|---|---|
| multiple | `minSelect` / `maxSelect` | 最少/最多可选数量（`min==max==N` 表示必须选 N 项） |
| text | `minLength` / `maxLength` | 文本最少/最多字符数 |
| number | `min` / `max` / `integerOnly` | 数值下限/上限/是否仅整数 |
| single | 无 | — |

### POST /api/surveys/{id}/publish
校验：至少一道题 → 结构校验（qid/oid 唯一、标题非空、题型约束自洽、跳转规则合法）→ 生成唯一 shareCode → `status=PUBLISHED`。
错误：400 `仅草稿状态的问卷可以发布` / `问卷至少需要一道题才能发布`。

### POST /api/surveys/{id}/close
仅 `PUBLISHED` 可关闭 → `status=CLOSED`（关闭后填写端拒绝访问）。错误：400 `仅已发布的问卷可以关闭`。

### GET /api/surveys/{id}/stats
响应示例：

```json
{
  "surveyId": "66f...",
  "title": "期末调查",
  "responseCount": 12,
  "questions": [
    { "qid": "q1", "title": "你的性别", "type": "single",
      "summary": { "total": 12, "options": [ { "oid": "q1-o1", "text": "男", "count": 7 }, { "oid": "q1-o2", "text": "女", "count": 5 } ] } },
    { "qid": "q2", "title": "喜欢的水果", "type": "multiple",
      "summary": { "total": 9, "options": [ { "oid": "q2-o1", "text": "苹果", "count": 6 } ] } },
    { "qid": "q3", "title": "你的建议", "type": "text",
      "summary": { "total": 8, "texts": ["多些实践课", "作业少一点"] } },
    { "qid": "q4", "title": "你的年龄", "type": "number",
      "summary": { "total": 10, "average": 22.4, "min": 18, "max": 30 } }
  ]
}
```

说明：只统计 `SUBMITTED` 答卷；多选题 `total` 为作答该题的人数，`options[].count` 为各选项被选次数；未作答的答卷不计入对应题目的 `total`。

## 3. 填写端（通过分享链接，可匿名）

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/s/{shareCode}` | 问卷公开信息 |
| POST | `/api/s/{shareCode}/responses` | 开始/继续填写 |
| GET | `/api/s/{shareCode}/responses/{responseId}` | 答卷进度（续填） |
| POST | `/api/s/{shareCode}/responses/{responseId}/answers` | 保存单题答案 |
| POST | `/api/s/{shareCode}/responses/{responseId}/submit` | 提交答卷 |

### GET /api/s/{shareCode}
响应 200：`{"shareCode":"MRN5NUTx","title":"...","description":"...","allowAnonymous":true,"questions":[ ... ]}`

错误：404 问卷不存在/未发布；400 `问卷已关闭` / `问卷已截止`。

### POST /api/s/{shareCode}/responses
带 token 时以登录身份填写；不带则匿名（问卷 `allowAnonymous=false` 时返回 401 `该问卷需要登录后填写`）。

响应 200：`{"responseId":"66f...","currentQid":"q1","resumed":false}`
- 登录用户若已有草稿，直接返回原草稿并 `resumed=true`；
- 若问卷 `allowMultiple=false` 且该用户已提交过，返回 409 `您已提交过该问卷`。

### POST /api/s/{shareCode}/responses/{responseId}/answers
请求：`{"qid":"q1","optionIds":["q1-o1"]}`，字段按题型使用（`optionIds` / `text` / `number`）。

响应 200：`{"nextQid":"q3","done":false}`；`nextQid=null` 且 `done=true` 表示已到末尾。

错误：400 校验不通过（如 `多选题「喜欢的水果」最多选择 2 项`、`数字题「你的年龄」只接受整数`、`题目「你的性别」为必答题`）；400 `答卷已提交，不能再修改`。

### POST /api/s/{shareCode}/responses/{responseId}/submit
提交前会沿跳转路径从头逐题复核：路径上必答题未答 → 400；全部通过则 `status=SUBMITTED` 并记录 `submittedAt`。重复提交 → 400 `答卷已提交，请勿重复操作`。

响应 200：`{"result":"ok","responseId":"66f..."}`

## 4. 页面路由（Thymeleaf）

| 路径 | 页面 |
|---|---|
| `/login` | 登录/注册一体页 |
| `/surveys` | 我的问卷列表（创建、发布、关闭、删除、分享链接） |
| `/surveys/{id}/edit` | 问卷编辑器（基本信息/设置/题目/约束/跳转规则；发布后只读） |
| `/surveys/{id}/stats` | 统计页 |
| `/s/{shareCode}` | 填写页（逐题、跳转、校验、草稿续填） |
