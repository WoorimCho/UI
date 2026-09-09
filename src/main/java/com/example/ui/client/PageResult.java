package com.example.ui.client;

import java.util.List;

/** The stable page envelope the catalogue services return (see their {@code PageResponse<T>}). */
public record PageResult<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last,
        int numberOfElements,
        boolean empty) {

    public List<T> contentOrEmpty() {
        return content == null ? List.of() : content;
    }

    public boolean hasPrevious() {
        return !first;
    }

    public boolean hasNext() {
        return !last;
    }
}
