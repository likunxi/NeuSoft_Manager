<<<<<<< HEAD
# 爱管理 · Sprint 1（第22组）

JavaWeb 可视化原型：管理员管账号角色，项目经理派任务、看看板和趋势，成员看权限地图并更新自己的任务。

## 最快启动（3 步）

1. 执行库脚本（会一次建好 Sprint 1/2/3 的表，代码只使用 Sprint 1）

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

测试：`TaskStatusRulesTest`（状态机与越权，不依赖数据库）。

## 本轮页面

- `/dashboard` 总览：指标、三列看板、近 7 日完成量、最近动态、工作量（E1-09）
- `/search` 按标题 / 故事号搜任务
- `/my-tasks` 我负责的任务
- `/profile` 权限地图（E1-03）
- `/users` `/roles`（E1-01 / E1-02）
- `/projects` `/projects/{id}/tasks`（E1-04 / E1-05，支持筛选与故事号）
- `/projects/{id}/board`（E1-06）
- `/projects/{id}/trend`（E1-08）
- `/projects/{id}/timeline` 状态时间轴

甘特、故事地图、故事点、依赖、UML、AI、评论附件、导出报表没有菜单入口。对应表已在 `sql/V2`、`sql/V3` 建好，留给后两个 Sprint。
=======
# NeuSoft_Manager
东软软件项目管理项目
>>>>>>> 866df7a61a2821d162fd33c8499516172e3d02d9
