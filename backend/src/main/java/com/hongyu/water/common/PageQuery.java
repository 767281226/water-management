package com.hongyu.water.common;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * 分页参数工具：page 从 1 开始，size 限制在 1~200。
 */
public final class PageQuery {

    private PageQuery() {
    }

    public static Pageable of(Integer page, Integer size, Sort sort) {
        int p = page == null || page < 1 ? 1 : page;
        int s = size == null || size < 1 ? 20 : Math.min(size, 200);
        return PageRequest.of(p - 1, s, sort);
    }
}
