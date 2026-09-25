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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
    private static final String CATALOG_URL = "https://podcast-api.pocketcasts.com/podcast/full/%s";
    private static final String PAGED_CATALOG_URL = "https://cache.pocketcasts.com/podcast/full/%s/%d/2/1000";
    private static final int MAX_CATALOG_PAGES = 30;
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String token;
    private boolean episodeLookupAvailable = true;

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

    /**
     * Uses a token copied from the web player, for accounts that sign in with Google or Apple.
     */
    public static PocketCastsClient fromToken(String token) {
        String trimmed = token.trim();
        if (trimmed.regionMatches(true, 0, "Bearer ", 0, 7)) {
            trimmed = trimmed.substring(7).trim();
        }
        return new PocketCastsClient(trimmed);
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
     * Combines the full catalog with the paged one, whose pages hold 1000 episodes each.
     */
    public Map<String, CatalogEpisode> getCatalog(String podcastUuid) throws IOException {
        Map<String, CatalogEpisode> result = new HashMap<>();
        IOException last = null;
        try {
            addAll(result, parseCatalog(get(String.format(Locale.ROOT, CATALOG_URL, podcastUuid))));
        } catch (IOException e) {
            last = e;
        }
        for (int page = 0; page < MAX_CATALOG_PAGES; page++) {
            JSONObject json;
            try {
                json = get(String.format(Locale.ROOT, PAGED_CATALOG_URL, podcastUuid, page));
            } catch (IOException e) {
                last = e;
                break;
            }
            List<CatalogEpisode> episodes = parseCatalog(json);
            int before = result.size();
            addAll(result, episodes);
            if (episodes.isEmpty() || result.size() == before || !json.optBoolean("has_more_episodes", false)) {
                break;
            }
        }
        if (result.isEmpty() && last != null) {
            throw last;
        }
        return result;
    }

    /**
     * The last episodes in the account's listening history, with titles and dates.
     */
    public Map<String, CatalogEpisode> getHistory() throws IOException {
        Map<String, CatalogEpisode> result = new HashMap<>();
        addAll(result, parseCatalog(post(API + "user/history", new JSONObject(), token)));
        return result;
    }

    /**
     * Looks up one episode's title and date. Returns null if Pocket Casts doesn't know it; stops asking
     * for the rest of the import if the lookup isn't supported.
     */
    @Nullable
    public CatalogEpisode getEpisode(String episodeUuid, String podcastUuid) {
        if (!episodeLookupAvailable) {
            return null;
        }
        JSONObject body = new JSONObject();
        try {
            body.put("uuid", episodeUuid);
            body.put("podcast", podcastUuid);
            JSONObject response = post(API + "user/episode", body, token);
            JSONObject episode = response.optJSONObject("episode") != null ? response.optJSONObject("episode")
                    : response;
            List<CatalogEpisode> parsed = parseCatalog(wrap(episode));
            return parsed.isEmpty() ? null : parsed.get(0);
        } catch (JSONException e) {
            return null;
        } catch (IOException e) {
            if (e instanceof HttpException && ((HttpException) e).code >= 400 && ((HttpException) e).code < 500
                    && ((HttpException) e).code != 404) {
                episodeLookupAvailable = false;
            }
            return null;
        }
    }

    private static JSONObject wrap(JSONObject episode) throws JSONException {
        JSONArray array = new JSONArray();
        array.put(episode);
        return new JSONObject().put("episodes", array);
    }

    private static void addAll(Map<String, CatalogEpisode> map, List<CatalogEpisode> episodes) {
        for (CatalogEpisode episode : episodes) {
            map.put(episode.uuid, episode);
        }
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
                throw new HttpException(401, "Pocket Casts rejected the sign-in (email, password or token)");
            }
            if (!response.isSuccessful() || body == null) {
                throw new HttpException(response.code(), "Pocket Casts returned HTTP " + response.code());
            }
            return new JSONObject(body.string());
        } catch (JSONException e) {
            throw new IOException("Could not read the Pocket Casts response", e);
        }
    }

    static final class HttpException extends IOException {
        private static final long serialVersionUID = 1L;
        final int code;

        HttpException(int code, String message) {
            super(message);
            this.code = code;
        }
    }
}
