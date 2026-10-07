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
            + "  var SKIP='header,nav,aside,footer,[class*=\"sidebar\"],[class*=\"side-bar\"]';\n"
            + "  function abs(u){try{return new URL(u,location.href).href;}catch(e){return u||'';}}\n"
            + "  function text(el){return el?(el.textContent||'').replace(/\\s+/g,' ').trim():'';}\n"
            + "  function usable(u){return !!u&&u.indexOf('data:')!==0&&!/spacer|blank|pixel|placeholder/i.test(u);}\n"
            + "  function attr(el,names){for(var i=0;i<names.length;i++){var v=el.getAttribute(names[i]);if(v&&v.trim()) return v.trim();}return '';}\n"
            + "  // light DOM plus open shadow roots\n"
            + "  function deep(root,sel){\n"
            + "    var out=[].slice.call(root.querySelectorAll(sel));\n"
            + "    if(root.shadowRoot){out=out.concat(deep(root.shadowRoot,sel));}\n"
            + "    [].forEach.call(root.querySelectorAll('*'),function(e){if(e.shadowRoot){out=out.concat(deep(e.shadowRoot,sel));}});\n"
            + "    return out;\n"
            + "  }\n"
            + "  var AV='address,[class*=\"channel\"],[class*=\"avatar\"],[class*=\"profile\"]';\n"
            + "  function pick(im){\n"
            + "    var c=[im.currentSrc,im.getAttribute('src'),im.getAttribute('data-src'),im.getAttribute('data-lazy-src'),im.getAttribute('data-original'),im.getAttribute('data-thumb'),im.getAttribute('data-image')];\n"
            + "    var ss=im.getAttribute('srcset')||im.getAttribute('data-srcset');\n"
            + "    if(ss){c.push(ss.split(',')[0].trim().split(' ')[0]);}\n"
            + "    for(var j=0;j<c.length;j++){if(usable(c[j])) return abs(c[j]);}\n"
            + "    return '';\n"
            + "  }\n"
            + "  function imgUrl(card){\n"
            + "    var imgs=deep(card,'img');\n"
            + "    for(var i=0;i<imgs.length;i++){\n"
            + "      if(imgs[i].closest&&imgs[i].closest(AV)) continue;\n"
            + "      var u=pick(imgs[i]); if(u) return u;\n"
            + "    }\n"
            + "    var vids=deep(card,'video[poster],[data-poster]');\n"
            + "    for(var v=0;v<vids.length;v++){\n"
            + "      var pu=vids[v].getAttribute('poster')||vids[v].getAttribute('data-poster');\n"
            + "      if(usable(pu)) return abs(pu);\n"
            + "    }\n"
            + "    var srcs=deep(card,'source[srcset],source[data-srcset]');\n"
            + "    for(var s=0;s<srcs.length;s++){\n"
            + "      if(srcs[s].closest&&srcs[s].closest(AV)) continue;\n"
            + "      var ss=(srcs[s].getAttribute('srcset')||srcs[s].getAttribute('data-srcset')||'').split(',')[0].trim().split(' ')[0];\n"
            + "      if(usable(ss)) return abs(ss);\n"
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
            + "    var imgs=deep(card,'address img,[class*=\"channel\"] img,[class*=\"avatar\"] img,img[class*=\"avatar\"]');\n"
            + "    for(var i=0;i<imgs.length;i++){var u=pick(imgs[i]); if(u) return u;}\n"
            + "    return '';\n"
            + "  }\n"
            + "  function channelLink(card){\n"
            + "    var ch=deep(card,'a[href^=\"/c/\"],a[href^=\"/user/\"],a[href*=\"rumble.com/c/\"],a[href*=\"rumble.com/user/\"]')[0];\n"
            + "    if(!ch) return null;\n"
            + "    var mm=/\\/(c|user)\\/([^\\/?#]+)/.exec(ch.getAttribute('href')||'');\n"
            + "    return mm?{id:mm[1]+'/'+mm[2],name:text(ch)||mm[2],el:ch}:null;\n"
            + "  }\n"
            + "  function durationOf(card,raw){\n"
            + "    if(raw!==undefined&&raw!==''&&/^\\d+$/.test(String(raw))) return parseInt(raw,10);\n"
            + "    var dm=/(?:(\\d+):)?(\\d+):(\\d{2})/.exec(text(card.querySelector('[class*=\"duration\"]')));\n"
            + "    return dm?(parseInt(dm[1]||'0',10)*60+parseInt(dm[2],10))*60+parseInt(dm[3],10):0;\n"
            + "  }\n"
            + "  var root=document.querySelector('main')||document.querySelector('#main')||document.body;\n"
            + "  var list=[], seen={}, strategy='';\n"
            + "\n"
            + "  // 1) Rumble's own card element: <rum-video-thumbnail url=... src=... avatar=... name=... duration=... time=... hints=...>\n"
            + "  var custom=[].slice.call(root.querySelectorAll('[url]')).filter(function(e){\n"
            + "    return e.tagName.indexOf('-')>0&&VID.test(e.getAttribute('url')||'')&&!(e.closest&&e.closest(SKIP));\n"
            + "  });\n"
            + "  if(!custom.length){\n"
            + "    custom=[].slice.call(root.querySelectorAll('[url]')).filter(function(e){return e.tagName.indexOf('-')>0&&VID.test(e.getAttribute('url')||'');});\n"
            + "  }\n"
            + "  if(custom.length){\n"
            + "    strategy='element';\n"
            + "    custom.forEach(function(el){\n"
            + "      var m=VID.exec(el.getAttribute('url')||''); var id=m[1];\n"
            + "      if(seen[id]) return; seen[id]=1;\n"
            + "      var ts='attr';\n"
            + "      var title=attr(el,['title','video-title','data-title','aria-label']);\n"
            + "      if(!title){var h=deep(el,'h1,h2,h3,h4,h5,[class*=\"title\"],[slot*=\"title\"]')[0]; title=text(h); ts='inner';}\n"
            + "      if(!title){title=text(el); ts='text';}\n"
            + "      var ch=channelLink(el);\n"
            + "      var thumb=attr(el,['src','poster','thumbnail','image']);\n"
            + "      thumb=usable(thumb)?abs(thumb):imgUrl(el);\n"
            + "      var av=attr(el,['avatar']); av=usable(av)?abs(av):avatarOf(el);\n"
            + "      var hints=(el.getAttribute('hints')||'').toLowerCase();\n"
            + "      list.push({id:id,slug:m[2],title:title,ts:title?ts:'slug',thumb:thumb,channelId:ch?ch.id:'',channelName:attr(el,['name'])||(ch?ch.name:''),avatar:av,\n"
            + "        duration:durationOf(el,el.getAttribute('duration')),pub:attr(el,['time','datetime']),live:hints.indexOf('live')>=0,tag:el.tagName.toLowerCase(),\n"
            + "        html:list.length<3?(el.outerHTML||'').replace(/\\s+/g,' ').substring(0,700):'',\n"
            + "        shadow:list.length<3?(el.shadowRoot?(el.shadowRoot.innerHTML||'').replace(/\\s+/g,' ').substring(0,500):'no shadow root'):''});\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  // 2) Plain links: find each video's card as the biggest block holding only that video\n"
            + "  if(!list.length){\n"
            + "    strategy='links';\n"
            + "    var collect=function(strict){\n"
            + "      return [].filter.call(root.querySelectorAll('a[href]'),function(a){\n"
            + "        if(!VID.test(a.getAttribute('href')||'')) return false;\n"
            + "        return !(strict&&a.closest(SKIP));\n"
            + "      });\n"
            + "    };\n"
            + "    var anchors=collect(true); if(!anchors.length) anchors=collect(false);\n"
            + "    var items=[];\n"
            + "    anchors.forEach(function(a){\n"
            + "      var m=VID.exec(a.getAttribute('href')||''); var id=m[1];\n"
            + "      if(seen[id]) return; seen[id]=1;\n"
            + "      var card=a, el=a;\n"
            + "      while(el.parentElement&&el.parentElement!==root&&el.parentElement!==document.body&&el.parentElement!==document.documentElement){\n"
            + "        var p=el.parentElement, ids={}, n=0;\n"
            + "        [].forEach.call(p.querySelectorAll('a[href]'),function(x){var mm=VID.exec(x.getAttribute('href')||'');if(mm&&!ids[mm[1]]){ids[mm[1]]=1;n++;}});\n"
            + "        if(n>1||p.querySelector('h1,h2')) break; // a section heading means the block is bigger than one card\n"
            + "        card=p; el=p;\n"
            + "      }\n"
            + "      items.push({id:id,slug:m[2],card:card});\n"
            + "    });\n"
            + "    items.forEach(function(it,idx){\n"
            + "      var card=it.card, ts='';\n"
            + "      var title='';\n"
            + "      // the video's own links first: text of a link to this video, or a heading inside one\n"
            + "      var own=[].filter.call(card.querySelectorAll('a[href]'),function(x){var mm=VID.exec(x.getAttribute('href')||'');return mm&&mm[1]===it.id;});\n"
            + "      for(var i=0;i<own.length&&!title;i++){\n"
            + "        var hh=own[i].querySelector('h3,h4,h5,[class*=\"title\"]');\n"
            + "        title=text(hh)||(own[i].getAttribute('title')||'').trim()||text(own[i]);\n"
            + "        if(title) ts='link';\n"
            + "      }\n"
            + "      if(!title){title=text(card.querySelector('h3,h4,h5')); ts='heading';}\n"
            + "      if(!title){title=text(card.querySelector('[class*=\"title\"]:not([class*=\"channel\"])')); ts='class';}\n"
            + "      if(!title){var im=card.querySelector('img[alt]'); title=im?(im.getAttribute('alt')||'').trim():''; ts='alt';}\n"
            + "      var ch=channelLink(card);\n"
            + "      var tm=card.querySelector('time[datetime]');\n"
            + "      list.push({id:it.id,slug:it.slug,title:title,ts:title?ts:'slug',thumb:imgUrl(card),channelId:ch?ch.id:'',channelName:ch?(text(card.querySelector('[class*=\"channel__name\"],[class*=\"channel-name\"]'))||ch.name):'',\n"
            + "        avatar:avatarOf(card),duration:durationOf(card),pub:tm?tm.getAttribute('datetime'):'',live:false,tag:'',\n"
            + "        html:idx<3?(card.outerHTML||'').replace(/\\s+/g,' ').substring(0,700):'',shadow:''});\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  if(!list.length){ return w.__pa>=8?'[]':''; }\n"
            + "  try{window.scrollTo(0,document.body.scrollHeight);}catch(e){}\n"
            + "  // wait until the list stops growing (more cards load while scrolling)\n"
            + "  var same=(w.__pn===list.length); w.__ps=same?((w.__ps||0)+1):0; w.__pn=list.length;\n"
            + "  if((w.__ps>=3&&w.__pa>=5)||w.__pa>=12) return JSON.stringify({strategy:strategy,cards:list});\n"
            + "  return '';\n"
            + "})()\n";

    /** Runs inside the embed page: lists the media urls the page's player has loaded. Returns "" until there is one. */
    private static final String PLAYER_SCRIPT = "(function(){var o={src:'',urls:[],title:document.title||'',image:''};"
            + "var v=document.querySelector('video');"
            + "if(v){try{v.muted=true;v.play();}catch(e){}o.src=v.currentSrc||v.src||'';}"
            + "var b=document.querySelector('[class*=\"play-button\"],[class*=\"PlayButton\"],[class*=\"big-play\"],button[aria-label*=\"lay\"]');"
            + "if(b){try{b.click();}catch(e){}}"
            + "try{performance.getEntriesByType('resource').forEach(function(r){if(/\\.(mp4|m3u8|webm)(\\?|$)/i.test(r.name)){o.urls.push({u:r.name,s:(r.decodedBodySize||r.encodedBodySize||r.transferSize||0)});}});}catch(e){}"
            + "if(o.src&&o.src.indexOf('blob:')!==0&&!/\\.(mp4|m3u8|webm)(\\?|$)/i.test(o.src)){o.urls.push({u:o.src,s:0});}"
            + "var m=document.querySelector('meta[property=\"og:image\"]');if(m){o.image=m.content;}"
            + "var good=(o.src&&o.src.indexOf('blob:')!==0)||o.urls.length;"
            + "if(good&&v){try{v.pause();}catch(e){}}"
            + "return good?JSON.stringify(o):'';})()";
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

        for (RumbleParser.Entry entry : entries) { // remember channel pictures and card titles
            if (entry.channelId != null && entry.channelThumb != null) {
                CHANNEL_THUMBS.put(entry.channelId, entry.channelThumb);
            }

            if (entry.stem != null && entry.title != null && !"slug".equals(entry.titleSource)) {
                TITLES.put(entry.stem, entry.title);
            }
        }

        if (!entries.isEmpty()) {
            CACHE.put(key, new Object[]{System.currentTimeMillis(), entries});

            final List<RumbleParser.Entry> checked = entries;
            new Thread(() -> RumbleDebug.auditListing(path, checked)).start(); // quiet unless something looks wrong
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

    /**
     * Like {@link #listing} but through the hidden browser, so the signed-in session cookies are sent.
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
     * Metadata and stream urls of a video. {@code videoId} is the page name from the watch address ("v7cwvbs-some-title").<br/>
     * The player id is not in that name, so the watch page and oEmbed are asked for likely ids. Each answer is checked
     * against the title the card showed, so a wrong guess never plays a different video unnoticed.
     */
    public static RumbleParser.Stream stream(String videoId) throws IOException {
        List<String> ids = embedIdsOf(videoId);
        StringBuilder problems = new StringBuilder();
        RumbleParser.Stream firstFound = null;
        String mismatch = null;

        for (int i = 0; i < ids.size() && i < 3; i++) {
            RumbleParser.Stream stream = streamOf(ids.get(i), i == 0, problems);

            if (stream == null) {
                continue;
            }

            if (firstFound == null) {
                firstFound = stream;
            }

            if (titleAcceptable(videoId, stream)) {
                return stream;
            }

            mismatch = "card says \"" + shorten(expectedTitle(videoId)) + "\" but player id " + ids.get(i) + " is \"" + shorten(stream.title) + "\"";
            problems.append("id ").append(ids.get(i)).append(": other video; ");
        }

        if (firstFound != null) { // nothing matched: play the first, but say so in the details line
            firstFound.stability = "(unverified: " + mismatch + ")";
            return firstFound;
        }

        throw new IOException(problems.length() > 0 ? problems.toString() : "no player id found");
    }

    private static final java.util.Map<String, String> TITLES = new java.util.concurrent.ConcurrentHashMap<>();

    /** Title the card showed for this video (from a list), or from oEmbed. Null if unknown. */
    private static String expectedTitle(String videoId) {
        return TITLES.get(videoId);
    }

    private static boolean titleAcceptable(String videoId, RumbleParser.Stream stream) {
        if (stream.title == null) {
            return true; // nothing to compare
        }

        String card = TITLES.get(videoId);
        RumbleParser.Oembed oembed = OEMBEDS.get(videoId);

        boolean known = card != null || (oembed != null && oembed.title != null);
        return !known || (card != null && RumbleParser.titlesMatch(card, stream.title))
                || (oembed != null && oembed.title != null && RumbleParser.titlesMatch(oembed.title, stream.title));
    }

    private static String shorten(String text) {
        return text == null ? "?" : (text.length() > 40 ? text.substring(0, 40) + "..." : text);
    }

    /** Stream details for one player id: the embed API, and for the first id also the page's own player as a fallback. */
    private static RumbleParser.Stream streamOf(String embedId, boolean allowPagePlayer, StringBuilder problems) {
        for (String version : new String[]{"u4", "u3"}) {
            try {
                String json = RumbleHttp.get("/embedJS/" + version + "/?request=video&ver=2&v=" + encode(embedId), SITE + "/embed/" + embedId + "/", true);
                RumbleParser.Stream stream = RumbleParser.parseEmbed(json);

                if (stream != null && (stream.mp4Url != null || stream.hlsUrl != null || stream.webmUrl != null || !stream.candidates.isEmpty())) {
                    return stream;
                }

                problems.append(embedId).append(" ").append(version).append(": ").append(stream == null ? "not JSON (" + snippet(json) + ")" : "no stream in answer").append("; ");
            } catch (IOException e) {
                problems.append(embedId).append(" ").append(version).append(": ").append(e.getMessage()).append("; ");
            }
        }

        if (!allowPagePlayer) {
            return null;
        }

        // The API refused us: let the embed page's own player fetch the stream, and read it from there
        try {
            RumbleParser.Stream stream = RumbleParser.parseScrape(BrowserFetcher.scrape(SITE + "/embed/" + embedId + "/", PLAYER_SCRIPT, 25, true));

            if (stream != null) {
                return stream;
            }

            problems.append("player: no stream found; ");
        } catch (IOException e) {
            problems.append("player: ").append(e.getMessage()).append("; ");
        }

        return null;
    }

    private static final java.util.Map<String, String> EMBED_IDS = new java.util.concurrent.ConcurrentHashMap<>();

    private static final java.util.Map<String, List<String>> EMBED_ID_LISTS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Likely player ids of a video, best first: ids the watch page names for its own video, then oEmbed's answer, then
     * other embed links on the page.
     */
    static List<String> embedIdsOf(String videoId) throws IOException {
        List<String> cached = EMBED_ID_LISTS.get(videoId);

        if (cached != null) {
            return cached;
        }

        List<String> ids = new java.util.ArrayList<>();
        IOException pageError = null;
        List<String> pageIds = new java.util.ArrayList<>();

        try {
            pageIds = RumbleParser.findEmbedIds(RumbleHttp.get("/" + videoId + ".html", SITE + "/", false));
        } catch (IOException e) {
            pageError = e;
        }

        RumbleParser.Oembed oembed = oembedFor(videoId);
        int main = Math.min(pageIds.size(), 1); // the page's first named id comes before oEmbed's

        ids.addAll(pageIds.subList(0, main));

        if (oembed != null && oembed.embedId != null && !ids.contains(oembed.embedId)) {
            ids.add(oembed.embedId);
        }

        for (String id : pageIds) {
            if (!ids.contains(id)) {
                ids.add(id);
            }
        }

        if (ids.isEmpty()) {
            throw new IOException("player id not found (" + (pageError != null ? "page: " + pageError.getMessage() : "page and oEmbed gave nothing") + ")");
        }

        EMBED_ID_LISTS.put(videoId, ids);
        return ids;
    }

    private static final java.util.Map<String, RumbleParser.Oembed> OEMBEDS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * What Rumble says about this watch page: the real title, picture and player id. Null if the service can't be reached.
     */
    public static RumbleParser.Oembed oembedFor(String videoId) {
        RumbleParser.Oembed cached = OEMBEDS.get(videoId);

        if (cached != null) {
            return cached;
        }

        try {
            RumbleParser.Oembed oembed = RumbleParser.parseOembed(
                    RumbleHttp.get("/api/Media/oembed.json?url=" + encode(SITE + "/" + videoId + ".html"), SITE + "/", true));

            if (oembed != null) {
                OEMBEDS.put(videoId, oembed);
            }

            return oembed;
        } catch (IOException e) {
            return null;
        }
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
