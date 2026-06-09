package com.agenthub.repository;

import com.agenthub.model.entity.AgentSession;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface AgentSessionMapper extends BaseMapper<AgentSession> {

    @Select("SELECT * FROM agent_session WHERE user_id = #{userId} ORDER BY created_at DESC")
    List<AgentSession> findByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM agent_session WHERE session_id = #{sessionId} AND user_id = #{userId}")
    AgentSession findBySessionIdAndUserId(@Param("sessionId") String sessionId, @Param("userId") Long userId);
}
