package de.danoeh.antennapod.ui.shufflepod;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import de.danoeh.antennapod.storage.database.ShufflepodPeople;
import okhttp3.HttpUrl;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal client for the Podcast Index API (https://podcastindex-org.github.io/docs-api/).
 * Only the search-by-person call is used. Needs a free API key and secret from api.podcastindex.org.
 */
public final class PodcastIndexClient {
    private static final String BASE_URL = "https://api.podcastindex.org/api/1.0/";
    private static final int MAX_RESULTS = 1000;

    private PodcastIndexClient() {
    }

    /**
     * Episodes where the person is mentioned: person tags, episode title or description, feed owner or author.
     */
    public static List<ShufflepodPeople.RemoteEpisode> searchByPerson(String name, String apiKey, String apiSecret)
            throws IOException {
        HttpUrl base = HttpUrl.parse(BASE_URL + "search/byperson");
        if (base == null) {
            throw new IOException("Bad Podcast Index URL");
        }
        HttpUrl url = base.newBuilder()
                .addQueryParameter("q", name)
                .addQueryParameter("max", String.valueOf(MAX_RESULTS))
                .addQueryParameter("fulltext", "")
                .build();
        String authDate = String.valueOf(System.currentTimeMillis() / 1000);
        Request request = new Request.Builder()
                .url(url)
                .header("X-Auth-Key", apiKey)
                .header("X-Auth-Date", authDate)
                .header("Authorization", sha1Hex(apiKey + apiSecret + authDate))
                .build();
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Podcast Index returned HTTP " + response.code());
            }
            return parse(body.string());
        }
    }

    static List<ShufflepodPeople.RemoteEpisode> parse(String json) throws IOException {
        List<ShufflepodPeople.RemoteEpisode> result = new ArrayList<>();
        try {
            JSONArray items = new JSONObject(json).optJSONArray("items");
            if (items == null) {
                return result;
            }
            for (int i = 0; i < items.length(); i++) {
                JSONObject o = items.optJSONObject(i);
                if (o == null) {
                    continue;
                }
                ShufflepodPeople.RemoteEpisode e = new ShufflepodPeople.RemoteEpisode();
                e.feedUrl = text(o, "feedUrl");
                e.feedTitle = text(o, "feedTitle");
                e.feedImage = text(o, "feedImage");
                e.guid = text(o, "guid");
                e.title = text(o, "title");
                e.description = text(o, "description");
                e.link = text(o, "link");
                e.image = text(o, "image");
                e.enclosureUrl = text(o, "enclosureUrl");
                e.enclosureType = text(o, "enclosureType") != null ? text(o, "enclosureType") : "audio/mpeg";
                e.enclosureLength = o.optLong("enclosureLength", 0);
                e.publishedAtMillis = o.optLong("datePublished", 0) * 1000L;
                e.durationSeconds = o.optInt("duration", 0);
                result.add(e);
            }
        } catch (JSONException e) {
            throw new IOException("Could not read Podcast Index response", e);
        }
        return result;
    }

    private static String text(JSONObject o, String key) {
        if (!o.has(key) || o.isNull(key)) {
            return null;
        }
        String value = o.optString(key, "");
        return value.isEmpty() ? null : value;
    }

    private static String sha1Hex(String text) throws IOException {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-1").digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16)).append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IOException(e);
        }
    }
}
