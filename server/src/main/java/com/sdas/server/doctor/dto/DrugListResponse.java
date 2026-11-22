package com.sdas.server.doctor.dto;

import java.util.List;

/**
 * 端点 5 分页响应（统一 {@code {list,total,page,pageSize}}）。
 *
 * @param list     当前页药品
 * @param total    总条数
 * @param page     页码（≥1）
 * @param pageSize 每页条数
 */
public record DrugListResponse(
        List<DrugView> list,
        long total,
        int page,
        int pageSize) {
}
