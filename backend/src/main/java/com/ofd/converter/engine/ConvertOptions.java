package com.ofd.converter.engine;

import java.util.Map;

public record ConvertOptions(String pages, Integer dpi) {
    public static ConvertOptions from(Map<String, Object> m) {
        if (m == null) return new ConvertOptions(null, null);
        Object dpi = m.get("dpi");
        return new ConvertOptions(
            m.get("pages") instanceof String s ? s : null,
            dpi instanceof Number n ? n.intValue() : null);
    }
}
