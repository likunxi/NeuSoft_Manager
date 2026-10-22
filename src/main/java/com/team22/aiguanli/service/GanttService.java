package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskDependency;
import com.team22.aiguanli.mapper.PmTaskDependencyMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 只读甘特：一条 pm_task 对应一条，位置按 startDate / dueDate 折成 left% 与 width%。
 */
@Service
public class GanttService {

    private final ProjectService projectService;
    private final PmTaskDependencyMapper dependencyMapper;

    public GanttService(ProjectService projectService, PmTaskDependencyMapper dependencyMapper) {
        this.projectService = projectService;
        this.dependencyMapper = dependencyMapper;
    }

    public View view(Long projectId) {
        View view = new View();
        view.project = projectService.getProject(projectId);
        view.bars = layout(projectService.listTasks(projectId));
        view.groups = group(view.bars);
        view.taskCount = view.bars.size();
        view.days = new ArrayList<>();
        if (view.bars.isEmpty()) {
            view.rangeStart = "";
            view.rangeEnd = "";
            return view;
        }
        view.rangeStart = view.bars.get(0).rangeStart;
        view.rangeEnd = view.bars.get(0).rangeEnd;
        LocalDate start = LocalDate.parse(view.rangeStart);
        LocalDate end = LocalDate.parse(view.rangeEnd);
        LocalDate today = LocalDate.now();
        long span = ChronoUnit.DAYS.between(start, end) + 1;
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            DayCol col = new DayCol();
            col.day = String.valueOf(day.getDayOfMonth());
            col.md = day.getMonthValue() + "/" + day.getDayOfMonth();
            col.week = WEEK[day.getDayOfWeek().getValue() - 1];
            col.today = day.equals(today);
            view.days.add(col);
        }
        if (!today.isBefore(start) && !today.isAfter(end) && span > 0) {
            double pct = (ChronoUnit.DAYS.between(start, today) + 0.5) * 100.0 / span;
            view.todayPct = formatPct(pct);
        }
        return view;
    }

    public List<String> dependencyLines(Long projectId) {
        List<String> lines = new ArrayList<>();
        if (dependencyMapper == null || projectId == null) {
            return lines;
        }
        List<PmTaskDependency> deps = dependencyMapper.selectList(new LambdaQueryWrapper<PmTaskDependency>()
                .eq(PmTaskDependency::getProjectId, projectId)
                .orderByAsc(PmTaskDependency::getId));
        Map<Long, String> titles = new java.util.LinkedHashMap<>();
        for (PmTask task : projectService.listTasks(projectId)) {
            titles.put(task.getId(), task.getTitle());
        }
        for (PmTaskDependency dep : deps) {
            String from = titles.getOrDefault(dep.getFromTaskId(), "任务" + dep.getFromTaskId());
            String to = titles.getOrDefault(dep.getToTaskId(), "任务" + dep.getToTaskId());
            String type = dep.getDepType() == null ? "FS" : dep.getDepType();
            lines.add(from + " → " + to + "（" + type + "）");
        }
        return lines;
    }

    /**
     * 时间轴取全部任务起止的最小日到最大日（含首尾）。
     * left% = (任务开始 - 轴起点) / 轴跨度；width% = 任务天数 / 轴跨度。
     * 没有日期的任务仍保留一条，避免条数少于 pm_task 行数。
     */
    public List<Bar> layout(List<PmTask> tasks) {
        List<Bar> bars = new ArrayList<>();
        if (tasks == null || tasks.isEmpty()) {
            return bars;
        }
        List<LocalDate> starts = new ArrayList<>();
        List<LocalDate> ends = new ArrayList<>();
        for (PmTask task : tasks) {
            LocalDate start = resolveStart(task);
            LocalDate end = resolveEnd(task, start);
            if (end.isBefore(start)) {
                LocalDate swap = start;
                start = end;
                end = swap;
            }
            starts.add(start);
            ends.add(end);
        }
        LocalDate rangeStart = starts.stream().min(LocalDate::compareTo).orElseThrow();
        LocalDate rangeEnd = ends.stream().max(LocalDate::compareTo).orElseThrow();
        long span = ChronoUnit.DAYS.between(rangeStart, rangeEnd) + 1;
        if (span < 1) {
            span = 1;
        }
        for (int i = 0; i < tasks.size(); i++) {
            PmTask task = tasks.get(i);
            LocalDate start = starts.get(i);
            LocalDate end = ends.get(i);
            long offset = ChronoUnit.DAYS.between(rangeStart, start);
            long length = ChronoUnit.DAYS.between(start, end) + 1;
            if (length < 1) {
                length = 1;
            }
            double left = offset * 100.0 / span;
            double width = length * 100.0 / span;
            if (left < 0) {
                left = 0;
            }
            if (left > 100) {
                left = 100;
            }
            if (left + width > 100) {
                width = 100 - left;
            }
            if (width < 0) {
                width = 0;
            }
            Bar bar = new Bar();
            bar.taskId = task.getId();
            bar.title = task.getTitle() == null ? "" : task.getTitle();
            bar.storyCode = task.getStoryCode() == null ? "" : task.getStoryCode();
            bar.status = task.getStatus() == null ? "" : task.getStatus();
            bar.stage = TaskStages.of(task.getStoryCode());
            bar.assigneeId = task.getAssigneeId();
            bar.progress = progressOf(task);
            bar.startLabel = task.getStartDate() == null ? "未排期" : task.getStartDate().toString();
            bar.dueLabel = task.getDueDate() == null ? "未排期" : task.getDueDate().toString();
            bar.leftPct = formatPct(left);
            bar.widthPct = formatPct(width);
            bar.rangeStart = rangeStart.toString();
            bar.rangeEnd = rangeEnd.toString();
            bars.add(bar);
        }
        return bars;
    }

    private List<Group> group(List<Bar> bars) {
        Map<String, Group> map = new LinkedHashMap<>();
        for (Bar bar : bars) {
            String stage = bar.stage == null || bar.stage.isBlank() ? "e1" : bar.stage;
            Group group = map.computeIfAbsent(stage, key -> {
                Group created = new Group();
                created.key = key;
                created.label = TaskStages.label(key);
                created.bars = new ArrayList<>();
                return created;
            });
            group.bars.add(bar);
        }
        List<Group> ordered = new ArrayList<>();
        for (String stage : List.of("e1", "e2", "e3")) {
            if (map.containsKey(stage)) {
                ordered.add(map.remove(stage));
            }
        }
        ordered.addAll(map.values());
        return ordered;
    }

    private int progressOf(PmTask task) {
        int progress;
        if (task.getProgress() != null) {
            progress = task.getProgress();
        } else if ("done".equals(task.getStatus())) {
            progress = 100;
        } else {
            progress = 0;
        }
        return Math.max(0, Math.min(100, progress));
    }

    private LocalDate resolveStart(PmTask task) {
        if (task.getStartDate() != null) {
            return task.getStartDate();
        }
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        if (task.getCreatedAt() != null) {
            return task.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private LocalDate resolveEnd(PmTask task, LocalDate start) {
        if (task.getDueDate() != null) {
            return task.getDueDate();
        }
        if (task.getStartDate() != null) {
            return task.getStartDate();
        }
        return start;
    }

    private static final String[] WEEK = {"一", "二", "三", "四", "五", "六", "日"};

    private static String formatPct(double value) {
        double rounded = Math.round(value * 10.0) / 10.0;
        return String.format(Locale.US, "%.1f", rounded);
    }

    public static class View {
        public PmProject project;
        public List<Bar> bars = new ArrayList<>();
        public List<Group> groups = new ArrayList<>();
        public List<DayCol> days = new ArrayList<>();
        public String rangeStart;
        public String rangeEnd;
        public String todayPct;
        public int taskCount;
    }

    public static class Group {
        public String key;
        public String label;
        public List<Bar> bars;
    }

    public static class DayCol {
        public String day;
        public String md;
        public String week;
        public boolean today;
    }

    public static class Bar {
        public Long taskId;
        public String title;
        public String storyCode;
        public String status;
        public String stage;
        public Long assigneeId;
        public int progress;
        public String startLabel;
        public String dueLabel;
        public String leftPct;
        public String widthPct;
        public String rangeStart;
        public String rangeEnd;
    }
}
