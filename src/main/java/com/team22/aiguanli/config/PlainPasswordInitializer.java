package com.team22.aiguanli.config;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.team22.aiguanli.entity.SysUser;
import com.team22.aiguanli.mapper.SysUserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 种子脚本允许 {plain} 密码，启动时刷成 BCrypt，避免库里长期留明文。
 */
@Component
public class PlainPasswordInitializer implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PlainPasswordInitializer.class);

    private final SysUserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public PlainPasswordInitializer(SysUserMapper userMapper, PasswordEncoder passwordEncoder) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<SysUser> users = userMapper.selectList(new LambdaQueryWrapper<SysUser>()
                .likeRight(SysUser::getPasswordHash, "{plain}"));
        for (SysUser user : users) {
            String raw = user.getPasswordHash().substring("{plain}".length());
            user.setPasswordHash(passwordEncoder.encode(raw));
            userMapper.updateById(user);
            log.info("已将用户 {} 的种子密码升级为 BCrypt", user.getUsername());
        }
    }
}
