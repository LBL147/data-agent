package com.pangruixin.llm.mentor.agentx;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * dodo-agentx 启动类。
 * <p>
 * 基于 spring-ai-agentx 框架开发的豆豆智能助手
 */
@SpringBootApplication
@MapperScan({
        "com.pangruixin.llm.mentor.agentx.mapper",
        "com.pangruixin.llm.mentor.agentx.sys.mapper"
})
@EnableScheduling
public class DodoAgentxApplication {

    public static void main(String[] args) {
        SpringApplication.run(DodoAgentxApplication.class, args);
    }
}
