/*
 * Copyright 2026 Brix Platform Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.brix.platform.common.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.io.Serial;
import java.io.Serializable;

/**
 * <p>Unified pagination request object, encapsulating page number, size, and sort fields,
 * eliminating the need for controllers to repeatedly parse parameters.</p>
 * <p>Default page number starts from 1. When not explicitly specified,
 * the minimum pagination configuration recommended in the documentation is used.</p>
 */
public final class PageRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = -5355081423527869775L;

    @NotNull(message = "Page number cannot be null")
    @Min(value = 1, message = "Page number starts from 1")
    private Integer page = 1;

    @NotNull(message = "Page size cannot be null")
    @Min(value = 1, message = "Page size must be at least 1")
    @Max(value = 200, message = "Page size cannot exceed 200")
    private Integer size = 20;

    @NotBlank(message = "Sort field cannot be empty")
    private String sortBy = "id";

    @NotNull(message = "Sort direction cannot be null")
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
     * Calculate pagination offset for data access layer, avoiding repeated calculations.
     *
     * @return offset value
     */
    public int offset() {
        return (Math.max(1, page) - 1) * Math.max(1, size);
    }

    /**
     * Sort direction enum, strictly limited to ASC/DESC to avoid magic strings.
     */
    public enum SortDirection {
        ASC,
        DESC
    }
}
