package com.hongyu.water.system.role;

/**
 * 数据范围，声明顺序即范围大小（SELF &lt; DEPT &lt; ALL）。
 */
public enum DataScope {
    /** 仅本人 */
    SELF,
    /** 本部门（含下级部门） */
    DEPT,
    /** 全公司 */
    ALL
}
