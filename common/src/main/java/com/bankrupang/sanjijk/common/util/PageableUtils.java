package com.bankrupang.sanjijk.common.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

public class PageableUtils {

    private static final int DEFAULT_PAGE = 0;
    private static final int DEFAULT_SIZE = 10;
    private static final Set<Integer> ALLOWED_SIZES = Set.of(10, 30, 50);

    private PageableUtils() {}

    public static Pageable of(int page, int size, String sortField, boolean desc) {
        int validSize = ALLOWED_SIZES.contains(size) ? size : DEFAULT_SIZE;
        int validPage = Math.max(page, DEFAULT_PAGE);
        Sort sort = desc
                ? Sort.by(sortField).descending()
                : Sort.by(sortField).ascending();
        return PageRequest.of(validPage, validSize, sort);
    }

    public static Pageable ofDefault(int page, int size) {
        return of(page, size, "createdAt", true);
    }
}