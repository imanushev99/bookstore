package com.bookstore.common.api;

import java.util.List;

public class PageResponse<T> {

    private final List<T> content;
    private final int page;
    private final int size;
    private final long totalElements;
    private final long totalPages;

    public PageResponse(List<T> content, int page, int size, long totalElements) {
        if (size <= 0) {
            throw new IllegalArgumentException("Page size must be greater than zero");
        }

        this.content = List.copyOf(content);
        this.page = page;
        this.size = size;
        this.totalElements = totalElements;
        this.totalPages = totalElements / size + (totalElements % size == 0 ? 0 : 1);
    }

    public List<T> getContent() {
        return content;
    }

    public int getPage() {
        return page;
    }

    public int getSize() {
        return size;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public long getTotalPages() {
        return totalPages;
    }
}
