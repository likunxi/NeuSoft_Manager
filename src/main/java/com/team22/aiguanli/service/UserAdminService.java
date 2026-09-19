package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.SysRole;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.SysRoleMapper;
import com.team22.aiguanli.mapper.SysUserMapper;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class UserAdminService {

    private final SysUserMapper userMapper;
    private final SysRoleMapper roleMapper;
    private final PasswordEncoder passwordEncoder;

    public UserAdminService(SysUserMapper userMapper, SysRoleMapper roleMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public List<SysUser> listUsers() {
        return userMapper.selectList(new LambdaQueryWrapper<SysUser>().orderByAsc(SysUser::getId));
    }

    public List<SysRole> listRoles() {
        return roleMapper.selectList(new LambdaQueryWrapper<SysRole>().orderByAsc(SysRole::getId));
    }

    public void createUser(String username, String password, String displayName, Long roleId) {
        if (username == null || username.isBlank() || password == null || password.length() < 6) {
            throw new BizException(400, "用户名不能空，密码至少 6 位");
        }
        Long exists = userMapper.selectCount(new LambdaQueryWrapper<SysUser>().eq(SysUser::getUsername, username));
        if (exists > 0) {
            throw new BizException(400, "用户名已存在");
        }
        if (roleMapper.selectById(roleId) == null) {
            throw new BizException(400, "角色不存在");
        }
        SysUser user = new SysUser();
        user.setUsername(username.trim());
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setDisplayName(displayName == null || displayName.isBlank() ? username : displayName);
        user.setRoleId(roleId);
        user.setIsActive(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.insert(user);
    }

    public void toggleActive(Long id, Long operatorId, boolean active) {
        if (id.equals(operatorId) && !active) {
            throw new BizException(400, "不能停用自己");
        }
        SysUser user = mustGet(id);
        user.setIsActive(active ? 1 : 0);
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public SysUser get(Long id) {
        return mustGet(id);
    }

    public void updateProfile(Long id, String displayName, String studentNo) {
        SysUser user = mustGet(id);
        if (displayName != null && !displayName.isBlank()) {
            user.setDisplayName(displayName.trim());
        }
        user.setStudentNo(studentNo == null || studentNo.isBlank() ? user.getStudentNo() : studentNo.trim());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void changeOwnPassword(Long id, String oldPassword, String newPassword) {
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        SysUser user = mustGet(id);
        String stored = user.getPasswordHash();
        boolean matched = stored != null && stored.startsWith("{plain}")
                ? stored.substring("{plain}".length()).equals(oldPassword)
                : passwordEncoder.matches(oldPassword == null ? "" : oldPassword, stored);
        if (!matched) {
            throw new BizException(400, "原密码不正确");
        }
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public void resetPassword(Long id, Long operatorId, String newPassword) {
        if (id.equals(operatorId)) {
            throw new BizException(400, "改自己的密码请走「账号设置」并验证原密码");
        }
        if (newPassword == null || newPassword.length() < 6) {
            throw new BizException(400, "新密码至少 6 位");
        }
        SysUser user = mustGet(id);
        user.setPasswordHash(passwordEncoder.encode(newPassword));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 权限上调 / 下调：按角色码 ADMIN > PM > 其他。不能调自己，也不能撤掉最后一名管理员。
     */
    public void adjustRole(Long userId, String direction, Long operatorId) {
        if (userId.equals(operatorId)) {
            throw new BizException(400, "不能调整自己的权限");
        }
        SysUser user = mustGet(userId);
        List<SysRole> ordered = listRoles().stream()
                .sorted((a, b) -> Integer.compare(rank(b), rank(a)))
                .toList();
        int idx = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(user.getRoleId())) {
                idx = i;
                break;
            }
        }
        if (idx < 0) {
            throw new BizException(400, "当前角色不在可调整列表里");
        }
        boolean up = "up".equalsIgnoreCase(direction);
        int next = up ? idx - 1 : idx + 1;
        if (next < 0) {
            throw new BizException(400, "已经是最高权限");
        }
        if (next >= ordered.size()) {
            throw new BizException(400, "已经是最低权限");
        }
        SysRole target = ordered.get(next);
        if (!up && rank(roleOf(user.getRoleId())) >= 30 && adminCount() <= 1) {
            throw new BizException(400, "至少保留一名系统管理员");
        }
        user.setRoleId(target.getId());
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    public String roleName(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        return role == null ? "-" : role.getRoleName();
    }

    private SysUser mustGet(Long id) {
        SysUser user = userMapper.selectById(id);
        if (user == null) {
            throw new BizException(404, "用户不存在");
        }
        return user;
    }

    private SysRole roleOf(Long roleId) {
        SysRole role = roleMapper.selectById(roleId);
        if (role == null) {
            throw new BizException(404, "角色不存在");
        }
        return role;
    }

    private int rank(SysRole role) {
        if (role == null || role.getRoleCode() == null) {
            return 10;
        }
        return switch (role.getRoleCode()) {
            case "ADMIN" -> 30;
            case "PM" -> 20;
            default -> 10;
        };
    }

    private long adminCount() {
        List<SysRole> admins = roleMapper.selectList(new LambdaQueryWrapper<SysRole>().eq(SysRole::getRoleCode, "ADMIN"));
        if (admins.isEmpty()) {
            return 0;
        }
        Long adminRoleId = admins.get(0).getId();
        return userMapper.selectCount(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getRoleId, adminRoleId)
                .eq(SysUser::getIsActive, 1));
    }
}
