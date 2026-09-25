package de.danoeh.antennapod.ui.shufflepod;

import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import de.danoeh.antennapod.net.common.AntennapodHttpClient;
import okhttp3.MediaType;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * Minimal client for the private API behind the Pocket Casts web player. It is undocumented and may change
 * at any time; it is only used for a one-time import. The password is sent only to Pocket Casts and never
 * stored.
 */
public final class PocketCastsClient {
    public static final int STATUS_PLAYED = 3;
    private static final String API = "https://api.pocketcasts.com/";
    private static final String[] CATALOG_URLS = {
        "https://podcast-api.pocketcasts.com/podcast/full/%s",
        "https://cache.pocketcasts.com/podcast/full/%s/0/2/1000",
    };
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String token;

    private PocketCastsClient(String token) {
        this.token = token;
    }

    public static final class Podcast {
        public String uuid;
        public String title;
        public String url;
    }

    public static final class EpisodeStatus {
        public String uuid;
        public int playingStatus;
        public boolean archived;
    }

    public static final class CatalogEpisode {
        public String uuid;
        public String title;
        public String url;
        public long publishedMs;
    }

    public static PocketCastsClient login(String email, String password) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("email", email);
            body.put("password", password);
            body.put("scope", "webplayer");
        } catch (JSONException e) {
            throw new IOException(e);
        }
        JSONObject response = post(API + "user/login", body, null);
        String token = response.optString("token", "");
        if (token.isEmpty()) {
            throw new IOException("Pocket Casts did not return a login token");
        }
        return new PocketCastsClient(token);
    }

    public List<Podcast> getSubscriptions() throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("v", 1);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        JSONArray podcasts = post(API + "user/podcast/list", body, token).optJSONArray("podcasts");
        List<Podcast> result = new ArrayList<>();
        if (podcasts == null) {
            return result;
        }
        for (int i = 0; i < podcasts.length(); i++) {
            JSONObject o = podcasts.optJSONObject(i);
            if (o != null && o.has("uuid")) {
                Podcast podcast = new Podcast();
                podcast.uuid = o.optString("uuid");
                podcast.title = o.optString("title", "");
                podcast.url = o.optString("url", "");
                result.add(podcast);
            }
        }
        return result;
    }

    public List<EpisodeStatus> getEpisodeStatuses(String podcastUuid) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("uuid", podcastUuid);
        } catch (JSONException e) {
            throw new IOException(e);
        }
        JSONArray episodes = post(API + "user/podcast/episodes", body, token).optJSONArray("episodes");
        List<EpisodeStatus> result = new ArrayList<>();
        if (episodes == null) {
            return result;
        }
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject o = episodes.optJSONObject(i);
            if (o != null && o.has("uuid")) {
                EpisodeStatus status = new EpisodeStatus();
                status.uuid = o.optString("uuid");
                status.playingStatus = o.optInt("playingStatus", 0);
                status.archived = o.optBoolean("isDeleted", false);
                result.add(status);
            }
        }
        return result;
    }

    /**
     * The show's episode list as Pocket Casts knows it (titles, audio URLs, dates), used for matching only.
     */
    public List<CatalogEpisode> getCatalog(String podcastUuid) throws IOException {
        IOException last = null;
        for (String template : CATALOG_URLS) {
            try {
                List<CatalogEpisode> episodes = parseCatalog(get(String.format(Locale.ROOT, template, podcastUuid)));
                if (!episodes.isEmpty()) {
                    return episodes;
                }
            } catch (IOException e) {
                last = e;
            }
        }
        if (last != null) {
            throw last;
        }
        return new ArrayList<>();
    }

    static List<CatalogEpisode> parseCatalog(JSONObject json) {
        JSONObject podcast = json.optJSONObject("podcast");
        JSONArray episodes = podcast != null ? podcast.optJSONArray("episodes") : json.optJSONArray("episodes");
        List<CatalogEpisode> result = new ArrayList<>();
        if (episodes == null) {
            return result;
        }
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject o = episodes.optJSONObject(i);
            if (o != null && o.has("uuid")) {
                CatalogEpisode episode = new CatalogEpisode();
                episode.uuid = o.optString("uuid");
                episode.title = o.optString("title", "");
                episode.url = o.optString("url", "");
                episode.publishedMs = parseDate(o.optString("published", ""));
                result.add(episode);
            }
        }
        return result;
    }

    static long parseDate(String text) {
        if (text == null || text.length() < 19) {
            return 0;
        }
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        try {
            Date date = format.parse(text.substring(0, 19));
            return date != null ? date.getTime() : 0;
        } catch (ParseException e) {
            return 0;
        }
    }

    private static JSONObject post(String url, JSONObject body, @Nullable String token) throws IOException {
        Request.Builder request = new Request.Builder()
                .url(url)
                .post(RequestBody.create(body.toString(), JSON));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        return execute(request.build());
    }

    private static JSONObject get(String url) throws IOException {
        return execute(new Request.Builder().url(url).build());
    }

    private static JSONObject execute(Request request) throws IOException {
        try (Response response = AntennapodHttpClient.getHttpClient().newCall(request).execute()) {
            ResponseBody body = response.body();
            if (response.code() == 401) {
                throw new IOException("Pocket Casts rejected the email or password");
            }
            if (!response.isSuccessful() || body == null) {
                throw new IOException("Pocket Casts returned HTTP " + response.code());
            }
            return new JSONObject(body.string());
        } catch (JSONException e) {
            throw new IOException("Could not read the Pocket Casts response", e);
        }
    }
}
