package com.casino.casinoerp.service;

import java.util.*;
import java.util.function.Function;

/** Bulk reference loading for HR response mapping; maps live only for one response collection. */
final class HrReferenceLookup {
    private HrReferenceLookup() {}

    static <T> Map<UUID, T> load(Collection<UUID> ids,
            Function<Iterable<UUID>, List<T>> loader, Function<T, UUID> id) {
        Set<UUID> uniqueIds = new HashSet<>(ids);
        uniqueIds.remove(null);
        if (uniqueIds.isEmpty()) return Map.of();
        Map<UUID, T> result = new HashMap<>();
        loader.apply(uniqueIds).forEach(value -> result.put(id.apply(value), value));
        return result;
    }
}
