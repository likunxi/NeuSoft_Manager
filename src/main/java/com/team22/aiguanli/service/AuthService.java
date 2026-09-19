package com.team22.aiguanli.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.common.BizException;
import com.team22.aiguanli.entity.SysPermission;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.SysUserMapper;
import com.team22.aiguanli.security.LoginUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class AuthService {

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public AuthService(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    public LoginUser login(String username, String rawPassword) {
        SysUser user = userMapper.selectOne(new LambdaQueryWrapper<SysUser>()
                .eq(SysUser::getUsername, username));
        if (user == null || !matches(rawPassword, user.getPasswordHash())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getIsActive() == null || user.getIsActive() == 0) {
            throw new BizException(401, "账号已停用");
        }
        return toLoginUser(user);
    }

    public LoginUser toLoginUser(SysUser user) {
        LoginUser login = new LoginUser();
        login.setId(user.getId());
        login.setUsername(user.getUsername());
        login.setDisplayName(user.getDisplayName());
        login.setRoleName(userMapper.selectRoleName(user.getId()));
        login.setPermissions(userMapper.selectPermissions(user.getId()).stream()
                .map(SysPermission::getPermCode)
                .collect(Collectors.toSet()));
        return login;
    }

    private boolean matches(String raw, String stored) {
        if (stored != null && stored.startsWith("{plain}")) {
            return stored.substring("{plain}".length()).equals(raw);
        }
        return passwordEncoder.matches(raw, stored);
    }
}
