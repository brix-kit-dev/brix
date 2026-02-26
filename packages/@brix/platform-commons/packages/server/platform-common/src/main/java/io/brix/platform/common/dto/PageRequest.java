package io.brix.platform.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * <p>统一分页入参对象，封装页码、大小与排序字段，使控制层无需重复解析参数。</p>
 * <p>默认页码从 1 开始，未显式指定时将采用文档中推荐的最小分页配置。</p>
 */
public final class PageRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -5355081423527869775L;

    @NotNull(message = "页码不能为空")
    @Min(value = 1, message = "页码从 1 开始")
    private Integer page = 1;

    @NotNull(message = "分页大小不能为空")
    @Min(value = 1, message = "分页大小至少为 1")
    @Max(value = 200, message = "分页大小不得超过 200")
    private Integer size = 20;

    @NotBlank(message = "排序字段不能为空")
    private String sortBy = "id";

    @NotNull(message = "排序方式不能为空")
    private SortDirection direction = SortDirection.DESC;

    public PageRequest() {
    }

    public PageRequest(Integer page, Integer size, String sortBy, SortDirection direction) {
        this.page = page;
        this.size = size;
        this.sortBy = sortBy;
        this.direction = direction;
    }

    public Integer getPage() {
        return page;
    }

    public void setPage(Integer page) {
        this.page = page;
    }

    public Integer getSize() {
        return size;
    }

    public void setSize(Integer size) {
        this.size = size;
    }

    public String getSortBy() {
        return sortBy;
    }

    public void setSortBy(String sortBy) {
        this.sortBy = sortBy;
    }

    public SortDirection getDirection() {
        return direction;
    }

    public void setDirection(SortDirection direction) {
        this.direction = direction;
    }

    /**
     * 计算分页偏移量，供数据访问层统一引用，避免重复计算。
     *
     * @return offset 数值
     */
    public int offset() {
        return (Math.max(1, page) - 1) * Math.max(1, size);
    }

    /**
     * 排序方向枚举，严格限定为 ASC/DESC，避免魔法字符串。
     */
    public enum SortDirection {
        ASC,
        DESC
    }
}
