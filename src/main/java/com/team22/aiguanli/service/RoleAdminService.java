package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.SysPermission;
import com.team22.aiguanli.entity.SysRole;
import com.team22.aiguanli.mapper.SysPermissionMapper;
import com.team22.aiguanli.mapper.SysRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class RoleAdminService {

    private final SysRoleMapper roleMapper;
    private final SysPermissionMapper permissionMapper;

    public RoleAdminService(SysRoleMapper roleMapper, SysPermissionMapper permissionMapper) {
        this.roleMapper = roleMapper;
        this.permissionMapper = permissionMapper;
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public List<SysPermission> listPermissions() {
        return permissionMapper.selectList(new LambdaQueryWrapper<SysPermission>().orderByAsc(SysPermission::getSortOrder));
    }

    public List<String> codesOf(Long roleId) {
        return permissionMapper.selectCodesByRole(roleId);
    }

    public void createRole(String name, String description) {
        if (name == null || name.isBlank()) {
            throw new BizException(400, "角色名不能为空");
        }
        SysRole role = new SysRole();
        role.setRoleCode("CUSTOM_" + System.currentTimeMillis());
        role.setRoleName(name.trim());
        role.setDescription(description);
        role.setIsSystem(0);
        roleMapper.insert(role);
    }

    @Transactional
    public void savePermissions(Long roleId, List<Long> permIds) {
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(404, "角色不存在");
        }
        permissionMapper.deleteRolePerms(roleId);
        if (permIds == null) {
            return;
        }
        for (Long permId : permIds) {
            permissionMapper.insertRolePerm(roleId, permId);
        }
    }
}
