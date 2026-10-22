package io.github.byteb8.fleetsim.util;

import java.util.List;
import java.util.Map;

/** Minimal JSON writer for maps, lists, numbers, strings and booleans. */
public final class Json {
    private Json() {}

    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(sb, o, 0);
        return sb.toString();
    }

    private static void write(StringBuilder sb, Object o, int indent) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof String s) {
            sb.append('"');
            for (char c : s.toCharArray()) {
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    default -> sb.append(c);
                }
            }
            sb.append('"');
        } else if (o instanceof Double d) {
            sb.append(d.isNaN() || d.isInfinite() ? "null" : String.format("%.6g", d).replaceAll("\\.?0+$", "").replaceAll("\\.?0+e", "e"));
        } else if (o instanceof Float f) {
            write(sb, (double) f, indent);
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof Map<?, ?> m) {
            sb.append("{\n");
            int i = 0;
            for (Map.Entry<?, ?> e : m.entrySet()) {
                pad(sb, indent + 1);
                write(sb, String.valueOf(e.getKey()), 0);
                sb.append(": ");
                write(sb, e.getValue(), indent + 1);
                if (++i < m.size()) sb.append(',');
                sb.append('\n');
            }
            pad(sb, indent);
            sb.append('}');
        } else if (o instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) sb.append(", ");
                write(sb, l.get(i), indent);
            }
            sb.append(']');
        } else if (o instanceof int[] a) {
            sb.append('[');
            for (int i = 0; i < a.length; i++) {
                if (i > 0) sb.append(", ");
                sb.append(a[i]);
            }
            sb.append(']');
        } else {
            write(sb, o.toString(), indent);
        }
    }

    private static void pad(StringBuilder sb, int n) {
        for (int i = 0; i < n; i++) sb.append("  ");
    }
}
