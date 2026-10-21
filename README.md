# 问卷系统（Survey System）

基于 **MongoDB + Spring Boot** 的在线问卷系统（类问卷星 / Google Forms），数据库课程设计第一阶段成果。

注册用户可创建问卷并通过分享链接分发，填写者（支持匿名）逐题作答，创建者实时查看统计结果。

## 功能

| 模块 | 能力 |
|---|---|
| 用户 | 注册、登录（JWT 认证，密码 BCrypt 哈希存储） |
| 问卷管理 | 创建、编辑、发布、关闭、删除；问卷列表按创建时间倒序 |
| 题型 | 单选题、多选题、文本填空题、数字填空题；支持必答标记 |
| 约束 | 多选题 `minSelect`/`maxSelect`、文本 `minLength`/`maxLength`、数字 `min`/`max`/`integerOnly` |
| 跳转逻辑 | 数据驱动的条件跳转规则：按某题答案跳到指定题目或结束问卷 |
| 分享 | 发布时生成 8 位 `shareCode`，链接形如 `/s/{shareCode}` |
| 填写 | 逐题渲染、即时校验、按规则跳转、草稿实时落库、断点续填 |
| 设置 | 是否允许匿名、是否允许重复提交、截止时间 |
| 统计 | 选项计数、文本内容列表、数字平均值/最小值/最大值 |

## 技术栈

- **后端**：Java 21、Spring Boot 3.3.5、Spring Data MongoDB、Spring Security Crypto（仅 BCrypt）、jjwt 0.12.6、Lombok
- **数据库**：MongoDB（`surveydb`，三个集合 `users` / `surveys` / `responses`）
- **前端**：Thymeleaf + 原生 JavaScript（fetch），无前端构建步骤
- **测试**：JUnit 5、MockMvc、内嵌 MongoDB（de.flapdoodle.embed.mongo.spring31x）

## 环境要求

- JDK 21
- Maven 3.9+
- MongoDB（本地默认 `localhost:27017`；仅运行测试时不需要）

## 快速开始

```bash
# 1. 启动本地 MongoDB（Windows 服务名通常是 MongoDB）
#    数据目录示例：D:\MongoDB\data

# 2. 启动应用
mvn spring-boot:run

# 3. 浏览器访问
#    注册账号 → 我的问卷 → 新建问卷 → 编辑题目 → 保存并发布 → 复制分享链接
open http://localhost:8080/login
```

页面路由：

| 路径 | 说明 |
|---|---|
| `/login` | 登录 / 注册 |
| `/surveys` | 我的问卷列表 |
| `/surveys/{id}/edit` | 问卷编辑器 |
| `/surveys/{id}/stats` | 统计结果 |
| `/s/{shareCode}` | 填写页（对外分享） |

## 配置

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080

spring:
  data:
    mongodb:
      uri: mongodb://localhost:27017/surveydb
  thymeleaf:
    cache: false

app:
  jwt:
    secret: <HS256 密钥，至少 32 字节，生产环境应改为环境变量注入>
    ttl-hours: 72
```

## 项目结构

```
dbms_project/
├── pom.xml
├── docs/                                  设计与过程文档
├── src/main/java/com/example/survey/
│   ├── SurveyApplication.java             启动类
│   ├── config/                            WebConfig（拦截器注册）
│   ├── controller/                        Auth / Survey / Fill / Page 控制器
│   ├── service/                           Auth / Survey / Response / Stats 业务层
│   ├── core/                              可扩展核心逻辑
│   │   ├── question/                      题型处理器接口 + 4 个实现 + 注册表
│   │   ├── logic/                         条件算子注册表 + 跳转引擎
│   │   ├── validation/                    答案校验入口
│   │   └── Values.java                    参数类型统一解析
│   ├── model/                             领域文档模型
│   ├── repository/                        MongoRepository
│   ├── dto/                               请求 / 响应对象
│   ├── security/                          JWT 服务、认证拦截器
│   └── exception/                         统一异常处理
├── src/main/resources/
│   ├── application.yml
│   ├── templates/                         login / surveys / edit / fill / stats
│   └── static/                            css/app.css、js/common.js
└── src/test/java/com/example/survey/      单元 / 集成 / 端到端测试
```

## API 概览

所有接口前缀 `/api`，请求与响应均为 JSON；错误统一返回 `{"error": "..."}`。
管理端接口需请求头 `Authorization: Bearer <token>`。

**认证**

| 方法 | 路径 | 说明 |
|---|---|---|
| POST | `/api/auth/register` | 注册并返回 token |
| POST | `/api/auth/login` | 登录 |

**问卷管理（需登录）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/surveys` | 我的问卷列表 |
| POST | `/api/surveys` | 创建问卷（草稿） |
| GET / PUT / DELETE | `/api/surveys/{id}` | 查询 / 更新 / 删除 |
| POST | `/api/surveys/{id}/publish` | 发布并生成分享码 |
| POST | `/api/surveys/{id}/close` | 关闭问卷 |
| GET | `/api/surveys/{id}/stats` | 统计结果 |

**填写端（支持匿名）**

| 方法 | 路径 | 说明 |
|---|---|---|
| GET | `/api/s/{shareCode}` | 问卷公开信息 |
| POST | `/api/s/{shareCode}/responses` | 开始 / 继续填写 |
| GET | `/api/s/{shareCode}/responses/{responseId}` | 读取答卷进度 |
| POST | `/api/s/{shareCode}/responses/{responseId}/answers` | 保存单题答案，返回下一题 |
| POST | `/api/s/{shareCode}/responses/{responseId}/submit` | 提交答卷 |

完整参数、响应结构与错误码见 [API 说明](docs/02-API说明.md)。

## 测试

```bash
mvn test
```

测试会自动启动内嵌 MongoDB，不依赖本地数据库，使用独立库名 `surveydb_test` 并逐用例清库。

| 测试类 | 类型 | 用例数 |
|---|---|---|
| `ConditionOperatorsTest` | 纯单元（条件算子） | 8 |
| `LogicEngineTest` | 纯单元（跳转引擎） | 11 |
| `SurveyFlowIntegrationTest` | 集成（Spring + 内嵌 Mongo） | 21 |
| `ApiEndToEndTest` | 端到端（MockMvc 完整链路） | 8 |

最近一次执行：**48 个用例全部通过（0 失败 / 0 错误）**。

> 若 Maven 本地仓库位于受限目录导致插件下载报 `AccessDeniedException`，请在非沙箱终端中运行。

## 设计要点

- **文档模型**：题目、选项、约束、跳转规则全部内嵌于 `surveys`；答案内嵌于 `responses`。问卷是可变定义，答卷是已发生记录，二者用 `surveyId` 引用而非内嵌复制。
- **不写死逻辑**：题型通过 `QuestionTypeHandler` + Spring 注册表扩展，新增题型只需加一个实现类；跳转条件通过 `ConditionOperators` 注册表扩展，新增算子只需注册一行。
- **三层校验**：前端即时提示 → 保存单题时后端校验 → 提交时沿跳转路径逐题复核。
- **防脏数据**：回改前面的题目会清除该题之后的旧答案（跳转路径可能已改变），避免统计污染。
- **发布即锁定**：发布后题目与逻辑结构不可修改，标题、说明与设置仍可调整，保证已发出的链接与答卷一致。

## 文档

| 文档 | 内容 |
|---|---|
| [系统说明与数据库设计](docs/01-系统说明与数据库设计.md) | 集合字段、索引、内嵌与引用的取舍、为何选用文档数据库 |
| [API 说明](docs/02-API说明.md) | 全部端点、参数、响应、错误码、约束键 |
| [关键逻辑说明](docs/03-关键逻辑说明.md) | 扩展点、跳转引擎、校验分层、草稿与续填 |
| [测试用例](docs/04-测试用例.md) | 自动化用例与冒烟验证的步骤、输入、预期与结果 |
| [AI 使用日志](docs/05-AI使用日志.md) | 提示词、AI 产出、人工修改与问题修复记录 |
| [项目完成报告](docs/06-项目完成报告.md) | 目标、设计、测试结论、问题与第二阶段扩展预留 |

## 已知限制（阶段一）

- 跳转规则只允许向后跳，避免环；向前跳与循环需在第二阶段配合访问计数实现。
- 已发布问卷不支持修改题目结构。
- 统计实时计算、不落库，适合当前数据规模。
- JWT 密钥写在配置文件，部署时应改为环境变量。
