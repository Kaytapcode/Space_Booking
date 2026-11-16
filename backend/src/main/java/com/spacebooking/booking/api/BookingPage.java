package com.spacebooking.booking.api;

import java.util.List;
import org.springframework.data.domain.Page;

/** OpenAPI `BookingPage` (PageMetadata + content). */
public record BookingPage(int page, int size, long totalElements, int totalPages, List<BookingResponse> content) {

    public static BookingPage from(Page<BookingResponse> page) {
        return new BookingPage(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages(),
                page.getContent());
    }
}
