package de.danoeh.antennapod.shufflepod;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Pure logic for deciding whether an episode's text mentions a person. Matching is case- and
 * accent-insensitive and only counts whole words, so "Mara" does not match "Marathon".
 */
public final class PersonMatcher {
    private PersonMatcher() {
    }

    /**
     * Splits a comma or newline separated list of alternative names, dropping blanks and duplicates.
     */
    public static List<String> parseAliases(String text) {
        List<String> result = new ArrayList<>();
        if (text == null) {
            return result;
        }
        for (String part : text.split("[,\\n]")) {
            String alias = part.trim().replaceAll("\\s+", " ");
            if (!alias.isEmpty() && !result.contains(alias)) {
                result.add(alias);
            }
        }
        return result;
    }

    public static boolean matchesAny(List<String> names, String... texts) {
        for (String text : texts) {
            if (text == null || text.isEmpty()) {
                continue;
            }
            String haystack = normalize(stripHtml(text));
            for (String name : names) {
                if (containsWholeWords(haystack, normalize(name))) {
                    return true;
                }
            }
        }
        return false;
    }

    static boolean containsWholeWords(String haystack, String needle) {
        if (needle.isEmpty()) {
            return false;
        }
        int from = 0;
        while (true) {
            int index = haystack.indexOf(needle, from);
            if (index < 0) {
                return false;
            }
            int end = index + needle.length();
            boolean startOk = index == 0 || !Character.isLetterOrDigit(haystack.charAt(index - 1));
            boolean endOk = end == haystack.length() || !Character.isLetterOrDigit(haystack.charAt(end));
            if (startOk && endOk) {
                return true;
            }
            from = index + 1;
        }
    }

    static String normalize(String text) {
        String decomposed = Normalizer.normalize(text, Normalizer.Form.NFD);
        String withoutAccents = decomposed.replaceAll("\\p{M}+", "");
        String unifiedQuotes = withoutAccents.replace('’', '\'').replace('‘', '\'');
        return unifiedQuotes.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }

    static String stripHtml(String text) {
        return text.replaceAll("<[^>]*>", " ").replace("&nbsp;", " ").replace("&amp;", "&");
    }
}
