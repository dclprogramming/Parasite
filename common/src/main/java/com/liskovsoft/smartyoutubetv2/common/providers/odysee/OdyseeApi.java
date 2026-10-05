package com.liskovsoft.smartyoutubetv2.common.providers.odysee;

import com.liskovsoft.smartyoutubetv2.common.providers.ProviderHttp;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

/**
 * Odysee (LBRY) public web API: claim_search for browsing, lighthouse for text search, CDN for streams.
 */
public final class OdyseeApi {
    public static final int PAGE_SIZE = 24;
    private static final String[] API_HOSTS = {"https://api.na-backend.odysee.com", "https://api.lbry.tv"};
    private static final String[] SEARCH_HOSTS = {"https://lighthouse.odysee.com", "https://lighthouse.odysee.tv", "https://lighthouse.lbry.com"};
    private static final String[] STREAM_HOSTS = {"https://player.odycdn.com", "https://player.odysee.com", "https://cdn.lbryplayer.xyz"};
    private static final String[] MATURE_TAGS = {
            "porn", "porno", "nsfw", "mature", "xxx", "sex", "creampie", "blowjob", "handjob", "vagina", "boobs",
            "big boobs", "big dick", "pussy", "cumshot", "anal", "hard fucking", "ass", "fuck", "hentai"
    };

    private OdyseeApi() {
    }

    /**
     * Description of one claim_search query.
     */
    public static class Query {
        public boolean channels; // search channels instead of videos
        public String[] orderBy;
        public String[] anyTags;
        public String[] channelIds;
        public String[] claimIds;
        public String text;
        public String releaseTime; // e.g. ">1700000000"
        public int pageSize = PAGE_SIZE;
        public int limitPerChannel;

        public Query orderBy(String... orderBy) {
            this.orderBy = orderBy;
            return this;
        }

        public Query tags(String... tags) {
            this.anyTags = tags;
            return this;
        }

        public Query channels(String... channelIds) {
            this.channelIds = channelIds;
            return this;
        }

        public Query claims(String... claimIds) {
            this.claimIds = claimIds;
            return this;
        }

        public Query newerThanDays(int days) {
            releaseTime = ">" + (System.currentTimeMillis() / 1000 - days * 86400L);
            return this;
        }

        public Query perChannel(int limit) {
            limitPerChannel = limit;
            return this;
        }

        public Query size(int pageSize) {
            this.pageSize = pageSize;
            return this;
        }

        JSONObject toParams(int page) throws JSONException {
            JSONObject params = new JSONObject();
            params.put("page", page);
            params.put("page_size", pageSize);
            params.put("no_totals", true);

            if (channels) {
                params.put("claim_type", new JSONArray().put("channel"));
            } else {
                params.put("claim_type", new JSONArray().put("stream"));
                params.put("stream_types", new JSONArray().put("video"));
                params.put("has_source", true);
                params.put("not_tags", toArray(MATURE_TAGS));
            }

            if (orderBy != null) {
                params.put("order_by", toArray(orderBy));
            }
            if (anyTags != null) {
                params.put("any_tags", toArray(anyTags));
            }
            if (channelIds != null) {
                params.put("channel_ids", toArray(channelIds));
            }
            if (claimIds != null) {
                params.put("claim_ids", toArray(claimIds));
            }
            if (text != null) {
                params.put("text", text);
            }
            if (releaseTime != null) {
                params.put("release_time", releaseTime);
            }
            if (limitPerChannel > 0) {
                params.put("limit_claims_per_channel", limitPerChannel);
            }

            return params;
        }
    }

    public static Query videos() {
        return new Query();
    }

    public static Query channelsQuery() {
        Query query = new Query();
        query.channels = true;
        return query;
    }

    /**
     * Runs claim_search and returns claims in the order of the response.
     */
    public static List<OdyseeClaim> claimSearch(Query query, int page) throws IOException {
        JSONObject result = rpc("claim_search", buildParams(query, page));
        return parseClaims(result);
    }

    /**
     * Resolves claims by ids and keeps the order of the passed ids.
     */
    public static List<OdyseeClaim> claimsByIds(List<String> ids, boolean channels) throws IOException {
        List<OdyseeClaim> result = new ArrayList<>();

        if (ids == null || ids.isEmpty()) {
            return result;
        }

        Query query = channels ? channelsQuery() : videos();
        query.claimIds = ids.toArray(new String[0]);
        query.pageSize = Math.min(50, ids.size());
        // ids are exact: do not filter by mature tags etc. in the id lookup
        List<OdyseeClaim> found = claimSearch(query, 1);

        for (String id : ids) {
            for (OdyseeClaim claim : found) {
                if (id.equals(claim.claimId)) {
                    result.add(claim);
                    break;
                }
            }
        }

        return result;
    }

    public static OdyseeClaim claimById(String claimId) throws IOException {
        List<String> ids = new ArrayList<>();
        ids.add(claimId);
        List<OdyseeClaim> claims = claimsByIds(ids, false);

        if (claims.isEmpty()) { // maybe it is a channel
            claims = claimsByIds(ids, true);
        }

        return claims.isEmpty() ? null : claims.get(0);
    }

    /**
     * Text search through the lighthouse service. Returns claim ids.
     */
    public static List<String> searchIds(String text, boolean channels, int from, int size) throws IOException {
        IOException last = null;

        for (String host : SEARCH_HOSTS) {
            try {
                String url = host + "/search?s=" + encode(text) + "&size=" + size + "&from=" + from + "&nsfw=false" +
                        (channels ? "&claimType=channel" : "&claimType=file&mediaType=video");
                JSONArray array = new JSONArray(ProviderHttp.get(url));
                List<String> ids = new ArrayList<>();

                for (int i = 0; i < array.length(); i++) {
                    String id = array.getJSONObject(i).optString("claimId", null);

                    if (id != null && !ids.contains(id)) {
                        ids.add(id);
                    }
                }

                return ids;
            } catch (IOException e) {
                last = e;
            } catch (JSONException e) {
                last = new IOException(e);
            }
        }

        throw last != null ? last : new IOException("Odysee search failed");
    }

    /**
     * Direct mp4 stream url of a free claim. Tries known CDN hosts and returns the first that answers.
     */
    public static String resolveStreamUrl(OdyseeClaim claim) {
        String sdHash6 = claim.sdHash.substring(0, 6);
        String first = null;

        for (String host : STREAM_HOSTS) {
            String[] candidates = {
                    host + "/v6/streams/" + claim.claimId + "/" + sdHash6 + ".mp4", // current web player
                    host + "/api/v4/streams/free/" + encode(claim.name) + "/" + claim.claimId + "/" + sdHash6,
                    host + "/api/v3/streams/free/" + encode(claim.name) + "/" + claim.claimId + "/" + sdHash6 + ".mp4"
            };

            for (String url : candidates) {
                if (first == null) {
                    first = url;
                }

                if (ProviderHttp.isReachable(url)) {
                    return url;
                }
            }
        }

        return first; // let the player report the error
    }

    private static JSONObject buildParams(Query query, int page) throws IOException {
        try {
            return query.toParams(page);
        } catch (JSONException e) {
            throw new IOException(e);
        }
    }

    private static JSONObject rpc(String method, JSONObject params) throws IOException {
        IOException last = null;

        for (String host : API_HOSTS) {
            try {
                JSONObject body = new JSONObject();
                body.put("jsonrpc", "2.0");
                body.put("method", method);
                body.put("params", params);
                body.put("id", 1);

                String response = ProviderHttp.postJson(host + "/api/v1/proxy?m=" + method, body.toString());
                JSONObject json = new JSONObject(response);

                if (json.has("error") && !json.isNull("error")) {
                    last = new IOException("Odysee error: " + json.get("error"));
                    continue;
                }

                JSONObject result = json.optJSONObject("result");

                if (result != null) {
                    return result;
                }
            } catch (IOException e) {
                last = e;
            } catch (JSONException e) {
                last = new IOException(e);
            }
        }

        throw last != null ? last : new IOException("Odysee API is not available");
    }

    private static List<OdyseeClaim> parseClaims(JSONObject result) {
        List<OdyseeClaim> claims = new ArrayList<>();
        JSONArray items = result.optJSONArray("items");

        if (items == null) {
            return claims;
        }

        for (int i = 0; i < items.length(); i++) {
            OdyseeClaim claim = OdyseeClaim.from(items.optJSONObject(i));

            if (claim != null) {
                claims.add(claim);
            }
        }

        return claims;
    }

    private static JSONArray toArray(String[] values) {
        JSONArray array = new JSONArray();

        for (String value : values) {
            array.put(value);
        }

        return array;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
