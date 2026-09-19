package com.team22.aiguanli.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.team22.aiguanli.entity.SysPermission;
import com.team22.aiguanli.entity.SysUser;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SysUserMapper extends BaseMapper<SysUser> {

    @Select("""
            SELECT p.id, p.perm_code, p.perm_name, p.epic, p.description, p.sort_order
            FROM sys_permission p
            JOIN sys_role_permission rp ON rp.permission_id = p.id
            JOIN sys_user u ON u.role_id = rp.role_id
            WHERE u.id = #{userId}
            ORDER BY p.sort_order
            """)
    List<SysPermission> selectPermissions(@Param("userId") Long userId);

    @Select("""
            SELECT r.role_name
            FROM sys_role r
            JOIN sys_user u ON u.role_id = r.id
            WHERE u.id = #{userId}
            """)
    String selectRoleName(@Param("userId") Long userId);
}
