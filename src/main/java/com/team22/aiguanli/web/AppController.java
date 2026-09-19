package com.team22.aiguanli.web;

import com.team22.aiguanli.common.Constants;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.entity.PmTask;
import com.team22.aiguanli.entity.SysPermission;
import com.team22.aiguanli.entity.SysRole;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.SysUserMapper;
import com.team22.aiguanli.security.LoginUser;
import com.team22.aiguanli.security.RequirePerm;
import com.team22.aiguanli.service.AuthService;
import com.team22.aiguanli.service.ProjectService;
import com.team22.aiguanli.service.RoleAdminService;
import com.team22.aiguanli.service.StatsService;
import com.team22.aiguanli.service.TaskStages;
import com.team22.aiguanli.service.UserAdminService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Controller
public class AppController {

    private final SysUserMapper userMapper;
    private final UserAdminService userAdminService;
    private final RoleAdminService roleAdminService;
    private final ProjectService projectService;
    private final StatsService statsService;
    private final AuthService authService;

    public AppController(SysUserMapper userMapper,
                         UserAdminService userAdminService,
                         RoleAdminService roleAdminService,
                         ProjectService projectService,
                         StatsService statsService,
                         AuthService authService) {
        this.userMapper = userMapper;
        this.userAdminService = userAdminService;
        this.roleAdminService = roleAdminService;
        this.projectService = projectService;
        this.statsService = statsService;
        this.authService = authService;
    }

    @GetMapping("/dashboard")
    public String dashboard(LoginUser me, Model model) {
        List<PmProject> projects = projectService.listProjects();
        PmProject home = projects.isEmpty() ? null : projects.get(0);
        model.addAttribute("pageTitle", "总览");
        model.addAttribute("nav", "dashboard");
        model.addAttribute("projects", projects);
        model.addAttribute("homeProject", home);
        if (home != null) {
            model.addAttribute("trend", statsService.trend(home.getId(), 7));
            model.addAttribute("columns", statsService.board(home.getId()));
            if (me.has("workload:view")) {
                model.addAttribute("workloads", statsService.workload(home.getId()));
            }
        } else {
            model.addAttribute("trend", statsService.emptyTrend());
            model.addAttribute("columns", emptyBoard());
        }
        model.addAttribute("logs", projectService.recentLogs(8));
        model.addAttribute("userNames", nameMap());
        model.addAttribute("taskTitles", projectService.taskTitleMap());
        return "dashboard";
    }

    @GetMapping("/search")
    public String search(@RequestParam(required = false) String q, Model model) {
        model.addAttribute("pageTitle", "搜索");
        model.addAttribute("nav", "search");
        model.addAttribute("q", q);
        model.addAttribute("tasks", projectService.searchTasks(q));
        model.addAttribute("userNames", nameMap());
        model.addAttribute("projectNames", projectNameMap());
        return "search";
    }

    @GetMapping("/my-tasks")
    public String myTasks(LoginUser me, Model model) {
        model.addAttribute("pageTitle", "我的任务");
        model.addAttribute("nav", "mine");
        model.addAttribute("tasks", projectService.listMyTasks(me.getId()));
        model.addAttribute("users", projectService.listActiveUsers());
        model.addAttribute("userNames", nameMap());
        model.addAttribute("projectNames", projectNameMap());
        return "my-tasks";
    }

    @GetMapping("/account")
    public String account(LoginUser me, Model model) {
        model.addAttribute("pageTitle", "账号设置");
        model.addAttribute("nav", "account");
        model.addAttribute("user", userAdminService.get(me.getId()));
        return "account";
    }

    @PostMapping("/account/profile")
    public String updateSelf(LoginUser me,
                             @RequestParam String displayName,
                             @RequestParam(required = false) String studentNo,
                             HttpSession session,
                             RedirectAttributes ra) {
        userAdminService.updateProfile(me.getId(), displayName, studentNo);
        refreshSession(me.getId(), session);
        ra.addFlashAttribute("ok", "个人信息已更新");
        return "redirect:/account";
    }

    @PostMapping("/account/password")
    public String changeOwnPassword(LoginUser me,
                                    @RequestParam String oldPassword,
                                    @RequestParam String newPassword,
                                    RedirectAttributes ra) {
        userAdminService.changeOwnPassword(me.getId(), oldPassword, newPassword);
        ra.addFlashAttribute("ok", "密码已修改，下次登录请用新密码");
        return "redirect:/account";
    }

    @GetMapping("/profile")
    public String profile(LoginUser me, Model model) {
        List<SysPermission> mine = userMapper.selectPermissions(me.getId());
        List<SysPermission> all = roleAdminService.listPermissions();
        Set<String> mineCodes = mine.stream().map(SysPermission::getPermCode).collect(Collectors.toSet());
        Map<String, List<SysPermission>> grouped = new LinkedHashMap<>();
        for (SysPermission perm : all) {
            grouped.computeIfAbsent(perm.getEpic(), k -> new ArrayList<>()).add(perm);
        }
        model.addAttribute("pageTitle", "权限地图");
        model.addAttribute("nav", "profile");
        model.addAttribute("grouped", grouped);
        model.addAttribute("mineCodes", mineCodes);
        model.addAttribute("capability", capability(me));
        return "profile";
    }

    @GetMapping("/users")
    @RequirePerm("user:manage")
    public String users(@RequestParam(required = false) String q, Model model) {
        List<SysUser> users = userAdminService.listUsers();
        if (q != null && !q.isBlank()) {
            String key = q.trim().toLowerCase(Locale.ROOT);
            users = users.stream().filter(u -> contains(u.getUsername(), key)
                    || contains(u.getDisplayName(), key)
                    || contains(u.getStudentNo(), key)).toList();
        }
        model.addAttribute("pageTitle", "用户管理");
        model.addAttribute("nav", "users");
        model.addAttribute("userQuery", q);
        model.addAttribute("users", users);
        model.addAttribute("roles", userAdminService.listRoles());
        model.addAttribute("roleNames", userAdminService.listRoles().stream()
                .collect(Collectors.toMap(SysRole::getId, SysRole::getRoleName)));
        return "users";
    }

    @PostMapping("/users")
    @RequirePerm("user:manage")
    public String createUser(@RequestParam String username,
                             @RequestParam String password,
                             @RequestParam String displayName,
                             @RequestParam Long roleId) {
        userAdminService.createUser(username, password, displayName, roleId);
        return "redirect:/users";
    }

    @PostMapping("/users/{id}/toggle")
    @RequirePerm("user:manage")
    public String toggle(@PathVariable Long id, @RequestParam int active, LoginUser me, RedirectAttributes ra) {
        userAdminService.toggleActive(id, me.getId(), active == 1);
        ra.addFlashAttribute("ok", active == 1 ? "已启用" : "已停用");
        return "redirect:/users/" + id;
    }

    @GetMapping("/users/{id}")
    @RequirePerm("user:manage")
    public String userDetail(@PathVariable Long id, Model model) {
        SysUser user = userAdminService.get(id);
        model.addAttribute("pageTitle", "用户详情");
        model.addAttribute("nav", "users");
        model.addAttribute("user", user);
        model.addAttribute("roleName", userAdminService.roleName(user.getRoleId()));
        model.addAttribute("roles", userAdminService.listRoles());
        model.addAttribute("myTasks", projectService.listMyTasks(id));
        model.addAttribute("projectNames", projectNameMap());
        return "user-detail";
    }

    @PostMapping("/users/{id}/profile")
    @RequirePerm("user:manage")
    public String adminUpdateUser(@PathVariable Long id,
                                  @RequestParam String displayName,
                                  @RequestParam(required = false) String studentNo,
                                  RedirectAttributes ra) {
        userAdminService.updateProfile(id, displayName, studentNo);
        ra.addFlashAttribute("ok", "资料已保存");
        return "redirect:/users/" + id;
    }

    @PostMapping("/users/{id}/role")
    @RequirePerm("user:manage")
    public String adjustRole(@PathVariable Long id,
                             @RequestParam String direction,
                             LoginUser me,
                             RedirectAttributes ra) {
        userAdminService.adjustRole(id, direction, me.getId());
        ra.addFlashAttribute("ok", "up".equals(direction) ? "已上调权限" : "已下调权限");
        return "redirect:/users/" + id;
    }

    @PostMapping("/users/{id}/password")
    @RequirePerm("user:manage")
    public String resetPassword(@PathVariable Long id,
                                @RequestParam String newPassword,
                                LoginUser me,
                                RedirectAttributes ra) {
        userAdminService.resetPassword(id, me.getId(), newPassword);
        ra.addFlashAttribute("ok", "已重置密码，对方下次用新密码登录");
        return "redirect:/users/" + id;
    }

    @GetMapping("/roles")
    @RequirePerm("role:manage")
    public String roles(@RequestParam(required = false) Long roleId, Model model) {
        List<SysRole> roles = roleAdminService.listRoles();
        Long current = roleId != null ? roleId : roles.get(0).getId();
        Map<String, List<SysPermission>> grouped = new LinkedHashMap<>();
        for (SysPermission perm : roleAdminService.listPermissions()) {
            grouped.computeIfAbsent(perm.getEpic(), k -> new ArrayList<>()).add(perm);
        }
        model.addAttribute("pageTitle", "角色权限");
        model.addAttribute("nav", "roles");
        model.addAttribute("roles", roles);
        model.addAttribute("currentRoleId", current);
        model.addAttribute("grouped", grouped);
        model.addAttribute("checked", roleAdminService.codesOf(current));
        return "roles";
    }

    @PostMapping("/roles")
    @RequirePerm("role:manage")
    public String createRole(@RequestParam String name, @RequestParam(required = false) String description) {
        roleAdminService.createRole(name, description);
        return "redirect:/roles";
    }

    @PostMapping("/roles/{id}/permissions")
    @RequirePerm("role:manage")
    public String savePerms(@PathVariable Long id, @RequestParam(required = false) List<Long> permIds) {
        roleAdminService.savePermissions(id, permIds);
        return "redirect:/roles?roleId=" + id;
    }

    @GetMapping("/projects")
    @RequirePerm("project:view")
    public String projects(Model model) {
        model.addAttribute("pageTitle", "项目");
        model.addAttribute("nav", "projects");
        model.addAttribute("projects", projectService.listProjects());
        return "projects";
    }

    @PostMapping("/projects")
    @RequirePerm("project:create")
    public String createProject(@RequestParam String name,
                                @RequestParam(required = false) String projectCode,
                                @RequestParam(required = false) String description,
                                LoginUser me) {
        projectService.createProject(projectCode, name, description, me.getId());
        return "redirect:/projects";
    }

    @GetMapping("/work")
    @RequirePerm("project:view")
    public String workAll(@RequestParam(required = false) Long projectId,
                          @RequestParam(required = false) String status,
                          @RequestParam(required = false) Long assigneeId,
                          @RequestParam(required = false) String keyword,
                          Model model) {
        return fillWork(projectId, status, assigneeId, keyword, model);
    }

    @GetMapping({"/projects/{id}/tasks", "/projects/{id}/board"})
    @RequirePerm("project:view")
    public String workProject(@PathVariable Long id,
                              @RequestParam(required = false) String status,
                              @RequestParam(required = false) Long assigneeId,
                              @RequestParam(required = false) String keyword,
                              Model model) {
        return fillWork(id, status, assigneeId, keyword, model);
    }

    @PostMapping("/work/tasks")
    @RequirePerm("task:create")
    public String createFromWork(@RequestParam Long projectId,
                                 @RequestParam String title,
                                 @RequestParam(required = false) String description,
                                 @RequestParam(required = false) Long assigneeId,
                                 @RequestParam(required = false) String storyCode,
                                 @RequestParam(required = false) String stage,
                                 LoginUser me) {
        projectService.createTask(projectId, title, description, assigneeId, storyCode, stage, me);
        return "redirect:/work?projectId=" + projectId;
    }

    @PostMapping("/projects/{id}/tasks")
    @RequirePerm("task:create")
    public String createTask(@PathVariable Long id,
                             @RequestParam String title,
                             @RequestParam(required = false) String description,
                             @RequestParam(required = false) Long assigneeId,
                             @RequestParam(required = false) String storyCode,
                             @RequestParam(required = false) String stage,
                             LoginUser me) {
        projectService.createTask(id, title, description, assigneeId, storyCode, stage, me);
        return "redirect:/projects/" + id + "/tasks";
    }

    @PostMapping("/tasks/{id}/assign")
    @RequirePerm("task:assign")
    public String assign(@PathVariable Long id, @RequestParam Long assigneeId,
                         @RequestParam Long projectId,
                         @RequestParam(defaultValue = "tasks") String back,
                         @RequestParam(required = false) String q,
                         LoginUser me) {
        projectService.assign(id, assigneeId, me);
        return backTo(back, projectId, q);
    }

    @PostMapping("/tasks/{id}/status")
    public String changeStatus(@PathVariable Long id,
                               @RequestParam String status,
                               @RequestParam Long projectId,
                               @RequestParam(defaultValue = "tasks") String back,
                               @RequestParam(required = false) String q,
                               LoginUser me) {
        projectService.changeStatus(id, status, me);
        return backTo(back, projectId, q);
    }

    @GetMapping("/projects/{id}/trend")
    @RequirePerm("trend:view")
    public String trend(@PathVariable Long id,
                        @RequestParam(defaultValue = "7") int days,
                        LoginUser me,
                        Model model) {
        int window = days == 14 ? 14 : 7;
        model.addAttribute("pageTitle", "趋势");
        model.addAttribute("nav", "trend");
        model.addAttribute("project", projectService.getProject(id));
        model.addAttribute("days", window);
        model.addAttribute("trend", statsService.trend(id, window));
        if (me.has("workload:view")) {
            model.addAttribute("workloads", statsService.workload(id));
        }
        return "trend";
    }

    @GetMapping("/projects/{id}/timeline")
    @RequirePerm("board:view")
    public String timeline(@PathVariable Long id, Model model) {
        model.addAttribute("pageTitle", "状态时间轴");
        model.addAttribute("nav", "timeline");
        model.addAttribute("project", projectService.getProject(id));
        model.addAttribute("logs", projectService.timeline(id));
        model.addAttribute("userNames", nameMap());
        model.addAttribute("taskTitles", taskTitleMap(id));
        return "timeline";
    }

    @GetMapping("/coming/{page}")
    public String coming(@PathVariable String page,
                         @RequestParam(required = false) Long projectId,
                         Model model) {
        String title;
        String sprint;
        String summary;
        switch (page) {
            case "gantt" -> {
                title = "甘特图";
                sprint = "Sprint 2 · 一体化";
                summary = "按时间条看任务起止。本轮只留入口和画布，不写排期算法。";
            }
            case "story-map" -> {
                title = "故事地图";
                sprint = "Sprint 2 · 一体化";
                summary = "按史诗 / 故事看发布切片。本轮只留三列骨架。";
            }
            case "uml" -> {
                title = "UML";
                sprint = "Sprint 2 · 一体化";
                summary = "从任务和故事生成结构图。本轮只留画布。";
            }
            case "ai" -> {
                title = "AI 助手";
                sprint = "Sprint 3 · AI化";
                summary = "拆解、预测、预警都走这里。本轮只留对话框位置。";
            }
            default -> {
                title = "后续能力";
                sprint = "未排期";
                summary = "还没有对应的后两阶页面。";
            }
        }
        model.addAttribute("pageTitle", title);
        model.addAttribute("nav", "coming");
        model.addAttribute("comingPage", page);
        model.addAttribute("comingTitle", title);
        model.addAttribute("comingSprint", sprint);
        model.addAttribute("comingSummary", summary);
        model.addAttribute("projectId", projectId);
        return "coming";
    }

    private String fillWork(Long projectId, String status, Long assigneeId, String keyword, Model model) {
        List<PmTask> tasks = projectService.listAllTasks(projectId, status, assigneeId, keyword);
        model.addAttribute("pageTitle", "任务看板");
        model.addAttribute("nav", "work");
        model.addAttribute("project", projectId == null ? null : projectService.getProject(projectId));
        model.addAttribute("projects", projectService.listProjects());
        model.addAttribute("stages", projectService.groupByStage(tasks));
        model.addAttribute("stageLabels", Map.of(
                "e1", TaskStages.label("e1"),
                "e2", TaskStages.label("e2"),
                "e3", TaskStages.label("e3")));
        model.addAttribute("stageHints", Map.of(
                "e1", TaskStages.hint("e1"),
                "e2", TaskStages.hint("e2"),
                "e3", TaskStages.hint("e3")));
        model.addAttribute("users", projectService.listActiveUsers());
        model.addAttribute("userNames", nameMap());
        model.addAttribute("projectNames", projectNameMap());
        model.addAttribute("status", status);
        model.addAttribute("assigneeId", assigneeId);
        model.addAttribute("keyword", keyword);
        model.addAttribute("projectId", projectId);
        return "work";
    }

    private void refreshSession(Long userId, HttpSession session) {
        SysUser user = userAdminService.get(userId);
        session.setAttribute(Constants.SESSION_USER, authService.toLoginUser(user));
    }

    private String backTo(String back, Long projectId, String q) {
        if ("board".equals(back) || "work".equals(back)) {
            return "redirect:/projects/" + projectId + "/tasks";
        }
        if ("mine".equals(back)) {
            return "redirect:/my-tasks";
        }
        if ("search".equals(back)) {
            return "redirect:/search" + (q == null || q.isBlank() ? "" : "?q=" + q);
        }
        if ("dash".equals(back)) {
            return "redirect:/dashboard";
        }
        return "redirect:/projects/" + projectId + "/tasks";
    }

    private Map<String, List<PmTask>> emptyBoard() {
        Map<String, List<PmTask>> columns = new LinkedHashMap<>();
        columns.put("todo", new ArrayList<>());
        columns.put("doing", new ArrayList<>());
        columns.put("done", new ArrayList<>());
        return columns;
    }

    private Map<Long, String> nameMap() {
        return userMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<SysUser>())
                .stream()
                .collect(Collectors.toMap(SysUser::getId, SysUser::getDisplayName, (a, b) -> a));
    }

    private Map<Long, String> projectNameMap() {
        return projectService.listProjects().stream()
                .collect(Collectors.toMap(PmProject::getId, PmProject::getName, (a, b) -> a));
    }

    private Map<Long, String> taskTitleMap(Long projectId) {
        return projectService.listTasks(projectId).stream()
                .collect(Collectors.toMap(PmTask::getId, PmTask::getTitle, (a, b) -> a));
    }

    private String capability(LoginUser me) {
        List<String> bits = new ArrayList<>();
        if (me.has("user:manage")) {
            bits.add("管理用户账号");
        }
        if (me.has("role:manage")) {
            bits.add("配置角色权限");
        }
        if (me.has("project:create")) {
            bits.add("创建项目");
        }
        if (me.has("task:create")) {
            bits.add("创建任务");
        }
        if (me.has("task:assign")) {
            bits.add("指定负责人");
        }
        if (me.has("board:view")) {
            bits.add("查看看板");
        }
        if (me.has("trend:view")) {
            bits.add("查看趋势");
        }
        if (me.has("workload:view")) {
            bits.add("查看成员工作量");
        }
        if (me.has("task:update_any")) {
            bits.add("更新任意任务状态");
        } else if (me.has("task:update_own")) {
            bits.add("更新自己负责的任务状态");
        }
        if (bits.isEmpty()) {
            return "当前角色暂无可操作权限。";
        }
        return "你可以" + String.join("、", bits) + "。";
    }

    private boolean contains(String value, String key) {
        return value != null && value.toLowerCase(Locale.ROOT).contains(key);
    }
}
