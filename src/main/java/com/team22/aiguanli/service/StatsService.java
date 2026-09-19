package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class StatsService {

    private final PmTaskMapper taskMapper;
    private final PmTaskStatusLogMapper logMapper;
    private final SysUserMapper userMapper;

    public StatsService(PmTaskMapper taskMapper, PmTaskStatusLogMapper logMapper, SysUserMapper userMapper) {
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
        this.userMapper = userMapper;
    }

    public Map<String, List<PmTask>> board(Long projectId) {
        List<PmTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId));
        Map<String, List<PmTask>> columns = new LinkedHashMap<>();
        columns.put("todo", new ArrayList<>());
        columns.put("doing", new ArrayList<>());
        columns.put("done", new ArrayList<>());
        for (PmTask task : tasks) {
            columns.computeIfAbsent(task.getStatus(), k -> new ArrayList<>()).add(task);
        }
        return columns;
    }

    public TrendVo trend(Long projectId, int days) {
        List<PmTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId));
        TrendVo vo = new TrendVo();
        vo.total = tasks.size();
        vo.todo = (int) tasks.stream().filter(t -> "todo".equals(t.getStatus())).count();
        vo.doing = (int) tasks.stream().filter(t -> "doing".equals(t.getStatus())).count();
        vo.done = (int) tasks.stream().filter(t -> "done".equals(t.getStatus())).count();
        vo.completionRate = vo.total == 0 ? 0 : Math.round(vo.done * 1000.0 / vo.total) / 10.0;

        List<PmTaskStatusLog> logs = logMapper.selectList(new LambdaQueryWrapper<PmTaskStatusLog>()
                .eq(PmTaskStatusLog::getProjectId, projectId)
                .eq(PmTaskStatusLog::getToStatus, "done"));
        LocalDate today = LocalDate.now();
        Map<String, Integer> byDay = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            byDay.put(today.minusDays(i).toString(), 0);
        }
        for (PmTaskStatusLog log : logs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            String day = log.getCreatedAt().toLocalDate().toString();
            if (byDay.containsKey(day)) {
                byDay.put(day, byDay.get(day) + 1);
            }
        }
        vo.dates = new ArrayList<>(byDay.keySet());
        vo.counts = new ArrayList<>(byDay.values());
        vo.bars = toBars(byDay, 160);

        vo.todoPct = pct(vo.todo, vo.total);
        vo.doingPct = pct(vo.doing, vo.total);
        vo.donePct = pct(vo.done, vo.total);
        vo.todoDeg = deg(vo.todo, vo.total);
        vo.doingDeg = deg(vo.doing, vo.total);
        vo.doneDeg = Math.max(0, 360 - vo.todoDeg - vo.doingDeg);

        vo.e1 = (int) tasks.stream().filter(t -> "e1".equals(TaskStages.of(t.getStoryCode()))).count();
        vo.e2 = (int) tasks.stream().filter(t -> "e2".equals(TaskStages.of(t.getStoryCode()))).count();
        vo.e3 = (int) tasks.stream().filter(t -> "e3".equals(TaskStages.of(t.getStoryCode()))).count();
        vo.e1Pct = pct(vo.e1, vo.total);
        vo.e2Pct = pct(vo.e2, vo.total);
        vo.e3Pct = pct(vo.e3, vo.total);

        List<PmTaskStatusLog> allLogs = logMapper.selectList(new LambdaQueryWrapper<PmTaskStatusLog>()
                .eq(PmTaskStatusLog::getProjectId, projectId));
        Map<String, Integer> activity = new LinkedHashMap<>();
        for (int i = days - 1; i >= 0; i--) {
            activity.put(today.minusDays(i).toString(), 0);
        }
        for (PmTaskStatusLog log : allLogs) {
            if (log.getCreatedAt() == null) {
                continue;
            }
            String day = log.getCreatedAt().toLocalDate().toString();
            if (activity.containsKey(day)) {
                activity.put(day, activity.get(day) + 1);
            }
        }
        vo.activityBars = toBars(activity, 120);
        vo.activityTotal = allLogs.size();
        return vo;
    }

    private List<DayBar> toBars(Map<String, Integer> byDay, int maxHeight) {
        int max = byDay.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        List<DayBar> bars = new ArrayList<>();
        for (Map.Entry<String, Integer> e : byDay.entrySet()) {
            DayBar bar = new DayBar();
            bar.date = e.getKey();
            bar.label = e.getKey().length() >= 10 ? e.getKey().substring(5) : e.getKey();
            bar.count = e.getValue();
            bar.height = max <= 0 ? 8 : Math.max(8, Math.round(e.getValue() * (float) maxHeight / max));
            bars.add(bar);
        }
        return bars;
    }

    private int pct(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 100f / total);
    }

    private int deg(int part, int total) {
        return total == 0 ? 0 : Math.round(part * 360f / total);
    }

    /** E1-09：按负责人聚合当前项目任务数，给管理员调配人力。 */
    public List<WorkloadVo> workload(Long projectId) {
        List<PmTask> tasks = taskMapper.selectList(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId));
        Map<Long, WorkloadVo> map = new LinkedHashMap<>();
        for (PmTask task : tasks) {
            Long key = task.getAssigneeId() == null ? -1L : task.getAssigneeId();
            WorkloadVo item = map.computeIfAbsent(key, k -> new WorkloadVo());
            item.userId = task.getAssigneeId();
            if (item.name == null) {
                if (task.getAssigneeId() == null) {
                    item.name = "未分配";
                } else {
                    SysUser user = userMapper.selectById(task.getAssigneeId());
                    item.name = user == null ? "未分配" : user.getDisplayName();
                }
            }
            item.total++;
            if ("todo".equals(task.getStatus())) {
                item.todo++;
            } else if ("doing".equals(task.getStatus())) {
                item.doing++;
            } else if ("done".equals(task.getStatus())) {
                item.done++;
            }
        }
        int max = map.values().stream().mapToInt(w -> w.total).max().orElse(1);
        for (WorkloadVo item : map.values()) {
            item.share = max == 0 ? 0 : Math.round(item.total * 100f / max);
            item.todoPct = pct(item.todo, item.total);
            item.doingPct = pct(item.doing, item.total);
            item.donePct = pct(item.done, item.total);
        }
        return new ArrayList<>(map.values());
    }

    public TrendVo emptyTrend() {
        TrendVo vo = new TrendVo();
        vo.dates = new ArrayList<>();
        vo.counts = new ArrayList<>();
        vo.bars = new ArrayList<>();
        vo.activityBars = new ArrayList<>();
        return vo;
    }

    public static class WorkloadVo {
        public Long userId;
        public String name;
        public int total;
        public int todo;
        public int doing;
        public int done;
        public int share;
        public int todoPct;
        public int doingPct;
        public int donePct;
    }

    public static class DayBar {
        public String date;
        public String label;
        public int count;
        public int height;
    }

    public static class TrendVo {
        public int total;
        public int todo;
        public int doing;
        public int done;
        public double completionRate;
        public List<String> dates;
        public List<Integer> counts;
        public List<DayBar> bars;
        public List<DayBar> activityBars;
        public int activityTotal;
        public int todoPct;
        public int doingPct;
        public int donePct;
        public int todoDeg;
        public int doingDeg;
        public int doneDeg;
        public int e1;
        public int e2;
        public int e3;
        public int e1Pct;
        public int e2Pct;
        public int e3Pct;
    }
}
