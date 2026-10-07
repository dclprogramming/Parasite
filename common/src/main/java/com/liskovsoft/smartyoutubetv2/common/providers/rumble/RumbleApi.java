package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * Rumble web access: HTML listing pages, and the embed JSON that holds the stream urls.
 */
public final class RumbleApi {
    private static final String SITE = "https://rumble.com";
    /**
     * Runs inside the rendered page: finds each video card from its link, takes the biggest block that holds only that
     * video, and reads title, picture, channel, length and date from it. Returns "" until the list is complete.
     */
    static final String EXTRACT_SCRIPT =
            "(function(){\n"
            + "  var w=window; w.__pa=(w.__pa||0)+1;\n"
            + "  if(/just a moment|attention required/i.test(document.title||'')) return '';\n"
            + "  var VID=/\\/(v[0-9a-z]{3,12})-([^\\/?#\"']*)\\.html/i;\n"
            + "  function abs(u){try{return new URL(u,location.href).href;}catch(e){return u||'';}}\n"
            + "  function text(el){return el?(el.textContent||'').replace(/\\s+/g,' ').trim():'';}\n"
            + "  var root=document.querySelector('main')||document.querySelector('#main')||document.body;\n"
            + "  function collect(strict){\n"
            + "    var out=[];\n"
            + "    [].forEach.call(root.querySelectorAll('a[href]'),function(a){\n"
            + "      if(!VID.test(a.getAttribute('href')||'')) return;\n"
            + "      if(strict&&a.closest('header,nav,aside,footer,[class*=\"sidebar\"],[class*=\"side-bar\"]')) return;\n"
            + "      out.push(a);\n"
            + "    });\n"
            + "    return out;\n"
            + "  }\n"
            + "  var anchors=collect(true); if(!anchors.length) anchors=collect(false);\n"
            + "  var seen={}, items=[];\n"
            + "  anchors.forEach(function(a){\n"
            + "    var m=VID.exec(a.getAttribute('href')||''); var id=m[1];\n"
            + "    if(seen[id]) return; seen[id]=1;\n"
            + "    var card=a, el=a;\n"
            + "    while(el.parentElement&&el.parentElement!==root&&el.parentElement!==document.body&&el.parentElement!==document.documentElement){\n"
            + "      var p=el.parentElement, ids={}, n=0;\n"
            + "      [].forEach.call(p.querySelectorAll('a[href]'),function(x){var mm=VID.exec(x.getAttribute('href')||'');if(mm&&!ids[mm[1]]){ids[mm[1]]=1;n++;}});\n"
            + "      if(n>1) break;\n"
            + "      card=p; el=p;\n"
            + "    }\n"
            + "    items.push({id:id,slug:m[2],card:card,a:a});\n"
            + "  });\n"
            + "  if(!items.length){ return w.__pa>=8?'[]':''; }\n"
            + "  var AV='address,[class*=\"channel\"],[class*=\"avatar\"],[class*=\"profile\"]';\n"
            + "  function usable(u){return !!u&&u.indexOf('data:')!==0&&!/spacer|blank|pixel|placeholder/i.test(u);}\n"
            + "  function pick(im){\n"
            + "    var c=[im.currentSrc,im.getAttribute('src'),im.getAttribute('data-src'),im.getAttribute('data-lazy-src'),im.getAttribute('data-original'),im.getAttribute('data-thumb'),im.getAttribute('data-image')];\n"
            + "    var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset');\n"
            + "    if(ss){c.push(ss.split(',')[0].trim().split(' ')[0]);}\n"
            + "    for(var j=0;j<c.length;j++){if(usable(c[j])) return abs(c[j]);}\n"
            + "    return '';\n"
            + "  }\n"
            + "  function imgUrl(card){\n"
            + "    var imgs=card.querySelectorAll('img');\n"
            + "    for(var i=0;i<imgs.length;i++){\n"
            + "      if(imgs[i].closest(AV)) continue;\n"
            + "      var u=pick(imgs[i]); if(u) return u;\n"
            + "    }\n"
            + "    var srcs=card.querySelectorAll('source[srcset],source[data-srcset]');\n"
            + "    for(var s=0;s<srcs.length;s++){\n"
            + "      if(srcs[s].closest(AV)) continue;\n"
            + "      var ss=(srcs[s].getAttribute('srcset')||srcs[s].getAttribute('data-srcset')||'').split(',')[0].trim().split(' ')[0];\n"
            + "      if(usable(ss)) return abs(ss);\n"
            + "    }\n"
            + "    var vids=card.querySelectorAll('video[poster],[data-poster]');\n"
            + "    for(var v=0;v<vids.length;v++){\n"
            + "      var pu=vids[v].getAttribute('poster')||vids[v].getAttribute('data-poster');\n"
            + "      if(usable(pu)) return abs(pu);\n"
            + "    }\n"
            + "    var els=card.querySelectorAll('[style*=\"background\"],[class*=\"thumb\"]');\n"
            + "    for(var k=0;k<els.length;k++){\n"
            + "      var bg=(window.getComputedStyle(els[k]).backgroundImage||'')+' '+(els[k].getAttribute('style')||'');\n"
            + "      var mm=/url\\([\"']?([^\"')]+)[\"']?\\)/.exec(bg);\n"
            + "      if(mm&&usable(mm[1])) return abs(mm[1]);\n"
            + "    }\n"
            + "    var copy=card.cloneNode(true);\n"
            + "    [].forEach.call(copy.querySelectorAll(AV),function(n){if(n.parentNode) n.parentNode.removeChild(n);});\n"
            + "    var raw=/https?:\\/\\/[^\\s\"'<>()]+\\.(?:jpe?g|png|webp)(?:\\?[^\\s\"'<>()]*)?/i.exec(copy.outerHTML||'');\n"
            + "    return raw?raw[0]:'';\n"
            + "  }\n"
            + "  function avatarOf(card){\n"
            + "    var imgs=card.querySelectorAll('address img,[class*=\"channel\"] img,[class*=\"avatar\"] img,img[class*=\"avatar\"]');\n"
            + "    for(var i=0;i<imgs.length;i++){var u=pick(imgs[i]); if(u) return u;}\n"
            + "    return '';\n"
            + "  }\n"
            + "  function titleOf(it){\n"
            + "    var card=it.card;\n"
            + "    var t=text(card.querySelector('h1,h2,h3,h4,h5'));\n"
            + "    if(!t){var x=card.querySelector('[class*=\"title\"]:not([class*=\"channel\"])'); t=text(x);}\n"
            + "    if(!t){t=(it.a.getAttribute('title')||'').trim();}\n"
            + "    if(!t){var im=card.querySelector('img[alt]'); t=im?(im.getAttribute('alt')||'').trim():'';}\n"
            + "    if(!t){t=text(it.a);}\n"
            + "    return t;\n"
            + "  }\n"
            + "  var list=items.map(function(it){\n"
            + "    var card=it.card, chId='', chName='';\n"
            + "    var ch=card.querySelector('a[href^=\"/c/\"],a[href^=\"/user/\"],a[href*=\"rumble.com/c/\"],a[href*=\"rumble.com/user/\"]');\n"
            + "    if(ch){\n"
            + "      var mm=/\\/(c|user)\\/([^\\/?#]+)/.exec(ch.getAttribute('href')||'');\n"
            + "      if(mm){chId=mm[1]+'/'+mm[2]; chName=text(card.querySelector('[class*=\"channel__name\"],[class*=\"channel-name\"]'))||text(ch)||mm[2];}\n"
            + "    }\n"
            + "    var dur=0, dm=/(?:(\\d+):)?(\\d+):(\\d{2})/.exec(text(card.querySelector('[class*=\"duration\"]')));\n"
            + "    if(dm){dur=(parseInt(dm[1]||'0',10)*60+parseInt(dm[2],10))*60+parseInt(dm[3],10);}\n"
            + "    var tm=card.querySelector('time[datetime]');\n"
            + "    return {id:it.id,slug:it.slug,title:titleOf(it),thumb:imgUrl(card),channelId:chId,channelName:chName,avatar:avatarOf(card),duration:dur,pub:tm?tm.getAttribute('datetime'):''};\n"
            + "  });\n"
            + "  try{window.scrollTo(0,document.body.scrollHeight);}catch(e){}\n"
            + "  var stable=(w.__pn===list.length); w.__pn=list.length;\n"
            + "  if((stable&&w.__pa>=3)||w.__pa>=9) return JSON.stringify(list);\n"
            + "  return '';\n"
            + "})()\n";

    /** Runs inside the embed page: lists the media urls the page's player has loaded. Returns "" until there is one. */
    private static final String PLAYER_SCRIPT = "(function(){var o={src:'',urls:[],title:document.title||'',image:''};"
            + "var v=document.querySelector('video');"
            + "if(v){try{v.muted=true;v.play();}catch(e){}o.src=v.currentSrc||v.src||'';}"
            + "var b=document.querySelector('[class*=\"play-button\"],[class*=\"PlayButton\"],[class*=\"big-play\"],button[aria-label*=\"lay\"]');"
            + "if(b){try{b.click();}catch(e){}}"
            + "try{performance.getEntriesByType('resource').forEach(function(r){if(/\\.(mp4|m3u8|webm)(\\?|$)/i.test(r.name)){o.urls.push(r.name);}});}catch(e){}"
            + "if(o.src&&o.src.indexOf('blob:')!==0){o.urls.push(o.src);}"
            + "var m=document.querySelector('meta[property=\"og:image\"]');if(m){o.image=m.content;}"
            + "return o.urls.length?JSON.stringify(o):'';})()";
    /** A page with fewer videos than this is the last one. */
    public static final int MIN_FULL_PAGE = 8;

    private RumbleApi() {
    }

    /**
     * Videos listed on a Rumble page (browse, category, channel or search). Page numbers start from 1.
     */
    public static List<RumbleParser.Entry> listing(String path, int page) throws IOException {
        String key = path + "#" + page;
        Object[] cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < CACHE_MS) {
            return (List<RumbleParser.Entry>) cached[1];
        }

        List<RumbleParser.Entry> entries;

        try { // the page as a browser shows it: Rumble fills its lists with scripts
            entries = renderedListing(path, page);

            if (entries.isEmpty() && page == 1) {
                RumbleDebug.emptyRendered(path);
            }
        } catch (IOException renderError) { // no browser available: read the plain page instead
            entries = plainListing(path, page);
        }

        for (RumbleParser.Entry entry : entries) { // remember channel pictures for the channel lists
            if (entry.channelId != null && entry.channelThumb != null) {
                CHANNEL_THUMBS.put(entry.channelId, entry.channelThumb);
            }
        }

        if (!entries.isEmpty()) {
            CACHE.put(key, new Object[]{System.currentTimeMillis(), entries});
        }

        return entries;
    }

    private static final java.util.Map<String, String> CHANNEL_THUMBS = new java.util.concurrent.ConcurrentHashMap<>();

    /** Channel picture seen on any page so far, or null. */
    public static String knownChannelThumb(String channelId) {
        return channelId != null ? CHANNEL_THUMBS.get(channelId) : null;
    }

    private static final long CACHE_MS = 10 * 60 * 1000;
    private static final java.util.Map<String, Object[]> CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    private static String pagedPath(String path, int page) {
        return page > 1 ? path + (path.contains("?") ? "&" : "?") + "page=" + page : path;
    }

    private static List<RumbleParser.Entry> renderedListing(String path, int page) throws IOException {
        return RumbleParser.parseRendered(BrowserFetcher.scrape(SITE + pagedPath(path, page), EXTRACT_SCRIPT, 22, false));
    }

    private static List<RumbleParser.Entry> plainListing(String path, int page) throws IOException {
        String html = RumbleHttp.get(pagedPath(path, page), SITE + "/", false);
        List<RumbleParser.Entry> entries = RumbleParser.parseListing(html);

        if (entries.isEmpty() && page == 1) {
            RumbleDebug.emptyListing(path, html);
        }

        return entries;
    }

    public static String browsePath(String sort, String date) {
        return "/videos?sort=" + sort + "&date=" + date;
    }

    public static String categoryPath(String category) {
        return "/category/" + category;
    }

    public static String channelPath(String channelId) {
        return "/" + channelId;
    }

    public static String searchVideosPath(String text) {
        return "/search/video?q=" + encode(text);
    }

    public static List<ProviderMediaItem> searchChannels(String text) throws IOException {
        return RumbleParser.parseChannels(RumbleHttp.get("/search/channel?q=" + encode(text), SITE + "/", false));
    }

    /**
     * Name and picture of a channel, read from its page.
     */
    public static ProviderMediaItem channelInfo(String channelId) throws IOException {
        return RumbleParser.parseChannelPage(channelId, RumbleHttp.get(channelPath(channelId), SITE + "/", false));
    }

    /**
     * Metadata and stream urls of a video. {@code videoId} is the short embed id, e.g. "v7cwvbs".
     */
    public static RumbleParser.Stream stream(String videoId) throws IOException {
        StringBuilder problems = new StringBuilder();
        String embedId;

        try {
            embedId = embedIdOf(videoId);
        } catch (IOException e) {
            throw new IOException("watch page: " + e.getMessage());
        }

        for (String version : new String[]{"u4", "u3"}) {
            try {
                String json = RumbleHttp.get("/embedJS/" + version + "/?request=video&ver=2&v=" + encode(embedId), SITE + "/embed/" + embedId + "/", true);
                RumbleParser.Stream stream = RumbleParser.parseEmbed(json);

                if (stream != null && (stream.mp4Url != null || stream.hlsUrl != null || stream.webmUrl != null)) {
                    return stream;
                }

                problems.append(version).append(": ").append(stream == null ? "not JSON (" + snippet(json) + ")" : "no stream in answer").append("; ");
            } catch (IOException e) {
                problems.append(version).append(": ").append(e.getMessage()).append("; ");
            }
        }

        // The API refused us: let the embed page's own player fetch the stream, and read it from there
        try {
            RumbleParser.Stream stream = RumbleParser.parseScrape(BrowserFetcher.scrape(SITE + "/embed/" + embedId + "/", PLAYER_SCRIPT, 25, true));

            if (stream != null) {
                return stream;
            }

            problems.append("player: no stream found");
        } catch (IOException e) {
            problems.append("player: ").append(e.getMessage());
        }

        throw new IOException(problems.toString());
    }

    /**
     * Like {@link #listing} but always through the hidden browser, so the signed-in session cookies are sent.
     */
    public static List<RumbleParser.Entry> listingSignedIn(String path, int page) throws IOException {
        List<RumbleParser.Entry> entries;

        try {
            entries = renderedListing(path, page); // the hidden browser holds the login cookies
        } catch (IOException e) {
            entries = RumbleParser.parseListing(RumbleHttp.getWithSession(pagedPath(path, page)));
        }

        for (RumbleParser.Entry entry : entries) {
            if (entry.channelId != null && entry.channelThumb != null) {
                CHANNEL_THUMBS.put(entry.channelId, entry.channelThumb);
            }
        }

        return entries;
    }

    /**
     * Does the home page show the signed-in header? Uses the browser session.
     */
    public static boolean looksSignedIn() throws IOException {
        String html = RumbleHttp.getWithSession("/").toLowerCase(java.util.Locale.US);
        return html.contains("/logout") || html.contains("sign out") || html.contains("log out");
    }

    private static final java.util.Map<String, String> EMBED_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Rumble's player id of a video. {@code videoId} is the page name from the watch address ("v7cwvbs-some-title").
     */
    static String embedIdOf(String videoId) throws IOException {
        String cached = EMBED_IDS.get(videoId);

        if (cached != null) {
            return cached;
        }

        String embedId = RumbleParser.findEmbedId(RumbleHttp.get("/" + videoId + ".html", SITE + "/", false));

        if (embedId == null) {
            throw new IOException("player id not found in the page");
        }

        EMBED_IDS.put(videoId, embedId);
        return embedId;
    }

    /** First characters of an answer, for error messages. */
    private static String snippet(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() > 70 ? flat.substring(0, 70) : flat;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            return value;
        }
    }
}
