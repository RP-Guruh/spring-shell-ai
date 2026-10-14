package com.console.demo.design;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MarkdownRenderer {

    public record Rendered(String text, boolean animate) {
    }

    private static final String RESET = "\u001B[0m";
    private static final String BOLD = "\u001B[1m";
    private static final String BOLD_OFF = "\u001B[22m";
    private static final String ITALIC = "\u001B[3m";
    private static final String ITALIC_OFF = "\u001B[23m";
    private static final String STRIKE = "\u001B[9m";
    private static final String STRIKE_OFF = "\u001B[29m";
    private static final String GOLD = "\u001B[33m";
    private static final String CYAN = "\u001B[36m";
    private static final String GRAY = "\u001B[90m";
    private static final String FG_OFF = "\u001B[39m";

    private static final Pattern FENCE = Pattern.compile("^\\s*(```|~~~)\\s*(\\S*).*$");
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.*)$");
    private static final Pattern RULE = Pattern.compile("^\\s{0,3}([-*_])(\\s*\\1){2,}\\s*$");
    private static final Pattern BULLET = Pattern.compile("^(\\s*)[-*+]\\s+(.*)$");
    private static final Pattern NUMBERED = Pattern.compile("^(\\s*)(\\d+)[.)]\\s+(.*)$");
    private static final Pattern QUOTE = Pattern.compile("^\\s{0,3}>\\s?(.*)$");

    private static final Pattern CODE_SPAN = Pattern.compile("`([^`]+)`");
    private static final Pattern BOLD_TEXT = Pattern.compile("\\*\\*(.+?)\\*\\*");
    private static final Pattern ITALIC_TEXT =
            Pattern.compile("(?<![*\\w])\\*(?![\\s*])(.+?)(?<![\\s*])\\*(?![*\\w])");
    private static final Pattern STRIKE_TEXT = Pattern.compile("~~(.+?)~~");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]+)]\\(([^)\\s]+)\\)");
    private static final Pattern PLACEHOLDER = Pattern.compile("\u0000(\\d+)\u0000");
    private static final Pattern LINE_BREAK = Pattern.compile("(?i)<br\\s*/?>");
    private static final Pattern SEPARATOR_CELL = Pattern.compile("^:?-+:?$");

    private static final int MAX_WIDTH = 120;
    private static final int MIN_COLUMN = 6;

    private final IntSupplier widthSupplier;
    private final StringBuilder buffer = new StringBuilder();
    private final List<String> tableRows = new ArrayList<>();

    private boolean inCode;
    private boolean lastBlank = true;
    private boolean pendingBlank;

    public MarkdownRenderer(IntSupplier widthSupplier) {
        this.widthSupplier = widthSupplier;
    }

    // ---------------------------------------------------------------
    // API
    // ---------------------------------------------------------------

    public List<Rendered> accept(String chunk) {
        List<Rendered> out = new ArrayList<>();
        buffer.append(chunk);

        int newline;
        while ((newline = buffer.indexOf("\n")) >= 0) {
            String line = buffer.substring(0, newline);
            buffer.delete(0, newline + 1);
            processLine(stripCarriageReturn(line), out);
        }

        return out;
    }

    public List<Rendered> finish() {
        List<Rendered> out = new ArrayList<>();

        if (buffer.length() > 0) {
            processLine(stripCarriageReturn(buffer.toString()), out);
            buffer.setLength(0);
        }
        flushTable(out);

        return out;
    }

    // ---------------------------------------------------------------
    // Baris
    // ---------------------------------------------------------------

    private void processLine(String line, List<Rendered> out) {
        if (inCode) {
            processCodeLine(line, out);
            return;
        }

        if (isTableRow(line)) {
            tableRows.add(line);
            return;
        }
        flushTable(out);

        if (line.isBlank()) {
            emitBlank(out);
            return;
        }

        Matcher fence = FENCE.matcher(line);
        if (fence.matches()) {
            openCode(fence.group(2), out);
            return;
        }

        Matcher heading = HEADING.matcher(line);
        if (heading.matches()) {
            emitHeading(heading.group(1).length(), heading.group(2), out);
            return;
        }

        if (RULE.matcher(line).matches()) {
            emitLine(out, GRAY + "─".repeat(Math.min(terminalWidth(), 80)) + RESET, true);
            return;
        }

        Matcher bullet = BULLET.matcher(line);
        if (bullet.matches()) {
            emitBullet(bullet.group(1).length(), bullet.group(2), out);
            return;
        }

        Matcher numbered = NUMBERED.matcher(line);
        if (numbered.matches()) {
            String indent = " ".repeat(numbered.group(1).length() + 2);
            emitLine(out, indent + GOLD + numbered.group(2) + "." + FG_OFF + " "
                    + inline(numbered.group(3)), true);
            return;
        }

        Matcher quote = QUOTE.matcher(line);
        if (quote.matches()) {
            emitLine(out, GRAY + "│ " + FG_OFF + inline(quote.group(1)), true);
            return;
        }

        emitLine(out, inline(line), true);
    }

    private void processCodeLine(String line, List<Rendered> out) {
        String trimmed = line.stripLeading();

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            inCode = false;
            emitLine(out, GRAY + "└─" + RESET, false);
            pendingBlank = true;
            return;
        }

        emitLine(out, GRAY + "│ " + FG_OFF + CYAN + line + FG_OFF, false);
    }

    private void openCode(String language, List<Rendered> out) {
        inCode = true;
        ensureBlankBefore(out);

        String label = language.isEmpty() ? "" : " " + language;
        emitLine(out, GRAY + "┌─" + label + RESET, false);
    }

    private void emitHeading(int level, String text, List<Rendered> out) {
        ensureBlankBefore(out);

        String style = level <= 3 ? BOLD + GOLD : BOLD;
        emitLine(out, style + stripInline(text) + RESET, true);
    }

    private void emitBullet(int indent, String text, List<Rendered> out) {
        String symbol = indent == 0 ? "•" : indent <= 4 ? "◦" : "▪";

        emitLine(out, " ".repeat(indent + 2) + GOLD + symbol + FG_OFF + " " + inline(text), true);
    }

    // ---------------------------------------------------------------
    // Spasi antar blok
    // ---------------------------------------------------------------

    private void emitLine(List<Rendered> out, String text, boolean animate) {
        if (pendingBlank && !lastBlank) {
            out.add(new Rendered("\n", true));
        }
        pendingBlank = false;

        out.add(new Rendered(text + "\n", animate));
        lastBlank = false;
    }

    private void emitBlank(List<Rendered> out) {
        if (lastBlank) return;

        out.add(new Rendered("\n", true));
        lastBlank = true;
        pendingBlank = false;
    }

    private void ensureBlankBefore(List<Rendered> out) {
        if (lastBlank) return;

        out.add(new Rendered("\n", true));
        lastBlank = true;
    }

    // ---------------------------------------------------------------
    // Tabel
    // ---------------------------------------------------------------

    private boolean isTableRow(String line) {
        return line.stripLeading().startsWith("|");
    }

    private void flushTable(List<Rendered> out) {
        if (tableRows.isEmpty()) return;

        List<String> rows = new ArrayList<>(tableRows);
        tableRows.clear();

        ensureBlankBefore(out);
        emitLine(out, renderTable(rows), false);
        pendingBlank = true;
    }

    private String renderTable(List<String> rows) {
        List<List<String>> cells = new ArrayList<>();
        List<Character> alignments = new ArrayList<>();
        boolean hasHeader = false;

        for (String row : rows) {
            List<String> parsed = splitRow(row);

            if (isSeparator(parsed)) {
                if (cells.size() == 1 && !hasHeader) {
                    hasHeader = true;
                    for (String cell : parsed) {
                        alignments.add(alignmentOf(cell));
                    }
                }
                continue;
            }

            cells.add(parsed);
        }

        if (cells.isEmpty()) {
            return String.join("\n", rows);
        }

        int columns = 0;
        for (List<String> row : cells) {
            columns = Math.max(columns, row.size());
        }

        List<List<String>> plain = new ArrayList<>();
        for (List<String> row : cells) {
            List<String> normalized = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                String cell = c < row.size() ? row.get(c) : "";
                normalized.add(stripInline(LINE_BREAK.matcher(cell).replaceAll("\n")));
            }
            plain.add(normalized);
        }

        int[] widths = naturalWidths(plain, columns);
        fitToTerminal(widths, longestWords(plain, columns), columns);

        char[] align = new char[columns];
        for (int c = 0; c < columns; c++) {
            align[c] = c < alignments.size() ? alignments.get(c) : 'l';
        }

        List<List<List<String>>> wrapped = new ArrayList<>();
        boolean anyMultiline = false;
        for (List<String> row : plain) {
            List<List<String>> wrappedRow = new ArrayList<>();
            for (int c = 0; c < columns; c++) {
                List<String> lines = wrap(row.get(c), widths[c]);
                wrappedRow.add(lines);
                if (lines.size() > 1) anyMultiline = true;
            }
            wrapped.add(wrappedRow);
        }

        StringBuilder sb = new StringBuilder();
        sb.append(border('┌', '┬', '┐', widths)).append("\n");

        for (int r = 0; r < wrapped.size(); r++) {
            boolean header = hasHeader && r == 0;
            sb.append(rowLines(wrapped.get(r), widths, align, header ? BOLD + GOLD : ""));

            boolean last = r == wrapped.size() - 1;
            if (!last && (header || anyMultiline)) {
                sb.append("\n").append(border('├', '┼', '┤', widths));
            }
            sb.append("\n");
        }

        sb.append(border('└', '┴', '┘', widths));
        return sb.toString();
    }

    private List<String> splitRow(String row) {
        String trimmed = row.strip();
        if (trimmed.startsWith("|")) trimmed = trimmed.substring(1);
        if (trimmed.endsWith("|") && !trimmed.endsWith("\\|")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }

        List<String> cells = new ArrayList<>();
        for (String part : trimmed.split("(?<!\\\\)\\|", -1)) {
            cells.add(part.strip().replace("\\|", "|"));
        }
        return cells;
    }

    private boolean isSeparator(List<String> cells) {
        if (cells.isEmpty()) return false;

        for (String cell : cells) {
            if (!SEPARATOR_CELL.matcher(cell).matches()) return false;
        }
        return true;
    }

    private char alignmentOf(String separatorCell) {
        boolean left = separatorCell.startsWith(":");
        boolean right = separatorCell.endsWith(":");

        if (left && right) return 'c';
        if (right) return 'r';
        return 'l';
    }

    private int[] naturalWidths(List<List<String>> rows, int columns) {
        int[] widths = new int[columns];

        for (List<String> row : rows) {
            for (int c = 0; c < columns; c++) {
                for (String line : row.get(c).split("\n", -1)) {
                    widths[c] = Math.max(widths[c], displayWidth(line));
                }
            }
        }

        for (int c = 0; c < columns; c++) {
            widths[c] = Math.max(widths[c], 1);
        }
        return widths;
    }

    private int[] longestWords(List<List<String>> rows, int columns) {
        int[] longest = new int[columns];

        for (List<String> row : rows) {
            for (int c = 0; c < columns; c++) {
                for (String word : row.get(c).split("\\s+")) {
                    longest[c] = Math.max(longest[c], displayWidth(word));
                }
            }
        }

        for (int c = 0; c < columns; c++) {
            longest[c] = Math.max(longest[c], 1);
        }
        return longest;
    }

    private void fitToTerminal(int[] widths, int[] longestWords, int columns) {
        int budget = terminalWidth() - (3 * columns + 1);
        int total = 0;
        for (int width : widths) total += width;

        int[] minimums = new int[columns];
        for (int c = 0; c < columns; c++) {
            minimums[c] = MIN_COLUMN;
        }

        while (total > budget) {
            int target = widestAbove(widths, longestWords);
            if (target < 0) target = widestAbove(widths, minimums);
            if (target < 0) break;

            widths[target]--;
            total--;
        }
    }

    private int widestAbove(int[] widths, int[] floors) {
        int target = -1;

        for (int c = 0; c < widths.length; c++) {
            if (widths[c] > floors[c] && (target < 0 || widths[c] > widths[target])) {
                target = c;
            }
        }
        return target;
    }

    private String border(char left, char middle, char right, int[] widths) {
        StringBuilder sb = new StringBuilder(GRAY).append(left);

        for (int c = 0; c < widths.length; c++) {
            sb.append("─".repeat(widths[c] + 2));
            sb.append(c < widths.length - 1 ? middle : right);
        }

        return sb.append(RESET).toString();
    }

    private String rowLines(List<List<String>> row, int[] widths, char[] align, String style) {
        int height = 1;
        for (List<String> cell : row) {
            height = Math.max(height, cell.size());
        }

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < height; i++) {
            StringBuilder sb = new StringBuilder();

            for (int c = 0; c < widths.length; c++) {
                String text = i < row.get(c).size() ? row.get(c).get(i) : "";

                sb.append(GRAY).append("│").append(RESET).append(' ')
                        .append(style).append(pad(text, widths[c], align[c])).append(RESET)
                        .append(' ');
            }

            sb.append(GRAY).append("│").append(RESET);
            lines.add(sb.toString());
        }

        return String.join("\n", lines);
    }

    // ---------------------------------------------------------------
    // Inline
    // ---------------------------------------------------------------

    private String inline(String text) {
        List<String> codes = new ArrayList<>();

        String work = replace(CODE_SPAN, text, m -> {
            codes.add(m.group(1));
            return "\u0000" + (codes.size() - 1) + "\u0000";
        });

        work = replace(BOLD_TEXT, work, m -> BOLD + m.group(1) + BOLD_OFF);
        work = replace(ITALIC_TEXT, work, m -> ITALIC + m.group(1) + ITALIC_OFF);
        work = replace(STRIKE_TEXT, work, m -> STRIKE + m.group(1) + STRIKE_OFF);
        work = replace(LINK, work, m -> m.group(1) + GRAY + " (" + m.group(2) + ")" + FG_OFF);

        return replace(PLACEHOLDER, work,
                m -> CYAN + codes.get(Integer.parseInt(m.group(1))) + FG_OFF);
    }

    private String stripInline(String text) {
        String work = replace(CODE_SPAN, text, m -> m.group(1));
        work = replace(BOLD_TEXT, work, m -> m.group(1));
        work = replace(ITALIC_TEXT, work, m -> m.group(1));
        work = replace(STRIKE_TEXT, work, m -> m.group(1));
        return replace(LINK, work, m -> m.group(1));
    }

    private String replace(Pattern pattern, String text, Function<Matcher, String> function) {
        Matcher matcher = pattern.matcher(text);
        StringBuilder sb = new StringBuilder();

        while (matcher.find()) {
            matcher.appendReplacement(sb, Matcher.quoteReplacement(function.apply(matcher)));
        }
        matcher.appendTail(sb);

        return sb.toString();
    }

    // ---------------------------------------------------------------
    // Teks: lebar, bungkus, rata
    // ---------------------------------------------------------------

    private List<String> wrap(String text, int width) {
        List<String> lines = new ArrayList<>();

        for (String paragraph : text.split("\n", -1)) {
            StringBuilder current = new StringBuilder();

            for (String word : paragraph.split("\\s+")) {
                if (word.isEmpty()) continue;

                String piece = word;
                while (displayWidth(piece) > width) {
                    if (current.length() > 0) {
                        lines.add(current.toString());
                        current.setLength(0);
                    }
                    int cut = cutIndex(piece, width);
                    lines.add(piece.substring(0, cut));
                    piece = piece.substring(cut);
                }

                int joined = current.length() == 0
                        ? displayWidth(piece)
                        : displayWidth(current.toString()) + 1 + displayWidth(piece);

                if (joined <= width) {
                    if (current.length() > 0) current.append(' ');
                    current.append(piece);
                } else {
                    lines.add(current.toString());
                    current.setLength(0);
                    current.append(piece);
                }
            }

            lines.add(current.toString());
        }

        return lines;
    }

    private int cutIndex(String text, int width) {
        int used = 0;
        int index = 0;

        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            int w = charWidth(codePoint);

            if (used + w > width) break;

            used += w;
            index += Character.charCount(codePoint);
        }

        return Math.max(index, Character.charCount(text.codePointAt(0)));
    }

    private String pad(String text, int width, char align) {
        int space = Math.max(0, width - displayWidth(text));

        if (align == 'r') return " ".repeat(space) + text;
        if (align == 'c') {
            int left = space / 2;
            return " ".repeat(left) + text + " ".repeat(space - left);
        }
        return text + " ".repeat(space);
    }

    private int displayWidth(String text) {
        int width = 0;

        for (int i = 0; i < text.length(); ) {
            int codePoint = text.codePointAt(i);
            width += charWidth(codePoint);
            i += Character.charCount(codePoint);
        }

        return width;
    }

    private int charWidth(int cp) {
        int type = Character.getType(cp);

        if (type == Character.NON_SPACING_MARK
                || type == Character.ENCLOSING_MARK
                || type == Character.FORMAT
                || cp == 0xFE0F) {
            return 0;
        }

        boolean wide = (cp >= 0x1100 && cp <= 0x115F)
                || (cp >= 0x2E80 && cp <= 0xA4CF)
                || (cp >= 0xAC00 && cp <= 0xD7A3)
                || (cp >= 0xF900 && cp <= 0xFAFF)
                || (cp >= 0xFE30 && cp <= 0xFE6F)
                || (cp >= 0xFF00 && cp <= 0xFF60)
                || (cp >= 0xFFE0 && cp <= 0xFFE6)
                || (cp >= 0x1F300 && cp <= 0x1FAFF);

        return wide ? 2 : 1;
    }

    private int terminalWidth() {
        int width = widthSupplier.getAsInt();
        return width <= 0 ? 80 : Math.min(width, MAX_WIDTH);
    }

    private String stripCarriageReturn(String line) {
        return line.endsWith("\r") ? line.substring(0, line.length() - 1) : line;
    }
}