package de.danoeh.antennapod.shufflepod;

import androidx.annotation.Nullable;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Matches episodes known to Pocket Casts to episodes in the app: by audio URL, then by audio file name,
 * then by title (with the publish date to tell reruns apart). Pure logic, no Android or network.
 */
public final class PocketCastsMatcher {
    private static final long MAX_DATE_DIFFERENCE_MS = 3L * 24 * 60 * 60 * 1000;
    private static final long AMBIGUOUS = -2;

    private final Map<String, Long> byUrl = new HashMap<>();
    private final Map<String, Long> byFileName = new HashMap<>();
    private final Map<String, List<Episode>> byTitle = new HashMap<>();

    /**
     * An episode as seen on one side: its ID (the app's item ID; unused for Pocket Casts episodes),
     * audio URL, title and publish time in milliseconds (0 if unknown).
     */
    public static final class Episode {
        final long id;
        final String url;
        final String title;
        final long publishedMs;

        public Episode(long id, @Nullable String url, @Nullable String title, long publishedMs) {
            this.id = id;
            this.url = url;
            this.title = title;
            this.publishedMs = publishedMs;
        }
    }

    public PocketCastsMatcher(List<Episode> localEpisodes) {
        for (Episode local : localEpisodes) {
            String url = normalizeUrl(local.url);
            if (!url.isEmpty()) {
                putUnique(byUrl, url, local.id);
            }
            String fileName = fileName(local.url);
            if (fileName.length() >= 6) {
                putUnique(byFileName, fileName, local.id);
            }
            String title = normalizeTitle(local.title);
            if (!title.isEmpty()) {
                List<Episode> list = byTitle.get(title);
                if (list == null) {
                    list = new ArrayList<>();
                    byTitle.put(title, list);
                }
                list.add(local);
            }
        }
    }

    /**
     * The app's item ID for the Pocket Casts episode, or -1 if there is no single clear match.
     */
    public long match(Episode remote) {
        Long id = byUrl.get(normalizeUrl(remote.url));
        if (id != null && id >= 0) {
            return id;
        }
        id = byFileName.get(fileName(remote.url));
        if (id != null && id >= 0) {
            return id;
        }
        List<Episode> sameTitle = byTitle.get(normalizeTitle(remote.title));
        if (sameTitle == null) {
            return -1;
        }
        if (sameTitle.size() == 1) {
            Episode only = sameTitle.get(0);
            return datesCompatible(only.publishedMs, remote.publishedMs) ? only.id : -1;
        }
        long found = -1;
        for (Episode candidate : sameTitle) {
            if (remote.publishedMs > 0 && candidate.publishedMs > 0
                    && Math.abs(candidate.publishedMs - remote.publishedMs) <= MAX_DATE_DIFFERENCE_MS) {
                if (found != -1) {
                    return -1;
                }
                found = candidate.id;
            }
        }
        return found;
    }

    private static boolean datesCompatible(long a, long b) {
        return a <= 0 || b <= 0 || Math.abs(a - b) <= MAX_DATE_DIFFERENCE_MS;
    }

    private static void putUnique(Map<String, Long> map, String key, long id) {
        Long existing = map.get(key);
        if (existing == null) {
            map.put(key, id);
        } else if (existing != id) {
            map.put(key, AMBIGUOUS);
        }
    }

    /**
     * Lower case, without scheme, query or fragment, so http/https and tracking parameters don't matter.
     */
    public static String normalizeUrl(@Nullable String url) {
        if (url == null) {
            return "";
        }
        String result = url.trim().toLowerCase(Locale.ROOT);
        int cut = indexOfAny(result, '?', '#');
        if (cut >= 0) {
            result = result.substring(0, cut);
        }
        int scheme = result.indexOf("://");
        if (scheme >= 0) {
            result = result.substring(scheme + 3);
        }
        while (result.endsWith("/")) {
            result = result.substring(0, result.length() - 1);
        }
        return result;
    }

    /**
     * The last path segment of the audio URL, which usually survives changes of host or tracking prefix.
     */
    public static String fileName(@Nullable String url) {
        String normalized = normalizeUrl(url);
        int slash = normalized.lastIndexOf('/');
        return slash >= 0 ? normalized.substring(slash + 1) : "";
    }

    /**
     * Lower case letters and digits only, accents removed.
     */
    public static String normalizeTitle(@Nullable String title) {
        if (title == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(title, Normalizer.Form.NFD).toLowerCase(Locale.ROOT);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < decomposed.length(); i++) {
            char c = decomposed.charAt(i);
            if (Character.isLetterOrDigit(c) && Character.getType(c) != Character.NON_SPACING_MARK) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    private static int indexOfAny(String s, char a, char b) {
        int i = s.indexOf(a);
        int j = s.indexOf(b);
        if (i < 0) {
            return j;
        }
        return j < 0 ? i : Math.min(i, j);
    }
}
