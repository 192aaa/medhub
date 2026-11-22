package com.sdas.server.common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 分页响应数据 {@code { list, total, page, pageSize }}。
 *
 * <p>契约来源：.specify/memory/constitution.md 原则 III。</p>
 *
 * @param <T> 列表元素类型
 */
public class PageResult<T> implements Serializable {

    private static final long serialVersionUID = 1L;

    private List<T> list;

    private long total;

    private int page;

    private int pageSize;

    /** 默认构造，供 Jackson 反序列化使用。 */
    public PageResult() {
    }

    /**
     * 构造分页数据。
     *
     * @param list     当前页数据
     * @param total    总记录数
     * @param page     当前页码
     * @param pageSize 每页条数
     */
    public PageResult(List<T> list, long total, int page, int pageSize) {
        this.list = copyOf(list);
        this.total = total;
        this.page = page;
        this.pageSize = pageSize;
    }

    /** @return 当前页数据的只读视图 */
    public List<T> getList() {
        return list == null ? null : Collections.unmodifiableList(list);
    }

    /** @param list 当前页数据 */
    public void setList(List<T> list) {
        this.list = copyOf(list);
    }

    /**
     * 复制外部列表，避免持有调用方可变引用。
     *
     * @param source 源列表
     * @param <E>    元素类型
     * @return 独立副本；源为 {@code null} 时返回 {@code null}
     */
    private static <E> List<E> copyOf(List<E> source) {
        return source == null ? null : new ArrayList<>(source);
    }

    /** @return 总记录数 */
    public long getTotal() {
        return total;
    }

    /** @param total 总记录数 */
    public void setTotal(long total) {
        this.total = total;
    }

    /** @return 当前页码 */
    public int getPage() {
        return page;
    }

    /** @param page 当前页码 */
    public void setPage(int page) {
        this.page = page;
    }

    /** @return 每页条数 */
    public int getPageSize() {
        return pageSize;
    }

    /** @param pageSize 每页条数 */
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize;
    }
}
