package com.meanmutton.mechgame.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Small text helpers that stand in for String.split, String.format, toLowerCase/toUpperCase and date formatting.
 * Those pull Java's regular expressions, number formatting, locale and currency data and Unicode tables into the
 * browser build (TeaVM compiles in whatever the code reaches); the game only ever needs plain ASCII versions.
 */
public final class Text {
    private Text() {}

    /** Like {@code s.split(sep)} for a single character: trailing empty parts are dropped. */
    public static String[] split(String s, char sep) {
        return split(s, sep, false);
    }

    /**
     * Like {@code s.split(sep)} ({@code keepTrailing} false) or {@code s.split(sep, -1)} (true) for a single
     * character, with the same results: a string without the separator comes back whole (even an empty one), and
     * without keepTrailing, trailing empty parts are dropped.
     */
    public static String[] split(String s, char sep, boolean keepTrailing) {
        List<String> parts = new ArrayList<String>();
        int start = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == sep) {
                parts.add(s.substring(start, i));
                start = i + 1;
            }
        }
        if (parts.isEmpty()) return new String[]{s};
        parts.add(s.substring(start));
        if (!keepTrailing) {
            while (!parts.isEmpty() && parts.get(parts.size() - 1).isEmpty()) parts.remove(parts.size() - 1);
        }
        return parts.toArray(new String[0]);
    }

    /** The words of a string separated by runs of spaces or tabs, like {@code s.trim().split("\\s+")}. */
    public static String[] words(String s) {
        List<String> parts = new ArrayList<String>();
        int start = -1;
        for (int i = 0; i <= s.length(); i++) {
            boolean space = i == s.length() || s.charAt(i) <= ' ';
            if (space && start >= 0) {
                parts.add(s.substring(start, i));
                start = -1;
            } else if (!space && start < 0) start = i;
        }
        if (parts.isEmpty()) parts.add("");
        return parts.toArray(new String[0]);
    }

    /** ASCII lower case (game text is all ASCII). */
    public static String lower(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) if (c[i] >= 'A' && c[i] <= 'Z') c[i] = (char) (c[i] + 32);
        return new String(c);
    }

    /** ASCII upper case (game text is all ASCII). */
    public static String upper(String s) {
        char[] c = s.toCharArray();
        for (int i = 0; i < c.length; i++) if (c[i] >= 'a' && c[i] <= 'z') c[i] = (char) (c[i] - 32);
        return new String(c);
    }

    /**
     * A number with a fixed count of decimals, like {@code String.format("%.2f", v)}: rounded half up on the number
     * as it prints (2.675 gives 2.68 although its binary value is a hair under), hence the tiny nudge.
     */
    public static String fixed(double v, int decimals) {
        long scale = 1;
        for (int i = 0; i < decimals; i++) scale *= 10;
        long n = (long) Math.floor(Math.abs(v) * scale + 0.5 + 1e-9);
        StringBuilder b = new StringBuilder();
        if (v < 0) b.append('-');
        b.append(n / scale);
        if (decimals > 0) {
            String frac = String.valueOf(n % scale);
            b.append('.');
            for (int i = frac.length(); i < decimals; i++) b.append('0');
            b.append(frac);
        }
        return b.toString();
    }

    /** Right-aligns text in a field of the given width, like {@code %7s}. */
    public static String pad(String s, int width) {
        StringBuilder b = new StringBuilder();
        for (int i = s.length(); i < width; i++) b.append(' ');
        return b.append(s).toString();
    }

    /** A moment as "yyyy-MM-dd HH:mm:ss UTC". */
    public static String utc(long millis) {
        long secs = Math.max(0, millis) / 1000L;   // never before 1970 here, so plain division is floor division
        long days = secs / 86400L, rest = secs - days * 86400L;
        // civil date from days since 1970-01-01 (Howard Hinnant's algorithm)
        long z = days + 719468, era = z / 146097L, doe = z - era * 146097;
        long yoe = (doe - doe / 1460 + doe / 36524 - doe / 146096) / 365;
        long y = yoe + era * 400, doy = doe - (365 * yoe + yoe / 4 - yoe / 100), mp = (5 * doy + 2) / 153;
        long d = doy - (153 * mp + 2) / 5 + 1, m = mp < 10 ? mp + 3 : mp - 9;
        if (m <= 2) y++;
        return y + "-" + two(m) + "-" + two(d) + " " + two(rest / 3600) + ":" + two(rest / 60 % 60) + ":" + two(rest % 60) + " UTC";
    }

    private static String two(long v) {
        return v < 10 ? "0" + v : String.valueOf(v);
    }
}
