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

import com.fasterxml.jackson.annotation.JsonInclude;
import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * <p>Unified pagination response structure, compatible with frontend Schema's field naming conventions for table rendering.</p>
 * <p>All pagination APIs must use this structure to enable seamless component reuse on the frontend.</p>
 *
 * @param <T> List element type
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
     * Build a pagination response.
     *
     * @param records Data list
     * @param total   Total record count
     * @param page    Current page number (starting from 1)
     * @param size    Page size
     * @return Pagination response
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
