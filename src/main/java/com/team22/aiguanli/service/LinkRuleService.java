package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.VwLinkRule;
import com.team22.aiguanli.mapper.VwLinkRuleMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LinkRuleService {

    private final VwLinkRuleMapper ruleMapper;
    private final ProjectService projectService;

    public LinkRuleService(VwLinkRuleMapper ruleMapper, ProjectService projectService) {
        this.ruleMapper = ruleMapper;
        this.projectService = projectService;
    }

    public List<VwLinkRule> list(Long projectId) {
        projectService.getProject(projectId);
        return ruleMapper.selectList(new LambdaQueryWrapper<VwLinkRule>()
                .eq(VwLinkRule::getProjectId, projectId)
                .orderByAsc(VwLinkRule::getId));
    }

    public void toggle(Long projectId, Long ruleId, boolean enabled) {
        VwLinkRule rule = ruleMapper.selectById(ruleId);
        if (rule == null || !projectId.equals(rule.getProjectId())) {
            throw new BizException(404, "联动规则不存在");
        }
        rule.setIsEnabled(enabled ? 1 : 0);
        ruleMapper.updateById(rule);
    }

    public static String explain(VwLinkRule rule) {
        if (rule == null) {
            return "";
        }
        if ("story_board".equals(rule.getSourceView()) && "gantt".equals(rule.getTargetView())) {
            return "故事状态变更后，把该故事下的任务改成同一状态，甘特颜色跟着变";
        }
        if ("board".equals(rule.getSourceView()) && "member_map".equals(rule.getTargetView())) {
            return "看板上修改任务状态后，成员任务图按最新状态重画，并留下同步记录";
        }
        return rule.getSourceView() + " → " + rule.getTargetView();
    }
}
