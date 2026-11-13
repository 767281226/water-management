package com.hongyu.water.annual;

import java.math.BigDecimal;
import java.util.List;

public final class AnnualDtos {

    private AnnualDtos() {
    }

    /**
     * @param onTimeRate 按时完成率（%）：实际完成日期不晚于计划完成日期的存档项目占比
     * @param fillRate   周报填报率（%）：已提交周计划数 / 应填周数
     */
    public record Tiles(int archivedProjects, int publishedScores, BigDecimal avgScore, Integer onTimeRate,
            Integer fillRate, int people) {
    }

    public record MonthPoint(int month, int archived, BigDecimal avgScore) {
    }

    /**
     * @param monthly 1~12 月个人得分（该月已发布项目得分按个人贡献加权平均），无数据为 null
     * @param avg     有得分月份的平均分
     */
    public record PersonRow(Long userId, String name, String employeeNo, String deptName, int projects,
            BigDecimal hours, Integer fillRate, List<BigDecimal> monthly, BigDecimal avg, Integer rank) {
    }

    public record DeptRow(Long deptId, String name, int people, int archivedProjects, BigDecimal avgScore,
            Integer fillRate) {
    }

    public record AnnualResponse(int year, String scope, Tiles tiles, List<MonthPoint> months, List<PersonRow> persons,
            List<DeptRow> depts, PersonRow me) {
    }
}
