package com.pangruixin.llm.mentor.agentx.sys.mapper;

import com.pangruixin.llm.mentor.agentx.sys.entity.SysUserDept;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;

/**
 * sys_user_dept Mapper（联合主键，不能用 selectById/updateById）。
 */
@Mapper
public interface SysUserDeptMapper extends BaseMapper<SysUserDept> {
}
