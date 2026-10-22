# 爱管理 · Sprint 2（第22组）

在 Sprint 1 上继续做的 JavaWeb 原型。管理员管账号角色，项目经理派任务、看看板和趋势，成员更新自己的任务。本轮加上故事看板、只读甘特、成员任务图、UML 和视图联动。

## 启动

1. 执行库脚本（会建好 Sprint 1/2/3 的表。页面用到 Sprint 1 和 Sprint 2，AI 仍不接模型）

```powershell
cd sql
# 若 root 密码不是 123456：
# $env:MYSQL_PWD = "你的密码"
.\install.ps1
```

2. 若密码不是 123456，启动前设置同一环境变量，或改 `src/main/resources/application.yml`。

3. 用 IDEA 打开本目录（Maven 项目）运行 `AiguanliApplication`，浏览器打开 http://127.0.0.1:8080

演示账号：

| 用户 | 密码 | 角色 |
| --- | --- | --- |
| admin | admin123 | 系统管理员 |
| pm | pm123 | 项目经理 |
| member | member123 | 团队成员 |
| likx / zhangyl / lisb / qubq | 22team | 第22组真人 |

没有本机 Maven 时：用 IntelliJ IDEA 打开 `pom.xml`，IDEA 自带 Maven。

测试不依赖数据库：

```powershell
mvn -q org.apache.maven.plugins:maven-compiler-plugin:3.8.1:compile org.apache.maven.plugins:maven-compiler-plugin:3.8.1:testCompile org.apache.maven.plugins:maven-surefire-plugin:3.0.0-M5:test "-Dtest=TaskStatusRulesTest,GanttServiceTest,Sprint2ViewTest,Sprint1RegressionTest"
```

`Sprint1RegressionTest` 锁住 Sprint 1：成员不能改别人的任务，状态只有 todo / doing / done，改成已完成会写状态日志。

## 页面

Sprint 1：

- `/dashboard` 总览
- `/search` 按标题或故事号搜任务
- `/my-tasks` 我负责的任务
- `/profile` 权限地图
- `/users` `/roles` 用户与角色
- `/work` 三阶任务看板
- `/projects/{id}/tasks` 建任务、指派、改状态
- `/projects/{id}/trend` 完成趋势，读 `pm_task_status_log`
- `/projects/{id}/timeline` 状态时间轴

Sprint 2，从项目卡片进入：

- `/projects/{id}/stories` 故事看板
- `/projects/{id}/gantt` 只读甘特，条数等于该项目任务数，颜色跟 todo / doing / done
- `/projects/{id}/members` 成员任务图，按当前负责人分列
- `/projects/{id}/uml` 由故事和状态日志生成用例图、时序图
- `/projects/{id}/links` 联动开关。默认关闭。打开后，故事改状态会同步到甘特上的任务，看板上改状态会在成员图留下记录

`/coming/ai` 仍是预留页，本轮不接模型。甘特不能拖拽改期，没有第四种状态。
