package com.spacebooking.room.domain;

import java.util.List;

/** API token list <-> DB column (domain.md section 11). */
public final class Equipments {

    private Equipments() {
    }

    public static String toColumn(List<String> tokens) {
        return tokens == null ? "" : String.join(",", tokens.stream().distinct().sorted().toList());
    }

    public static List<String> fromColumn(String column) {
        return column == null || column.isEmpty() ? List.of() : List.of(column.split(","));
    }
}
