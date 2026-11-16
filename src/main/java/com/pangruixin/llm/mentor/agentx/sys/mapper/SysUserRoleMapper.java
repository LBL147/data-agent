package com.pangruixin.llm.mentor.agentx.sys.mapper;

import com.pangruixin.llm.mentor.agentx.sys.entity.SysUserRole;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user_role Mapper（联合主键，不能用 selectById/updateById）。
 */
@Mapper
public interface SysUserRoleMapper extends BaseMapper<SysUserRole> {
}
