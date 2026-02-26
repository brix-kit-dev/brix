package io.brix.platform.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * <p>统一的分页返回结构，兼容前端 Schema 在渲染表格时的字段命名约定。</p>
 * <p>所有分页接口务必使用该结构，以便前端可以无缝复用组件。</p>
 *
 * @param <T> 列表元素类型
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class PageResponse<T> implements Serializable {
    @Serial
    private static final long serialVersionUID = -4665367081887703272L;

    private final List<T> records;
    private final long total;
    private final int page;
    private final int size;

    private PageResponse(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
    }

    /**
     * 构建分页响应。
     *
     * @param records 数据列表
     * @param total   总记录数
     * @param page    当前页码（从 1 开始）
     * @param size    每页数量
     * @return 分页响应
     */
    public static <T> PageResponse<T> of(List<T> records, long total, int page, int size) {
        return new PageResponse<>(records == null ? Collections.emptyList() : List.copyOf(records), total, page, size);
    }

    public List<T> getRecords() {
        return records;
    }

    public long getTotal() {
        return total;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }
}
