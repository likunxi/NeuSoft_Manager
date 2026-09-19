# 数据库脚本

按序执行，或直接跑 `install.ps1`。

| 文件 | 说明 |
| --- | --- |
| `00_create_database.sql` | 建库 `aiguanli` |
| `V1__sprint1_core.sql` | 可视化主数据 + 状态日志 + 统计视图 |
| `V2__sprint2_integration.sql` | 一体化预留表 |
| `V3__sprint3_ai.sql` | AI 建议 / 人审 / 写回触发器 |
| `seed_sprint1.sql` | 演示数据与 22 组自举项目 |

重装：再跑一遍 `install.ps1`（V1 会先删后建）。
