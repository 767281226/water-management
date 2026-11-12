package com.hongyu.water.weekly;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.hongyu.water.system.config.ConfigService;
import com.hongyu.water.weekly.WeeklyDtos.WeekInfo;

/**
 * 周次与填报截止时间。截止时间来自参数配置，格式"周X HH:mm"或"下周X HH:mm"，格式错误时使用默认值。
 * 节假日顺延规则待接入节假日数据后实现。
 */
@Component
public class WeekRules {

    private static final Pattern DEADLINE = Pattern.compile("^(下)?周([一二三四五六日天])\\s*(\\d{1,2})[:：](\\d{2})$");
    private static final String DAYS = "一二三四五六日";
    private static final DateTimeFormatter MD = DateTimeFormatter.ofPattern("MM.dd");

    private final ConfigService configService;

    public WeekRules(ConfigService configService) {
        this.configService = configService;
    }

    public static LocalDate mondayOf(LocalDate d) {
        return d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    public LocalDate currentMonday() {
        return mondayOf(LocalDate.now());
    }

    public LocalDateTime planDeadline(LocalDate weekStart) {
        return deadline(weekStart, configService.get("fill.plan.deadline", ""), 0, LocalTime.of(23, 59));
    }

    public LocalDateTime reportDeadline(LocalDate weekStart) {
        return deadline(weekStart, configService.get("fill.report.deadline", ""), 4, LocalTime.of(23, 59));
    }

    public WeekInfo info(LocalDate weekStart) {
        LocalDate end = weekStart.plusDays(6);
        int week = weekStart.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
        int year = weekStart.get(IsoFields.WEEK_BASED_YEAR);
        int offset = (int) ((weekStart.toEpochDay() - currentMonday().toEpochDay()) / 7);
        String label = year + "年第" + week + "周（" + weekStart.format(MD) + " - " + end.format(MD) + "）";
        return new WeekInfo(weekStart, end, year, week, label, planDeadline(weekStart), reportDeadline(weekStart), offset);
    }

    public static LocalDateTime deadline(LocalDate weekStart, String text, int defaultDay, LocalTime defaultTime) {
        Matcher m = DEADLINE.matcher(text == null ? "" : text.trim());
        if (m.matches()) {
            int hour = Integer.parseInt(m.group(3));
            int minute = Integer.parseInt(m.group(4));
            if (hour < 24 && minute < 60) {
                char d = m.group(2).charAt(0) == '天' ? '日' : m.group(2).charAt(0);
                int day = DAYS.indexOf(d) + (m.group(1) != null ? 7 : 0);
                return weekStart.plusDays(day).atTime(hour, minute);
            }
        }
        return weekStart.plusDays(defaultDay).atTime(defaultTime);
    }
}
