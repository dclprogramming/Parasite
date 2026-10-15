package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import com.liskovsoft.smartyoutubetv2.common.providers.BrowserFetcher;
import com.liskovsoft.smartyoutubetv2.common.providers.model.ProviderMediaItem;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
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
            + "  function num(s){var m=/(\\d[\\d.,]*)\\s*([kKmMbB])?/.exec(s||'');if(!m) return 0;var n=parseFloat(m[1].replace(/,/g,''));if(isNaN(n)) return 0;var u=(m[2]||'').toLowerCase();return Math.round(n*(u==='k'?1e3:u==='m'?1e6:u==='b'?1e9:1));}\n"
            + "  function allText(root){var t='';(function walk(n){if(n.nodeType===3){t+=n.nodeValue+' ';return;}if(n.nodeType!==1&&n.nodeType!==11) return;if(n.shadowRoot) walk(n.shadowRoot);var k=n.childNodes;for(var i=0;i<k.length;i++) walk(k[i]);})(root);return t;}\n"
            + "  // the card element keeps its numbers as JS properties (this.views, total.views), not always as attributes or text\n"
            + "  function propViews(e){\n"
            + "    var c=[e.views,e.viewCount,e.view_count,e.total&&e.total.views,e.stats&&e.stats.views,e.data&&e.data.views,e.video&&e.video.views,e.item&&e.item.views,e.model&&e.model.views];\n"
            + "    for(var i=0;i<c.length;i++){var v=c[i];\n"
            + "      if(typeof v==='number'&&v>0) return Math.round(v);\n"
            + "      if(typeof v==='string'){var n=num(v);if(n) return n;}}\n"
            + "    return 0;\n"
            + "  }\n"
            + "  function viewsOf(el){\n"
            + "    var holders=[el].concat(deep(el,'*').filter(function(e){return e.tagName&&e.tagName.indexOf('-')>0;}));\n"
            + "    for(var h=0;h<holders.length;h++){var pv=propViews(holders[h]);if(pv) return pv;}\n"
            + "    var names=el.getAttributeNames?el.getAttributeNames():[];\n"
            + "    for(var i=0;i<names.length;i++){if(/view/i.test(names[i])){var n=num(el.getAttribute(names[i]));if(n) return n;}}\n"
            + "    var t=allText(el).replace(/\\s+/g,' ');\n"
            + "    var m=/(\\d[\\d.,]*\\s*[kKmMbB]?)\\s*(?:views?|watching)/i.exec(t);\n"
            + "    if(m) return num(m[1]);\n"
            + "    var ve=deep(el,'[class*=\"view\"],[slot*=\"view\"],[data-views]')[0];\n"
            + "    return ve?num(ve.getAttribute('data-views')||text(ve)):0;\n"
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
            + "        duration:durationOf(el,el.getAttribute('duration')),views:viewsOf(el),pub:attr(el,['time','datetime']),live:hints.indexOf('live')>=0,tag:el.tagName.toLowerCase(),\n"
            + "        html:list.length<3?(el.outerHTML||'').replace(/\\s+/g,' ').substring(0,700):'',\n"
            + "        shadow:list.length<3?(el.shadowRoot?(el.shadowRoot.innerHTML||'').replace(/\\s+/g,' ').substring(0,500):'no shadow root'):''});\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  // 2) Plain links: find each video's card as the biggest block holding only that video. Also when the page has its own\n"
            + "  // cards: category pages list the live streams as cards and the recorded videos as plain links\n"
            + "  {\n"
            + "    strategy=list.length?'mixed':'links';\n"
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
            + "        avatar:avatarOf(card),duration:durationOf(card),views:viewsOf(card),pub:tm?tm.getAttribute('datetime'):'',live:false,tag:'',\n"
            + "        html:idx<3?(card.outerHTML||'').replace(/\\s+/g,' ').substring(0,700):'',shadow:''});\n"
            + "    });\n"
            + "  }\n"
            + "\n"
            + "  if(!list.length){ return w.__pa>=8?'[]':''; }\n"
            + "  // scroll down a screen at a time (sections below only fill in when they come into view), until the bottom stays the bottom\n"
            + "  if(!w.__st){w.__st=setInterval(function(){\n"
            + "    try{var h=document.body.scrollHeight, y=window.pageYOffset||0, vh=window.innerHeight||0;\n"
            + "      if(y+vh>=h-4){w.__sd=(w.__sd||0)+1; if(w.__sd>12){clearInterval(w.__st);}}\n"
            + "      else{w.__sd=0; window.scrollBy(0,Math.max(300,vh*0.9));}}catch(e){}\n"
            + "  },200);}\n"
            + "  // wait until the list stops growing (more cards load while scrolling)\n"
            + "  var same=(w.__pn===list.length); w.__ps=same?((w.__ps||0)+1):0; w.__pn=list.length;\n"
            + "  if((w.__ps>=2&&w.__pa>=3&&(w.__sd||0)>=2)||w.__pa>=14) return JSON.stringify({strategy:strategy,cards:list});\n"
            + "  return '';\n"
            + "})()\n";

    /**
     * Runs inside the embed page: lists the media urls the page's player has loaded. Returns "" until the player has
     * loaded the video's own length and picture size (or 10 tries have passed), so a short preview that starts first is
     * not mistaken for the video.
     */
    private static final String PLAYER_SCRIPT = "(function(){var w=window;w.__pp=(w.__pp||0)+1;"
            + "var o={src:'',urls:[],title:document.title||'',image:'',dur:0,vh:0,vw:0};"
            + "var v=document.querySelector('video');"
            + "var inf=false;"
            + "if(v){try{v.muted=true;v.play();}catch(e){}o.src=v.currentSrc||v.src||'';"
            + "inf=(v.duration===Infinity);o.dur=isFinite(v.duration)?v.duration:0;o.vh=v.videoHeight||0;o.vw=v.videoWidth||0;}"
            + "var b=document.querySelector('[class*=\"play-button\"],[class*=\"PlayButton\"],[class*=\"big-play\"],button[aria-label*=\"lay\"]');"
            + "if(b){try{b.click();}catch(e){}}"
            + "try{performance.getEntriesByType('resource').forEach(function(r){if(/\\.(mp4|m3u8|webm)(\\?|$)/i.test(r.name)){o.urls.push({u:r.name,s:(r.decodedBodySize||r.encodedBodySize||r.transferSize||0)});}});}catch(e){}"
            + "if(o.src&&o.src.indexOf('blob:')!==0&&!/\\.(mp4|m3u8|webm)(\\?|$)/i.test(o.src)){o.urls.push({u:o.src,s:0});}"
            + "var m=document.querySelector('meta[property=\"og:image\"]');if(m){o.image=m.content;}"
            + "var found=(o.src&&o.src.indexOf('blob:')!==0)||o.urls.length;"
            + "var ready=!!(v&&o.vh>0&&(o.dur>0||inf));"
            + "var good=found&&(ready||w.__pp>=10);"
            + "if(good&&v){try{v.pause();}catch(e){}}"
            + "return good?JSON.stringify(o):'';})()";
    /**
     * Runs inside a page that lists channels (not videos): scrolls until the list stops growing, then returns the
     * page markup so the channel links can be read from it.
     */
    private static final String CHANNELS_SCRIPT = "(function(){var w=window;w.__cp=(w.__cp||0)+1;"
            + "if(/just a moment|attention required/i.test(document.title||'')) return '';"
            + "var n=document.querySelectorAll('a[href^=\\\"/c/\\\"],a[href^=\\\"/user/\\\"]').length;"
            + "try{window.scrollTo(0,document.body.scrollHeight);}catch(e){}"
            + "var same=(w.__cn===n);w.__cs=same?((w.__cs||0)+1):0;w.__cn=n;"
            + "if(n===0&&w.__cp>=4){return '<html></html>';}"
            + "if((n>0&&w.__cs>=3)||w.__cp>=14){return n>0?document.documentElement.outerHTML:'<html></html>';}"
            + "return '';})()";

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

        // Seen before (even in an earlier run of the app): show it at once, and renew it quietly if it is getting old
        Object[] stored = readStored(key);

        if (stored != null) {
            long savedAt = (Long) stored[0];
            List<RumbleParser.Entry> entries = (List<RumbleParser.Entry>) stored[1];
            CACHE.put(key, new Object[]{savedAt, entries});

            if (System.currentTimeMillis() - savedAt >= CACHE_MS) {
                refreshInBackground(path, page);
            }

            return entries;
        }

        return loadNow(path, page, false);
    }

    /**
     * Makes sure page 1 of the list is saved and recent, without disturbing screens (low priority).
     * @return false when it could not be loaded now
     */
    public static boolean preload(String path) {
        return preload(path, 1);
    }

    public static boolean preload(String path, int page) {
        String key = path + "#" + page;
        Object[] cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < CACHE_MS) {
            return true;
        }

        Object[] stored = readStored(key);

        if (stored != null && System.currentTimeMillis() - (Long) stored[0] < CACHE_MS) {
            CACHE.put(key, stored);
            return true;
        }

        try {
            loadNow(path, page, true);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static final java.util.Set<String> REFRESHING = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    private static void refreshInBackground(String path, int page) {
        String key = path + "#" + page;

        if (!REFRESHING.add(key)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                BrowserFetcher.waitUntilIdle(20_000); // screens being opened come first
                loadNow(path, page, true);
            } catch (IOException | RuntimeException e) {
                // the saved copy stays in use
            } finally {
                REFRESHING.remove(key);
            }
        }, "RumbleRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    private static List<RumbleParser.Entry> loadNow(String path, int page, boolean background) throws IOException {
        String key = path + "#" + page;
        List<RumbleParser.Entry> entries;

        try { // the page as a browser shows it: Rumble fills its lists with scripts
            entries = renderedListing(path, page, background);

            if (entries.isEmpty() && page == 1) {
                RumbleDebug.emptyRendered(path);
            }
        } catch (IOException renderError) { // no browser available: read the plain page instead
            if (background) {
                throw renderError; // preloading never competes with screens (the plain page may need the browser too)
            }

            entries = plainListing(path, page);
        }

        remember(entries); // channel pictures and card titles

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

    private static List<RumbleParser.Entry> renderedListing(String path, int page, boolean background) throws IOException {
        String rendered = BrowserFetcher.scrape(SITE + pagedPath(path, page), EXTRACT_SCRIPT, 22, false, background);
        List<RumbleParser.Entry> entries = RumbleParser.parseRendered(rendered);

        if (!entries.isEmpty()) {
            store(path + "#" + page, rendered);
        }

        return entries;
    }

    // Saved copies of rendered lists: survive restarts, so screens open at once

    private static final long STORED_MAX_MS = 12L * 60 * 60 * 1000;

    private static final long STORED_MAX_SIGNED_MS = 3L * 60 * 60 * 1000;

    private static java.io.File storeDir(String key) {
        android.content.Context context = com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData.getAppContext();
        return context != null ? new java.io.File(context.getCacheDir(), key.startsWith("signed:") ? "rumble-signed" : "rumble-lists5") : null;
    }

    private static java.io.File storeFile(String key) {
        java.io.File dir = storeDir(key);
        return dir != null ? new java.io.File(dir, Integer.toHexString(key.hashCode()) + "_" + key.length() + ".txt") : null;
    }

    private static void store(String key, String rendered) {
        try {
            java.io.File file = storeFile(key);

            if (file == null) {
                return;
            }

            file.getParentFile().mkdirs();
            java.io.File temp = new java.io.File(file.getPath() + ".tmp");

            try (java.io.Writer out = new java.io.OutputStreamWriter(new java.io.FileOutputStream(temp), "UTF-8")) {
                out.write(key);
                out.write("\n");
                out.write(String.valueOf(System.currentTimeMillis()));
                out.write("\n");
                out.write(rendered);
            }

            if (!temp.renameTo(file)) {
                file.delete();
                temp.renameTo(file);
            }

            java.io.File[] files = file.getParentFile().listFiles();

            if (files != null && files.length > 150) { // keep the folder small: drop the oldest
                java.util.Arrays.sort(files, (a, b) -> Long.compare(a.lastModified(), b.lastModified()));

                for (int i = 0; i < files.length - 100; i++) {
                    files[i].delete();
                }
            }
        } catch (java.io.IOException | RuntimeException e) {
            // saving is only a speed-up
        }
    }

    /** @return {savedAtMs, entries} or null when nothing usable is saved */
    private static Object[] readStored(String key) {
        try {
            java.io.File file = storeFile(key);

            if (file == null || !file.isFile()) {
                return null;
            }

            String all;

            try (java.io.InputStream in = new java.io.FileInputStream(file)) {
                java.io.ByteArrayOutputStream bytes = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[16384];
                int n;

                while ((n = in.read(buffer)) > 0) {
                    bytes.write(buffer, 0, n);
                }

                all = bytes.toString("UTF-8");
            }

            int first = all.indexOf('\n');
            int second = first >= 0 ? all.indexOf('\n', first + 1) : -1;

            if (second < 0 || !key.equals(all.substring(0, first))) {
                return null;
            }

            long savedAt = Long.parseLong(all.substring(first + 1, second));

            if (System.currentTimeMillis() - savedAt > (key.startsWith("signed:") ? STORED_MAX_SIGNED_MS : STORED_MAX_MS)) {
                return null;
            }

            List<RumbleParser.Entry> entries = RumbleParser.parseRendered(all.substring(second + 1));

            if (entries.isEmpty()) {
                return null;
            }

            remember(entries);
            return new Object[]{savedAt, entries};
        } catch (java.io.IOException | RuntimeException e) {
            return null;
        }
    }

    /** Remember channel pictures and card titles seen in a list. */
    private static void remember(List<RumbleParser.Entry> entries) {
        for (RumbleParser.Entry entry : entries) {
            if (entry.channelId != null && entry.channelThumb != null) {
                CHANNEL_THUMBS.put(entry.channelId, entry.channelThumb);
            }

            if (entry.stem != null && entry.title != null && !"slug".equals(entry.titleSource)) {
                TITLES.put(entry.stem, entry.title);
            }
        }
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

    /** Search results for a word, most viewed first, within a time range ("this-week"). Rumble does the sorting. */
    public static String searchTopOfWeekPath(String text, String range) {
        return "/search/video?q=" + encode(text) + "&sort=views&date=" + range;
    }

    public static String channelPath(String channelId) {
        return "/" + channelId;
    }

    public static String searchVideosPath(String text) {
        return "/search/video?q=" + encode(text);
    }

    // The signed-in account's own lists (subscriptions): through the hidden browser, so the session cookies are sent.
    // Saved on the device like the public lists, and wiped by clearSignedStore() when the account changes.

    private static final long SIGNED_FRESH_MS = 5 * 60 * 1000;

    public static List<RumbleParser.Entry> listingSignedIn(String path, int page) throws IOException {
        String key = "signed:" + path + "#" + page;
        Object[] cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < SIGNED_FRESH_MS) {
            return (List<RumbleParser.Entry>) cached[1];
        }

        Object[] stored = readStored(key); // seen before, even in an earlier run: show at once, renew quietly

        if (stored != null) {
            CACHE.put(key, stored);

            if (System.currentTimeMillis() - (Long) stored[0] >= SIGNED_FRESH_MS) {
                refreshSignedInBackground(path, page);
            }

            return (List<RumbleParser.Entry>) stored[1];
        }

        return signedNow(path, page, false);
    }

    /** Fresh data for background jobs (never served from an old saved copy). Cancelled when a screen needs the browser. */
    public static List<RumbleParser.Entry> listingSignedInBackground(String path, int page) throws IOException {
        String key = "signed:" + path + "#" + page;
        Object[] cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < SIGNED_FRESH_MS) {
            return (List<RumbleParser.Entry>) cached[1];
        }

        return signedNow(path, page, true);
    }

    /** Keeps page 1 of an account list saved and recent, at low priority. @return false when it could not be loaded now */
    public static boolean preloadSigned(String path) {
        String key = "signed:" + path + "#1";
        Object[] cached = CACHE.get(key);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < SIGNED_FRESH_MS) {
            return true;
        }

        try {
            signedNow(path, 1, true);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }

    private static void refreshSignedInBackground(String path, int page) {
        String key = "signed:" + path + "#" + page;

        if (!REFRESHING.add(key)) {
            return;
        }

        Thread thread = new Thread(() -> {
            try {
                BrowserFetcher.waitUntilIdle(20_000);
                signedNow(path, page, true);
            } catch (IOException | RuntimeException e) {
                // the saved copy stays in use
            } finally {
                REFRESHING.remove(key);
            }
        }, "RumbleSignedRefresh");
        thread.setDaemon(true);
        thread.start();
    }

    private static List<RumbleParser.Entry> signedNow(String path, int page, boolean background) throws IOException {
        String key = "signed:" + path + "#" + page;
        List<RumbleParser.Entry> entries;

        try {
            String rendered = BrowserFetcher.scrape(SITE + pagedPath(path, page), EXTRACT_SCRIPT, 22, false, background);
            entries = RumbleParser.parseRendered(rendered);

            if (!entries.isEmpty()) {
                store(key, rendered);
            }
        } catch (IOException e) {
            if (background) {
                throw e;
            }

            entries = RumbleParser.parseListing(RumbleHttp.getWithSession(pagedPath(path, page)));
        }

        remember(entries);

        if (!entries.isEmpty()) {
            CACHE.put(key, new Object[]{System.currentTimeMillis(), entries});
        }

        return entries;
    }

    /** Forgets everything saved from the signed-in account (sign-out or a new sign-in). */
    public static void clearSignedStore() {
        for (String key : new java.util.ArrayList<>(CACHE.keySet())) {
            if (key.startsWith("signed:")) {
                CACHE.remove(key);
            }
        }

        java.io.File dir = storeDir("signed:");
        java.io.File[] files = dir != null ? dir.listFiles() : null;

        if (files != null) {
            for (java.io.File file : files) {
                file.delete();
            }
        }

        android.content.Context context = com.liskovsoft.smartyoutubetv2.common.prefs.ProviderData.getAppContext();

        if (context != null) {
            context.getSharedPreferences("rumble_sync", android.content.Context.MODE_PRIVATE).edit().clear().apply();
        }
    }

    /**
     * Channels the signed-in account follows, read from a page that lists them (as opposed to the video feed, which
     * only shows channels that posted lately). Empty when no such page could be read.
     */
    public static List<ProviderMediaItem> followedChannelsSignedIn(boolean background) throws IOException {
        java.util.Map<String, ProviderMediaItem> found = new java.util.LinkedHashMap<>();

        for (String path : new String[]{"/subscriptions/channels", "/account/subscriptions", "/account/following"}) {
            try {
                String html = BrowserFetcher.scrape(SITE + path, CHANNELS_SCRIPT, 25, false, background);

                for (ProviderMediaItem channel : RumbleParser.parseChannels(html)) {
                    if (channel.channelId != null && !found.containsKey(channel.channelId)) {
                        found.put(channel.channelId, channel);
                    }
                }
            } catch (IOException e) {
                if (background && BrowserFetcher.isSuperseded(e)) {
                    throw e; // a screen needed the browser: the caller waits and tries again
                }
                // try the next page
            }
        }

        return new java.util.ArrayList<>(found.values());
    }

    /**
     * Does the home page show the signed-in header? Uses the browser session.
     */
    public static boolean looksSignedIn() throws IOException {
        String html = RumbleHttp.getWithSession("/").toLowerCase(java.util.Locale.US);
        return html.contains("/logout") || html.contains("sign out") || html.contains("log out");
    }

    /**
     * Scrolls the page down in steps (so lazily loaded pictures get loaded), then returns the page markup.
     * Plain requests cannot be used for pictures: Rumble refuses them, and the page fills pictures in with scripts.
     */
    private static final String SEARCH_CHANNELS_SCRIPT = "(function(){var w=window;w.__sp=(w.__sp||0)+1;"
            + "if(/just a moment|attention required/i.test(document.title||'')) return '';"
            + "var h=document.body.scrollHeight,vh=window.innerHeight||600,y=(w.__sp-1)*vh*0.7;"
            + "try{window.scrollTo(0,y);}catch(e){}"
            + "var n=document.querySelectorAll('a[href^=\\\"/c/\\\"],a[href^=\\\"/user/\\\"]').length;"
            + "if(y>=h||w.__sp>=12){"
            + "var els=document.querySelectorAll('[class*=user-image--img]');"
            + "for(var k=0;k<els.length;k++){var bg=getComputedStyle(els[k]).backgroundImage;if(bg&&bg!=='none'){els[k].setAttribute('data-bg',bg);}}"
            + "return n>0?document.documentElement.outerHTML:'<html></html>';}"
            + "return '';})()";

    public static List<ProviderMediaItem> searchChannels(String text) throws IOException {
        String url = SITE + "/search/channel?q=" + encode(text);
        List<ProviderMediaItem> found;

        try {
            found = RumbleParser.parseChannels(BrowserFetcher.scrape(url, SEARCH_CHANNELS_SCRIPT, 25, false));
        } catch (IOException e) {
            found = java.util.Collections.emptyList();
        }

        if (found.isEmpty()) { // the browser got nothing: fall back to the plain page
            found = RumbleParser.parseChannels(RumbleHttp.get("/search/channel?q=" + encode(text), SITE + "/", false));
        }

        for (ProviderMediaItem channel : found) { // pictures seen before fill the gaps
            String known = CHANNEL_THUMBS.get(channel.channelId);

            if (channel.cardImageUrl == null && known != null) {
                channel.cardImageUrl = known;
                channel.backgroundImageUrl = known;
            } else if (channel.cardImageUrl != null) {
                CHANNEL_THUMBS.put(channel.channelId, channel.cardImageUrl);
            }
        }

        return found;
    }

    /**
     * Name and picture of a channel, read from its page.
     */
    public static ProviderMediaItem channelInfo(String channelId) throws IOException {
        return RumbleParser.parseChannelPage(channelId, RumbleHttp.getDirect(channelPath(channelId), SITE + "/", false));
    }

    /**
     * Metadata and stream urls of a video. {@code videoId} is the page name from the watch address ("v7cwvbs-some-title").<br/>
     * The player id is not in that name, so the watch page and oEmbed are asked for likely ids. Each answer is checked
     * against the title the card showed, so a wrong guess never plays a different video unnoticed.
     */
    public static RumbleParser.Stream stream(String videoId) throws IOException {
        Object[] cached = STREAMS.get(videoId);

        if (cached != null && System.currentTimeMillis() - (Long) cached[0] < STREAM_CACHE_MS) {
            return (RumbleParser.Stream) cached[1]; // playback info and details both ask: do the work once
        }

        RumbleParser.Stream stream = streamUncached(videoId);
        STREAMS.put(videoId, new Object[]{System.currentTimeMillis(), stream});
        return stream;
    }

    private static final long STREAM_CACHE_MS = 3 * 60 * 1000;
    private static final java.util.Map<String, Object[]> STREAMS = new java.util.concurrent.ConcurrentHashMap<>();

    private static RumbleParser.Stream streamUncached(String videoId) throws IOException {
        List<String> ids = embedIdsOf(videoId);
        RumbleParser.Oembed known = OEMBEDS.get(videoId);
        long expectedSec = known != null ? known.durationSec : 0;
        StringBuilder problems = new StringBuilder();
        RumbleParser.Stream firstFound = null;
        String mismatch = null;

        for (int i = 0; i < ids.size() && i < 3; i++) {
            RumbleParser.Stream stream = streamOf(ids.get(i), i == 0, problems, expectedSec);

            if (stream != null && stream.durationSec <= 0) {
                stream.durationSec = expectedSec; // lets the size check know how long the video is
            }

            if (stream == null) {
                continue;
            }

            if (firstFound == null) {
                firstFound = stream;
            }

            if (titleAcceptable(videoId, stream)) {
                return withPlayerHls(ids.get(i), videoId, stream, expectedSec);
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

    /** Same as the player script, but done as soon as the page's player has asked for a playlist. */
    private static final String PLAYER_HLS_SCRIPT = PLAYER_SCRIPT.replace("var good=found&&(ready||w.__pp>=10);",
            "var hasList=o.urls.some(function(x){return /\\.m3u8/i.test(x.u);});var good=found&&(ready||w.__pp>=10||hasList);");

    private static final java.util.Map<String, List<String>> PLAYER_HLS = new java.util.concurrent.ConcurrentHashMap<>();
    private static final java.util.Set<String> PLAYER_HLS_TRIED = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<String, Boolean>());

    /**
     * Some videos list only small files (often 180p-480p) and a single-size playlist in the embed data, while
     * Rumble's own player offers every size through an adaptive playlist it loads itself. When only small files are
     * known, let the embed page's player run once and add the playlists it loaded; the picker keeps the one that
     * lists the most sizes.
     */
    private static RumbleParser.Stream withPlayerHls(String embedId, String videoId, RumbleParser.Stream stream, long expectedSec) {
        if (stream.live) {
            return stream;
        }

        int tallestFile = 0;

        for (RumbleParser.Candidate c : stream.candidates) {
            tallestFile = Math.max(tallestFile, c.height);
        }

        if (tallestFile > 480) {
            return stream; // a big file is known: nothing to gain
        }

        List<String> found = PLAYER_HLS.get(videoId);

        if (found == null && PLAYER_HLS_TRIED.add(videoId)) {
            found = new java.util.ArrayList<>();

            try {
                RumbleParser.Stream seen = RumbleParser.parseScrape(BrowserFetcher.scrape(SITE + "/embed/" + embedId + "/", PLAYER_HLS_SCRIPT, 15, true), expectedSec);

                if (seen != null) {
                    for (RumbleParser.Candidate c : seen.candidates) {
                        if (c.kind.equals("HLS")) {
                            found.add(c.url);
                        }
                    }
                }
            } catch (IOException e) {
                // keep what the embed data gave
            }

            PLAYER_HLS.put(videoId, found);
        }

        if (found != null) {
            for (String url : found) {
                boolean known = false;

                for (RumbleParser.Candidate c : stream.candidates) {
                    known |= c.url.equals(url);
                }

                if (!known) {
                    stream.candidates.add(new RumbleParser.Candidate(url, "HLS", 0));
                }
            }
        }

        return stream;
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
    private static RumbleParser.Stream streamOf(String embedId, boolean allowPagePlayer, StringBuilder problems, long expectedSec) {
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
            RumbleParser.Stream stream = RumbleParser.parseScrape(BrowserFetcher.scrape(SITE + "/embed/" + embedId + "/", PLAYER_SCRIPT, 25, true), expectedSec);

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
