package org.biblememory.app;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Word alignment for feedback only. The lesson still grades with AcceptedTextLogic. */
final class AnswerReviewLogic {
    enum Kind { MISSING, EXTRA, SPELLING }
    enum Summary { NONE, EMPTY, SHORT, DIFFERENT }

    static final class Issue {
        final Kind kind;
        final String expected, actual;
        Issue(Kind kind, String expected, String actual) {
            this.kind = kind;
            this.expected = expected;
            this.actual = actual;
        }
    }

    static final class Result {
        final Summary summary;
        final List<Issue> issues = new ArrayList<>();
        final List<int[]> expectedMarks = new ArrayList<>();
        final List<int[]> actualMarks = new ArrayList<>();
        Result(Summary summary) { this.summary = summary; }
    }

    private static final Pattern WORD = Pattern.compile("[\\p{L}\\p{N}][\\p{L}\\p{N}\\p{M}]*");
    private static final long MAX_ALIGNMENT_CELLS = 200_000;

    private static final class Word {
        final int start, end;
        final String key;
        Word(int start, int end, String key) {
            this.start = start;
            this.end = end;
            this.key = key;
        }
    }

    private static List<Word> words(String text, boolean allowMatthewVariant) {
        List<Word> result = new ArrayList<>();
        Matcher matcher = WORD.matcher(text);
        while (matcher.find()) {
            Word current = new Word(matcher.start(), matcher.end(),
                String.join(" ", ReviewLogic.words(matcher.group())));
            if (allowMatthewVariant && current.key.equals("годна") && !result.isEmpty()) {
                Word previous = result.get(result.size() - 1);
                if (previous.key.equals("не") && text.substring(previous.end, current.start).matches("\\s+")) {
                    result.set(result.size() - 1, new Word(previous.start, current.end, "негодна"));
                    continue;
                }
            }
            result.add(current);
        }
        return result;
    }

    static Result compare(String expected, String actual, boolean allowMatthewVariant) {
        if (actual.trim().isEmpty()) return new Result(Summary.EMPTY);
        List<Word> a = words(expected, allowMatthewVariant), b = words(actual, allowMatthewVariant);
        if (a.size() > 0 && 2L * b.size() < a.size()) return new Result(Summary.SHORT);
        int first = 0, endA = a.size(), endB = b.size();
        while (first < endA && first < endB && equal(a.get(first), b.get(first))) first++;
        while (endA > first && endB > first && equal(a.get(endA - 1), b.get(endB - 1))) {
            endA--;
            endB--;
        }
        int n = endA - first, m = endB - first;
        if ((n + 1L) * (m + 1L) > MAX_ALIGNMENT_CELLS) return new Result(Summary.DIFFERENT);

        int[][] cost = new int[n + 1][m + 1];
        byte[][] choice = new byte[n + 1][m + 1];
        for (int i = 1; i <= n; i++) { cost[i][0] = 2 * i; choice[i][0] = 1; }
        for (int j = 1; j <= m; j++) { cost[0][j] = 2 * j; choice[0][j] = 2; }
        for (int i = 1; i <= n; i++) {
            for (int j = 1; j <= m; j++) {
                int missing = cost[i - 1][j] + 2;
                int extra = cost[i][j - 1] + 2;
                cost[i][j] = Math.min(missing, extra);
                choice[i][j] = (byte) (missing <= extra ? 1 : 2);
                Word x = a.get(first + i - 1), y = b.get(first + j - 1);
                if (equal(x, y)) {
                    cost[i][j] = cost[i - 1][j - 1];
                    choice[i][j] = 0;
                } else if (cost[i - 1][j - 1] + 3 < cost[i][j] && similar(x.key, y.key)) {
                    cost[i][j] = cost[i - 1][j - 1] + 3;
                    choice[i][j] = 3;
                }
            }
        }
        List<Byte> steps = new ArrayList<>();
        int matched = a.size() - n;
        for (int i = n, j = m; i > 0 || j > 0;) {
            byte step = choice[i][j];
            steps.add(step);
            if (step != 2) i--;
            if (step != 1) j--;
            if (step == 0 || step == 3) matched++;
        }
        if (Math.max(a.size(), b.size()) >= 4 && 3L * matched < Math.max(a.size(), b.size()))
            return new Result(Summary.DIFFERENT);
        Collections.reverse(steps);
        Result result = new Result(Summary.NONE);
        int i = first, j = first;
        for (int k = 0; k < steps.size(); k++) {
            byte step = steps.get(k);
            if (step == 0) { i++; j++; continue; }
            int fromA = i, fromB = j;
            do {
                if (step != 2) i++;
                if (step != 1) j++;
                if (step == 3 || k + 1 == steps.size() || steps.get(k + 1) != step) break;
                k++;
            } while (true);
            String wanted = span(expected, a, fromA, i, result.expectedMarks);
            String typed = span(actual, b, fromB, j, result.actualMarks);
            result.issues.add(new Issue(step == 1 ? Kind.MISSING : step == 2 ? Kind.EXTRA : Kind.SPELLING,
                wanted, typed));
        }
        return result;
    }

    private static String span(String original, List<Word> words, int from, int to, List<int[]> marks) {
        if (from == to) return "";
        int start = words.get(from).start, end = words.get(to - 1).end;
        marks.add(new int[]{start, end});
        return original.substring(start, end);
    }

    private static boolean equal(Word a, Word b) { return a.key.equals(b.key); }

    /** One insertion, deletion, replacement or adjacent swap. Never match a lone letter. */
    private static boolean similar(String a, String b) {
        if (Math.min(a.length(), b.length()) < 3 || Math.abs(a.length() - b.length()) > 1) return false;
        if (a.length() > b.length()) return similar(b, a);
        int i = 0;
        while (i < a.length() && a.charAt(i) == b.charAt(i)) i++;
        if (a.length() != b.length()) return a.regionMatches(i, b, i + 1, a.length() - i);
        if (i == a.length()) return false;
        if (a.regionMatches(i + 1, b, i + 1, a.length() - i - 1)) return true;
        return i + 1 < a.length() && a.charAt(i) == b.charAt(i + 1) &&
            a.charAt(i + 1) == b.charAt(i) && a.regionMatches(i + 2, b, i + 2, a.length() - i - 2);
    }
}
