package com.hades.client.util;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

/**
 * Inspects any given packet object via reflection and extracts all primitive
 * and String field values for debugging purposes.
 * Field names are resolved through {@link PacketFieldMapper} for readability.
 */
public class PacketInspector {

    /**
     * Returns a list of strings in the form "readableName=value" for all
     * readable primitive/String fields in the packet class hierarchy.
     */
    public static List<String> inspect(Object packet) {
        List<String> lines = new ArrayList<>();
        if (packet == null)
            return lines;

        Class<?> clazz = packet.getClass();

        // Derive the simple obfuscated class key (e.g. "ip$a")
        String fullName = clazz.getName();
        int lastDot = fullName.lastIndexOf('.');
        String simpleClass = (lastDot == -1) ? fullName : fullName.substring(lastDot + 1);

        // Walk up the class hierarchy to pick up parent fields (e.g. ip$a parent ip)
        Class<?> walker = clazz;
        while (walker != null && walker != Object.class) {
            for (Field field : walker.getDeclaredFields()) {
                try {
                    field.setAccessible(true);
                    String obfName = field.getName();
                    // Resolve to human-readable name
                    String readableName = PacketFieldMapper.resolve(simpleClass, obfName);

                    Class<?> type = field.getType();
                    String value;

                    if (type == double.class)
                        value = String.format("%.4f", field.getDouble(packet));
                    else if (type == float.class)
                        value = String.format("%.3f", field.getFloat(packet));
                    else if (type == int.class)
                        value = String.valueOf(field.getInt(packet));
                    else if (type == long.class)
                        value = String.valueOf(field.getLong(packet));
                    else if (type == boolean.class)
                        value = String.valueOf(field.getBoolean(packet));
                    else if (type == byte.class)
                        value = String.valueOf(field.getByte(packet));
                    else if (type == short.class)
                        value = String.valueOf(field.getShort(packet));
                    else if (type == String.class) {
                        Object v = field.get(packet);
                        value = v == null ? "null" : "\"" + v + "\"";
                    } else {
                        Object v = field.get(packet);
                        value = v == null ? "null" : v.getClass().getSimpleName();
                    }

                    lines.add(readableName + "=" + value);
                } catch (Exception ignored) {
                }
            }
            walker = walker.getSuperclass();
        }
        return lines;
    }

    /**
     * Returns a compact single-line summary of all fields, joined by ", ".
     */
    public static String summary(Object packet) {
        List<String> parts = inspect(packet);
        if (parts.isEmpty())
            return "(no fields)";
        StringBuilder sb = new StringBuilder();
        for (String p : parts) {
            if (sb.length() > 0)
                sb.append(", ");
            sb.append(p);
        }
        String result = sb.toString();
        return result.length() > 120 ? result.substring(0, 117) + "..." : result;
    }
}
