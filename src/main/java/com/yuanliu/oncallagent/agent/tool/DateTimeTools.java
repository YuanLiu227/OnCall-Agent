package com.yuanliu.oncallagent.agent.tool;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContext;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * ClassName:
 * Package: com.yuanliu.oncallagent.agent.tool
 * Description:
 *
 * @Author Yuan Liu
 * @Create 2026/6/1 10:12
 * @Version 1.0
 */
@Component
public class DateTimeTools {

    //工具名常量，用于动态构建提示词
    public static final String TOOL_GET_CURRENT_DATETIME = "getCurrentDateTime";

    @Tool(description = "Get the current date and time in the user's timezone")
    public String getCurrentDateTime() {
        return LocalDateTime.now().atZone(LocaleContextHolder.getTimeZone().toZoneId()).toString();
    }
}
