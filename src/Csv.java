import java.util.ArrayList;
import java.util.List;

/**
 * Minimal RFC 4180 CSV reader/writer.
 *
 * Supports quoted fields, escaped quotes (""), commas, CRLF/LF line endings,
 * and multiline values. Blank lines are ignored.
 */
public final class Csv {

    private Csv() {
    }

    public static List<List<String>> parse(String text) {
        List<List<String>> records = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return records;
        }

        List<String> record = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        boolean fieldStarted = false;
        int i = 0;

        while (i < text.length()) {
            char c = text.charAt(i);

            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '"') {
                        field.append('"');
                        i += 2;
                    } else {
                        inQuotes = false;
                        i++;
                    }
                } else if (c == '\r') {
                    if (i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                        i++;
                    }
                    field.append('\n');
                    i++;
                } else {
                    field.append(c);
                    i++;
                }
                continue;
            }

            if (c == '"') {
                if (!fieldStarted) {
                    inQuotes = true;
                    fieldStarted = true;
                } else {
                    // A quote in the middle of an unquoted field is literal.
                    field.append('"');
                }
                i++;
            } else if (c == ',') {
                record.add(field.toString());
                field.setLength(0);
                fieldStarted = false;
                i++;
            } else if (c == '\r' || c == '\n') {
                if (c == '\r' && i + 1 < text.length() && text.charAt(i + 1) == '\n') {
                    i++;
                }
                record.add(field.toString());
                addRecord(records, record);
                record = new ArrayList<>();
                field.setLength(0);
                fieldStarted = false;
                i++;
            } else {
                field.append(c);
                fieldStarted = true;
                i++;
            }
        }

        if (inQuotes) {
            throw new IllegalArgumentException("Unterminated quoted field in CSV data.");
        }

        if (fieldStarted || field.length() > 0 || !record.isEmpty()) {
            record.add(field.toString());
            addRecord(records, record);
        }

        return records;
    }

    public static String format(List<List<String>> records) {
        StringBuilder out = new StringBuilder();
        for (List<String> record : records) {
            for (int i = 0; i < record.size(); i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(escape(record.get(i)));
            }
            out.append('\n');
        }
        return out.toString();
    }

    public static String escape(String value) {
        String text = value == null ? "" : value;
        boolean needsQuotes = text.indexOf(',') >= 0
                || text.indexOf('"') >= 0
                || text.indexOf('\n') >= 0
                || text.indexOf('\r') >= 0
                || (!text.isEmpty() && (Character.isWhitespace(text.charAt(0))
                        || Character.isWhitespace(text.charAt(text.length() - 1))));

        if (!needsQuotes) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }

    private static void addRecord(List<List<String>> records, List<String> record) {
        // Ignore blank trailing lines; real data rows always have multiple fields.
        if (record.size() == 1 && record.get(0).isEmpty()) {
            return;
        }
        records.add(new ArrayList<>(record));
    }
}
