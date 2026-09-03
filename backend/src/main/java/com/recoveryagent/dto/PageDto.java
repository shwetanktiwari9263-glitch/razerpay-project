package com.recoveryagent.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Pagination wrapper for list responses
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PageDto<T> {

    private List<T> content;
    private Long totalElements;
    private Integer totalPages;
    private Integer pageNumber;
    private Integer pageSize;
    private Boolean hasNext;
    private Boolean hasPrevious;
}
