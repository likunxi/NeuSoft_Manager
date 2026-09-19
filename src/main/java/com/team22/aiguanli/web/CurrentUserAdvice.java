package com.team22.aiguanli.web;

import com.team22.aiguanli.common.Constants;
import com.team22.aiguanli.entity.PmProject;
import com.team22.aiguanli.security.LoginUser;
import com.team22.aiguanli.service.ProjectService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.List;

@ControllerAdvice
public class CurrentUserAdvice {

    private final ProjectService projectService;

    public CurrentUserAdvice(ProjectService projectService) {
        this.projectService = projectService;
    }

    @ModelAttribute("me")
    public LoginUser me(HttpSession session) {
        Object value = session.getAttribute(Constants.SESSION_USER);
        return value instanceof LoginUser user ? user : null;
    }

    @ModelAttribute("homeProjectId")
    public Long homeProjectId() {
        List<PmProject> projects = projectService.listProjects();
        return projects.isEmpty() ? null : projects.get(0).getId();
    }
}
