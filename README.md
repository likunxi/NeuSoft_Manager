# 爱管理

第 22 组软件项目管理课程项目。三个 Sprint 叠在同一个 Java Web 应用里：管理员管账号和角色，项目经理建项目、派任务、看多视图，成员更新自己的任务。Sprint 3 的大模型只出建议，人确认后才写回故事和任务。

当前版本：`1.0.0-sprint3`。入口类 `com.team22.aiguanli.AiguanliApplication`，浏览器打开 http://127.0.0.1:8080 。

## 技术栈

| 项 | 版本 / 说明 |
| --- | --- |
| JDK | 17 |
| Spring Boot | 3.4.3 |
| 页面 | Thymeleaf + Bootstrap 5.3.3 |
| 持久化 | MyBatis-Plus 3.5.9 |
| 数据库 | MySQL 8，库名 `aiguanli`，时区 `Asia/Shanghai` |
| 密码 | 种子里用 `{plain}` 前缀，启动后刷成 BCrypt |
| 大模型 | OpenAI 兼容的 `/chat/completions`，默认 DeepSeek |

登录用 Session，超时 60 分钟。除 `/login`、`/logout`、`/error` 和静态资源外，其余页面都要先登录。

## 功能

### Sprint 1 · 可视化

- 用户、角色、权限。权限按史诗分组，在「我的权限」里能看见自己能做什么。
- 项目与任务：创建、指派负责人、按标题或故事号搜索。
- 任务状态只有三种：`todo` / `doing` / `done`。改成已完成会写入 `pm_task_status_log`。
- 成员只能改自己负责的任务。有「改任意任务状态」的账号才能改别人的。
- 总览、三列看板、近 7 日完成趋势、状态时间轴、工作量。趋势读状态日志，不写死数字。

### Sprint 2 · 一体化

- 故事看板：用户故事按待办 / 进行中 / 已完成分列。
- 只读甘特：条数等于该项目任务数，颜色跟任务状态走。色条不能拖拽改期。
- 成员任务图：按当前负责人分列。
- UML：由故事和状态日志生成用例图、时序图（页面用 Mermaid 渲染）。
- 视图联动默认关闭。打开后，故事改状态会同步到甘特上的任务；看板上改状态会在成员图留下记录。

### Sprint 3 · AI 化

AI 助手在 `/projects/{id}/ai`。没有「创建任务」权限的账号不能提交，也不能写回。

| 能力 | 行为 |
| --- | --- |
| 大模型配置 | 保存接口地址、模型名和密钥。密钥写入 `data/llm.properties`（已在 `.gitignore`），也可用环境变量 `AIGUANLI_LLM_API_KEY`。密钥留空表示沿用已保存的。 |
| 项目分析 | 按任务看板、甘特、人员或风险测评，用当前项目真实数据让模型写分析。只出文字，不改业务数据。 |
| 需求拆解 | 提交一段需求，模型按 OpenAI 兼容接口返回拆解建议，先落 `ai_suggestion`。采纳或修改后才写入故事和任务，故事号从 `E3-` 开始。拒绝只留人审记录。 |
| 智能排期 | 不调用大模型。按优先级、前置任务和「每人同时只做一件待办」生成日期建议。进行中和已完成保持原日期。人点「写入甘特」后才改起止日期。甘特底部保留最近写入记录。 |
| 进度预测 | 按未完成任务的剩余工期和近 7 天完成条数算出预测完成日。同一个人的未完成工期相加，项目结束日取最晚的那个人。配置了大模型时，模型只解释这个日期，不改甘特。 |
| 效率优化 | 根据剩余工期给阅读用的建议。要改日期仍走智能排期。 |
| 延期风险 | 按截止日期刷新。已过期为高，两天内到期为中，已完成不预警。 |

任务看板可以拖拽。有「改任意任务状态」权限时，把卡片拖到另一列，故事号前缀改成 `E1` / `E2` / `E3`。甘特按这个进阶重新分组：

| 前缀 | 进阶 |
| --- | --- |
| `E1`（以及其他前缀） | 进阶 1 · 可视化 |
| `E2` / `S2` | 进阶 2 · 一体化 |
| `E3` / `S3` | 进阶 3 · AI 化 |

人审约束（W-03）：建议表和写回表分开。处置只允许采纳、修改、拒绝，且必须填写理由（不超过 500 字）。拒绝不能写回。`sql/V3b__writeback_trigger.sql` 在数据库侧再拦一层：没有对应的采纳或修改记录时，插入 `ai_writeback` 会失败。

质量分析的表 `ai_quality_score` 已建，页面没有入口。详见 `RELEASE.md`。

## 角色与权限

| 权限码 | 名称 | 管理员 | 项目经理 | 团队成员 |
| --- | --- | --- | --- | --- |
| `user:manage` | 管理用户账号 | 有 | | |
| `role:manage` | 管理角色权限 | 有 | | |
| `project:create` | 创建项目 | 有 | 有 | |
| `project:view` | 查看项目 | 有 | 有 | 有 |
| `task:create` | 创建任务 | 有 | 有 | |
| `task:assign` | 指定负责人 | 有 | 有 | |
| `task:update_any` | 改任意任务状态 | 有 | 有 | |
| `task:update_own` | 改自己的任务状态 | 有 | 有 | 有 |
| `board:view` | 查看看板 | 有 | 有 | 有 |
| `trend:view` | 查看趋势 | 有 | 有 | |
| `workload:view` | 查看工作量 | 有 | | |

侧栏按权限显示：总览和我的权限始终可见；用户管理、角色权限、项目任务按上表出现。

## 环境

- JDK 17
- MySQL 8，本机 `127.0.0.1:3306`
- Maven 3.8+，或 IntelliJ IDEA 自带的 Maven
- 拆解、项目分析、进度预测的模型解释需要能访问所填的大模型地址。排期和延期风险不依赖外网。

## 启动

1. 建库并灌入演示数据。脚本会按序执行建库、Sprint 1/2/3 表和种子。默认尝试密码 `123456`、`root` 和空密码。

```powershell
cd sql
# 若 root 密码不是 123456：
# $env:MYSQL_PWD = "你的密码"
# $env:MYSQL_USER = "root"
.\install.ps1
```

`install.ps1` 会先找 `D:\DataBase\mysql-8.0.42-winx64\bin\mysql.exe`，找不到再用 PATH 里的 `mysql`。重装再跑一遍即可，`V1` 会先删后建。

写回触发器不在 `install.ps1` 里。需要数据库侧拦截时，在已选中 `aiguanli` 的客户端里执行 `sql/V3b__writeback_trigger.sql`。

2. 数据库密码不是 `123456` 时，改 `src/main/resources/application.yml` 里的 `spring.datasource.password`，或在启动前设置与脚本相同的环境变量。应用读的是 yml，脚本读的是 `MYSQL_PWD`，两处要一致。

3. 用 IDEA 打开本目录的 `pom.xml`，运行 `AiguanliApplication`。或：

```powershell
mvn spring-boot:run
```

4. 浏览器打开 http://127.0.0.1:8080 ，未登录会转到 `/login`。

## 配置

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/aiguanli?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
    username: root
    password: 123456
aiguanli:
  llm:
    base-url: https://api.deepseek.com/v1
    model: deepseek-chat
    api-key: ${AIGUANLI_LLM_API_KEY:}
```

页面上保存的地址和密钥会覆盖 yml，并写到运行目录下的 `data/llm.properties`。DeepSeek 用上面的默认地址和模型名 `deepseek-chat`。通义等兼容模式换成服务商给的 `v1` 地址即可，接口路径固定为 `{base-url}/chat/completions`。

## 演示账号

| 用户 | 密码 | 角色 | 用途 |
| --- | --- | --- | --- |
| admin | admin123 | 系统管理员 | 用户、角色、全部权限 |
| pm | pm123 | 项目经理 | 派任务、看板、AI 写回 |
| member | member123 | 团队成员 | 只能改自己的任务，打不开 AI 提交和写回 |
| likx / zhangyl | 22team | 项目经理 | 第 22 组：李坤溪、张译泷 |
| lisb / qubq | 22team | 团队成员 | 第 22 组：李诗博、曲柏桥 |

种子项目 `AG-22`（22组-爱管理）里已经放了 E1、E2 故事和任务。首次启动会把 `{plain}` 密码升级成 BCrypt，之后登录密码不变。

## 页面

登录后 `/` 会转到 `/dashboard`。

Sprint 1：

| 路径 | 说明 |
| --- | --- |
| `/dashboard` | 总览：指标、三列看板、近 7 日完成量、工作量（需 `workload:view`） |
| `/search` | 按标题或故事号搜任务 |
| `/my-tasks` | 我负责的任务 |
| `/profile` | 权限地图 |
| `/account` | 改自己的资料和密码 |
| `/users` `/users/{id}` | 用户列表、启停、改角色和重置密码 |
| `/roles` | 角色与权限分配 |
| `/projects` | 项目列表，可新建项目 |
| `/work` | 三阶任务看板，可拖拽改进行阶 |
| `/projects/{id}/tasks` | 建任务、指派、改状态。`/projects/{id}/board` 是同一页 |
| `/projects/{id}/trend` | 完成趋势，读 `pm_task_status_log` |
| `/projects/{id}/timeline` | 状态时间轴 |

Sprint 2，从项目卡片进入：

| 路径 | 说明 |
| --- | --- |
| `/projects/{id}/stories` | 故事看板 |
| `/projects/{id}/gantt` | 只读甘特 |
| `/projects/{id}/members` | 成员任务图 |
| `/projects/{id}/uml` | 用例图、时序图 |
| `/projects/{id}/links` | 联动开关，默认关闭 |

Sprint 3：

| 路径 | 说明 |
| --- | --- |
| `/projects/{id}/ai` | AI 助手：模型配置、分析、拆解、排期、预测、效率建议、延期风险 |
| `/coming/{page}` | 旧入口。`/coming/ai` 会转到当前项目的 AI 助手 |

主要写操作：

| 方法 | 路径 | 作用 |
| --- | --- | --- |
| POST | `/projects/{id}/tasks`、`/work/tasks` | 新建任务 |
| POST | `/tasks/{id}/assign` | 指定负责人 |
| POST | `/tasks/{id}/status` | 改状态 |
| POST | `/tasks/{id}/stage` | 拖到另一进阶，改故事号前缀 |
| POST | `/tasks/{id}/delete` | 删除任务 |
| POST | `/projects/{id}/stories/{storyId}/status` | 改故事状态 |
| POST | `/projects/{id}/links/{ruleId}` | 开关一条联动规则 |
| POST | `/projects/{id}/ai/llm` | 保存大模型配置 |
| POST | `/projects/{id}/ai/analyze` | 按所选对象生成分析 |
| POST | `/projects/{id}/ai/split` | 提交需求，生成拆解建议 |
| POST | `/projects/{id}/ai/suggestions/{suggestionId}/review` | 采纳、修改或拒绝 |
| POST | `/projects/{id}/ai/schedule` | 生成排期建议 |
| POST | `/projects/{id}/ai/schedule/{suggestionId}` | 写入甘特或不采用 |
| POST | `/projects/{id}/ai/forecast` | 生成进度预测 |
| POST | `/projects/{id}/ai/advice` | 生成效率建议 |
| POST | `/projects/{id}/ai/risks` | 按截止日期刷新延期风险 |

## 建议演示顺序

1. 用 `pm / pm123` 登录，打开项目「22组-爱管理」。
2. 看故事看板、甘特、成员图，确认 E1 / E2 任务已经在。
3. 打开 AI 助手，保存大模型地址、模型名和密钥。
4. 选「任务看板」或「风险测评」，点开始分析，确认只出现文字报告。
5. 贴两行需求，生成拆解建议，填写理由后点采纳。回到故事看板和任务看板，应能看到 `E3` 故事。
6. 再提交一条需求并拒绝，确认看板没有新增。
7. 在任务看板把一张卡片拖到另一列，打开甘特，分组跟着故事号前缀变。色条本身不能拖。
8. 生成排期建议并写入甘特，日期才会变。生成进度预测，日期不应被改掉。
9. 在任务页刷新延期风险：已过期为高，两天内到期为中。
10. 退出后用 `member / member123` 登录。可以改自己的任务状态，打不开 AI 提交和写回，也不能拖拽改别人的进阶。

## 目录

```
NeuSoft_Manager/
├── pom.xml
├── README.md
├── RELEASE.md                 已发布 / 未发布边界
├── sql/
│   ├── install.ps1            建库 + V1 + V2 + V3 + 种子
│   ├── 00_create_database.sql
│   ├── V1__sprint1_core.sql   用户、角色、项目、任务、状态日志
│   ├── V2__sprint2_integration.sql
│   ├── V3__sprint3_ai.sql
│   ├── V3b__writeback_trigger.sql
│   ├── seed_sprint1.sql       演示账号与 AG-22
│   └── README.md
└── src/main/java/com/team22/aiguanli/
    ├── AiguanliApplication.java
    ├── web/                   页面与登录
    ├── service/               看板、甘特、拆解、排期、预测、风险
    ├── security/              登录拦截与权限注解
    ├── mapper/  entity/  config/
└── src/main/resources/
    ├── application.yml
    ├── templates/             Thymeleaf 页面
    └── static/                样式与 Mermaid
```

`data/llm.properties` 在第一次于页面保存密钥后出现，不要提交。

## 数据库

脚本说明见 `sql/README.md`。业务表：

| 脚本 | 表 |
| --- | --- |
| V1 | `sys_role` `sys_permission` `sys_role_permission` `sys_user` `pm_project` `pm_task` `pm_task_status_log` |
| V2 | `pm_story` `pm_task_dependency` `vw_link_rule` `vw_sync_event` `pm_uml_artifact` |
| V3 | `ai_prd` `ai_suggestion` `ai_review` `ai_writeback` `ai_prediction` `ai_risk_alert` `ai_quality_score` |

AI 产出先写 `ai_suggestion`。人审写入 `ai_review`（`accept` / `modify` / `reject`）。只有采纳或修改才会记入 `ai_writeback` 并改 `pm_story`、`pm_task`。

## 测试

规则测试不连数据库，可以直接跑：

```powershell
mvn -q test
```

只跑和三个 Sprint 相关的用例：

```powershell
mvn -q org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile org.apache.maven.plugins:maven-compiler-plugin:3.8.1:testCompile org.apache.maven.plugins:maven-surefire-plugin:3.0.0-M5:test "-Dtest=TaskStatusRulesTest,Sprint1RegressionTest,Sprint2ViewTest,GanttServiceTest,TaskStageMoveTest,RequirementSplitterTest,LlmSplitterTest,AiReviewRulesTest,AiAssistServiceTest,DelayRiskRulesTest,SchedulePlannerTest,AnalysisFactsTest,TaskRemovalServiceTest"
```

| 测试 | 锁住的行为 |
| --- | --- |
| `TaskStatusRulesTest` / `Sprint1RegressionTest` | 状态只有三种；成员不能改别人的任务；完成会写状态日志 |
| `Sprint2ViewTest` / `GanttServiceTest` | 故事看板、甘特条数与颜色、联动 |
| `TaskStageMoveTest` | 拖拽进阶改 E1 / E2 / E3 前缀 |
| `RequirementSplitterTest` / `LlmSplitterTest` | 需求拆成建议，不直接写任务 |
| `AiReviewRulesTest` / `AiAssistServiceTest` | 拒绝不写回；采纳或修改才落地 |
| `DelayRiskRulesTest` | 过期为高，两天内为中，已完成不预警 |
| `SchedulePlannerTest` | 排期不调用大模型，尊重优先级和前置任务 |
| `AnalysisFactsTest` | 分析只用项目里已有的事实 |
| `TaskRemovalServiceTest` | 删除任务时的连带清理 |

## 边界

- 没有第四种任务状态。
- 甘特色条不能拖拽改期。日期只在智能排期被人确认后改变。
- 进度预测和效率优化不改甘特。
- 质量分析未发布，不要当成可演示功能。
- 旧地址 `/coming/ai` 只做跳转。
