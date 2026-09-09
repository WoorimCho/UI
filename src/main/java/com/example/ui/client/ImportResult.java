package com.example.ui.client;

import java.util.List;

/** Outcome of a CSV bulk import (both catalogues return this shape). */
public record ImportResult(int rows, int imported, int skipped, List<RowError> errors) {

    public List<RowError> errorsOrEmpty() {
        return errors == null ? List.of() : errors;
    }

    public record RowError(long line, String message) {
    }
}
