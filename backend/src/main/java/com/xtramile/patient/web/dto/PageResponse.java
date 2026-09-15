package com.xtramile.patient.web.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Our own pagination envelope.
 *
 * <p>Spring Data's {@code PageImpl} serialises to an unstable, implementation defined JSON shape
 * (Spring Boot even logs a warning about it). Declaring the envelope explicitly gives the Angular
 * client a contract that will not change underneath it when Spring Data is upgraded.
 *
 * @param page zero based index of the returned page
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.isFirst(),
                page.isLast());
    }
}
