package com.gunjan;

import io.restassured.path.json.JsonPath;

import java.util.Iterator;
import java.util.List;

public class KeysExtractor {

    public static String getKey(List<String> groupingKeyJsonPaths, Event object)
            throws NoSuchFieldException, IllegalAccessException {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        if (groupingKeyJsonPaths.size() > 0) {
            Iterator<String> it = groupingKeyJsonPaths.iterator();
            appendKeyValue(sb, object, it.next());

            while (it.hasNext()) {
                sb.append(";");
                appendKeyValue(sb, object, it.next());
            }
        }
        sb.append("}");
        return sb.toString();
    }

    private static void appendKeyValue(StringBuilder sb, Event devicePayload, String groupingKeyJsonPath)
            throws IllegalAccessException, NoSuchFieldException {
        final Object fieldValue = JsonPath.from(devicePayload.getPayload().toString()).get(groupingKeyJsonPath);
        sb.append(groupingKeyJsonPath);
        sb.append("=");
        sb.append(fieldValue);
    }
}
