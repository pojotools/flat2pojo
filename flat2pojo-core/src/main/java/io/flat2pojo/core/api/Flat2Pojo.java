package io.flat2pojo.core.api;

import io.flat2pojo.core.config.MappingConfig;
import java.util.*;
import java.util.stream.Stream;

public interface Flat2Pojo {
    <T> T convert(Map<String, ?> flatRow, Class<T> type, MappingConfig config);

    // Accept any List of Map<String, ?> (e.g., List<Map<String, Object>> is fine)
    <T> List<T> convertAll(List<? extends Map<String, ?>> flatRows,
                           Class<T> type, MappingConfig config);

    // Iterator version as well
    <T> Stream<T> stream(Iterator<? extends Map<String, ?>> rows,
                         Class<T> type, MappingConfig config);
}