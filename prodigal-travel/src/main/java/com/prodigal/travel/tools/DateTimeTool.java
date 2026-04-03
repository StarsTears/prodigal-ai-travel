package com.prodigal.travel.tools;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.context.i18n.LocaleContextHolder;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * @author Lang
 * @project prodigal-ai-travel
 * @Version: 1.0
 * @description 日期时间工具类 - 可被 AI 自动调用
 * 当用户询问时间相关问题时，AI 会自动调用此工具
 * @since 2026/4/2
 */
public class DateTimeTool {
    /**
     * 获取当前时间的详细信息（包含时区偏移）
     */
    @Tool(description = "Get detailed current time including timezone offset")
    public static String getDetailedCurrentTime() {
        ZoneId userZone = LocaleContextHolder.getTimeZone().toZoneId();
        ZonedDateTime now = ZonedDateTime.now(userZone);

        return String.format("""
            📍 用户时区: %s
            🕐 当前时间: %s
            🔄 UTC偏移: %s
            📅 星期: %s
            """,
                userZone.getId(),
                now.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")),
                now.getOffset(),
                now.format(DateTimeFormatter.ofPattern("EEEE"))
        );
    }
}
