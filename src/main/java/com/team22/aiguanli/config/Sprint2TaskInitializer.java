package com.team22.aiguanli.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.entity.PmStory;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.PmTaskStatusLog;
import com.team22.aiguanli.mapper.PmProjectMapper;
import com.team22.aiguanli.mapper.PmStoryMapper;
import com.team22.aiguanli.mapper.PmTaskMapper;
import com.team22.aiguanli.mapper.PmTaskStatusLogMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务看板的进阶 2 列只收故事号以 E2 开头的任务。已有库只有 Sprint 1 的 E1，这里补齐本轮故事和任务。
 */
@Component
@Order(20)
public class Sprint2TaskInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(Sprint2TaskInitializer.class);

    private static final List<Spec> SPECS = List.of(
            new Spec("E2-01", "故事看板", "项目经理", "在故事看板上按三列查看用户故事", "把需求和任务对上", "Must", "done", 100, -1, 0, 10L),
            new Spec("E2-02", "只读甘特图", "项目经理", "按起止日期看到任务条和实际进度", "安排这一周的工作", "Must", "doing", 70, 0, 3, 11L),
            new Spec("E2-03", "成员任务图", "项目经理", "按成员看到各自负责的任务", "发现谁的活堆在一起", "Must", "doing", 60, 0, 3, 12L),
            new Spec("E2-04", "由故事生成 UML", "团队成员", "从故事和状态日志生成用例图和时序图", "不用手画结构", "Should", "doing", 50, 1, 4, 13L),
            new Spec("E2-05", "需求变更同步甘特", "项目经理", "改故事状态时同步到甘特上的任务", "需求和排期不各写各的", "Must", "todo", 0, 2, 6, 10L),
            new Spec("E2-06", "看板状态刷新成员图", "项目经理", "看板上改状态后成员图跟着刷新", "人力视图不落后", "Must", "todo", 0, 2, 6, 11L),
            new Spec("E2-07", "视图联动规则", "项目经理", "打开或关闭视图之间的联动", "同步只在需要时发生", "Must", "todo", 0, 3, 7, 12L)
    );

    private final PmProjectMapper projectMapper;
    private final PmStoryMapper storyMapper;
    private final PmTaskMapper taskMapper;
    private final PmTaskStatusLogMapper logMapper;

    public Sprint2TaskInitializer(PmProjectMapper projectMapper, PmStoryMapper storyMapper,
                                  PmTaskMapper taskMapper, PmTaskStatusLogMapper logMapper) {
        this.projectMapper = projectMapper;
        this.storyMapper = storyMapper;
        this.taskMapper = taskMapper;
        this.logMapper = logMapper;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<PmProject> projects = projectMapper.selectList(new LambdaQueryWrapper<PmProject>()
                .orderByAsc(PmProject::getId));
        if (projects.isEmpty()) {
            return;
        }
        Long projectId = projects.get(0).getId();
        LocalDate today = LocalDate.now();
        int added = 0;
        for (Spec spec : SPECS) {
            if (ensure(projectId, spec, today)) {
                added++;
            }
        }
        if (added > 0) {
            log.info("已为项目 {} 补上 {} 条进阶 2 任务", projectId, added);
        }
    }

    private boolean ensure(Long projectId, Spec spec, LocalDate today) {
        Long existingTask = taskMapper.selectCount(new LambdaQueryWrapper<PmTask>()
                .eq(PmTask::getProjectId, projectId)
                .eq(PmTask::getStoryCode, spec.code));
        if (existingTask != null && existingTask > 0) {
            return false;
        }
        PmStory story = storyMapper.selectOne(new LambdaQueryWrapper<PmStory>()
                .eq(PmStory::getProjectId, projectId)
                .eq(PmStory::getStoryCode, spec.code)
                .last("LIMIT 1"));
        if (story == null) {
            story = new PmStory();
            story.setProjectId(projectId);
            story.setStoryCode(spec.code);
            story.setTitle(spec.title);
            story.setAsRole(spec.asRole);
            story.setIWant(spec.iWant);
            story.setSoThat(spec.soThat);
            story.setMoscow(spec.moscow);
            story.setSprintNo(2);
            story.setStatus(spec.status);
            story.setSortOrder(200 + SPECS.indexOf(spec) * 10);
            story.setCreatedAt(LocalDateTime.now());
            story.setUpdatedAt(LocalDateTime.now());
            storyMapper.insert(story);
        }
        PmTask task = new PmTask();
        task.setProjectId(projectId);
        task.setStoryId(story.getId());
        task.setStoryCode(spec.code);
        task.setTitle(spec.title);
        task.setDescription(spec.iWant);
        task.setAssigneeId(spec.assigneeId);
        task.setCreatedBy(10L);
        task.setStatus(spec.status);
        task.setProgress(spec.progress);
        task.setStartDate(today.plusDays(spec.startOffset));
        task.setDueDate(today.plusDays(spec.dueOffset));
        task.setCreatedAt(LocalDateTime.now());
        task.setUpdatedAt(LocalDateTime.now());
        if ("done".equals(spec.status)) {
            task.setDoneAt(LocalDateTime.now());
        }
        taskMapper.insert(task);
        if (!"todo".equals(spec.status)) {
            PmTaskStatusLog row = new PmTaskStatusLog();
            row.setTaskId(task.getId());
            row.setProjectId(projectId);
            row.setFromStatus("todo");
            row.setToStatus(spec.status);
            row.setOperatorId(spec.assigneeId);
            row.setRemark("补上进阶 2 任务");
            row.setCreatedAt(LocalDateTime.now());
            logMapper.insert(row);
        }
        return true;
    }

    private record Spec(String code, String title, String asRole, String iWant, String soThat,
                        String moscow, String status, int progress, int startOffset, int dueOffset,
                        Long assigneeId) {
    }
}
