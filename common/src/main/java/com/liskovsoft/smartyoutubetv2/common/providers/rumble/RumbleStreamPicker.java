package com.liskovsoft.smartyoutubetv2.common.providers.rumble;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Chooses which address of a Rumble video is played, and whether it is a live stream.<br/>
 * Rules: preview files never reach this point (the parser drops them); a file that is far too small for the video's
 * length is a clip and is passed over; files of unknown size are compared by their real size; live streams use HLS.
 */
final class RumbleStreamPicker {
    /** Network questions, so the rules can be tested without a network. */
    interface Net {
        boolean resolves(String url);

        /** File size in bytes, or -1 when unknown. */
        long length(String url);

        /** Text of a small file (a playlist), or null. */
        String text(String url);
    }

    static final class Result {
        RumbleParser.Candidate chosen;
        boolean live;
        String note = "";
        final Set<String> unreachable = new LinkedHashSet<>();
    }

    /** About 120 kbit/s: a real video file is never lighter than this per second of its length. */
    private static final long MIN_BYTES_PER_SECOND = 15_000;
    private static final int MAX_PROBES = 5;

    private RumbleStreamPicker() {
    }

    static Result pick(RumbleParser.Stream stream, boolean cardSaysLive, Net net) {
        Result result = new Result();
        List<RumbleParser.Candidate> list = new ArrayList<>();

        for (RumbleParser.Candidate c : stream.candidates) {
            if (!RumbleParser.isPreviewUrl(c.url) && net.resolves(c.url)) {
                list.add(c);
            } else if (!RumbleParser.isPreviewUrl(c.url)) {
                result.unreachable.add(hostOf(c.url));
            }
        }

        if (list.isEmpty()) {
            return result;
        }

        boolean liveHint = stream.live || cardSaysLive;
        boolean hasHls = false;
        RumbleParser.Candidate topFile = null;

        for (RumbleParser.Candidate c : list) {
            hasHls |= c.kind.equals("HLS");

            if (topFile == null && !c.kind.equals("HLS")) {
                topFile = c;
            }
        }

        // A live stream is only playable as HLS; a file that is only 240p or smaller loses to adaptive HLS
        if (hasHls && (liveHint || (topFile != null && topFile.height > 0 && topFile.height < 360))) {
            moveHlsFirst(list);
        }

        int probes = 0;

        // Files whose picture size is unknown (read from the page's player): compare them by real size, biggest first
        if (!liveHint) {
            List<Integer> slots = new ArrayList<>();

            for (int i = 0; i < list.size(); i++) {
                RumbleParser.Candidate c = list.get(i);

                if (!c.kind.equals("HLS") && c.height == 0 && probes < MAX_PROBES) {
                    c.bytes = net.length(c.url);
                    probes++;
                    slots.add(i);
                }
            }

            if (slots.size() > 1) {
                List<RumbleParser.Candidate> block = new ArrayList<>();

                for (int i : slots) {
                    block.add(list.get(i));
                }

                Collections.sort(block, new Comparator<RumbleParser.Candidate>() {
                    @Override
                    public int compare(RumbleParser.Candidate a, RumbleParser.Candidate b) {
                        return Long.compare(b.bytes, a.bytes);
                    }
                });

                for (int k = 0; k < slots.size(); k++) {
                    list.set(slots.get(k), block.get(k));
                }
            }
        }

        // Too small for the video's length = a clip
        long minBytes = !liveHint && stream.durationSec >= 30 ? stream.durationSec * MIN_BYTES_PER_SECOND : 0;
        RumbleParser.Candidate biggestSmall = null;
        int smallCount = 0;

        for (RumbleParser.Candidate c : list) {
            if (c.kind.equals("HLS")) {
                result.chosen = c;
                break;
            }

            if (minBytes > 0 && c.bytes < 0 && probes < MAX_PROBES) {
                c.bytes = net.length(c.url);
                probes++;
            }

            if (minBytes > 0 && c.bytes > 0 && c.bytes < minBytes) {
                c.suspect = true;
                smallCount++;

                if (biggestSmall == null || c.bytes > biggestSmall.bytes) {
                    biggestSmall = c;
                }

                continue;
            }

            result.chosen = c;
            break;
        }

        if (result.chosen == null && biggestSmall != null) { // nothing full-size exists: play the largest, and say so
            result.chosen = biggestSmall;
            result.note = " (only small files found: may be a clip)";
        } else if (smallCount > 0) {
            result.note = " (skipped " + smallCount + " clip" + (smallCount > 1 ? "s" : "") + ")";
        }

        if (result.chosen == null) {
            result.chosen = list.get(0);
        }

        if (stream.skippedPreviews > 0) {
            result.note += " (left out " + stream.skippedPreviews + " preview file" + (stream.skippedPreviews > 1 ? "s" : "") + ")";
        }

        result.live = decideLive(stream, liveHint, result.chosen, net);
        return result;
    }

    private static void moveHlsFirst(List<RumbleParser.Candidate> list) {
        List<RumbleParser.Candidate> hls = new ArrayList<>();

        for (RumbleParser.Candidate c : list) {
            if (c.kind.equals("HLS")) {
                hls.add(c);
            }
        }

        list.removeAll(hls);
        list.addAll(0, hls);
    }

    /** Live when Rumble says so, or when the stream has no known length and its playlist never ends. */
    private static boolean decideLive(RumbleParser.Stream stream, boolean hinted, RumbleParser.Candidate chosen, Net net) {
        if (!chosen.kind.equals("HLS")) {
            return false;
        }

        if (!hinted && stream.durationSec > 0) {
            return false; // a normal video with a length: no need to look at the playlist
        }

        Boolean open = playlistIsOpen(chosen.url, net);

        if (open == null) {
            return hinted; // could not read it: trust Rumble's flag
        }

        return open;
    }

    /** True: playlist has no end marker (live). False: it ends (recording). Null: unreadable. */
    static Boolean playlistIsOpen(String url, Net net) {
        try {
            String text = net.text(url);

            if (text == null) {
                return null;
            }

            if (text.contains("#EXT-X-STREAM-INF")) { // master playlist: look at its first stream
                String next = null;
                boolean after = false;

                for (String line : text.split("\\r?\\n")) {
                    String t = line.trim();

                    if (t.startsWith("#EXT-X-STREAM-INF")) {
                        after = true;
                    } else if (after && !t.isEmpty() && !t.startsWith("#")) {
                        next = t;
                        break;
                    }
                }

                if (next == null) {
                    return null;
                }

                text = net.text(new URI(url).resolve(next).toString());

                if (text == null) {
                    return null;
                }
            }

            String lower = text.toLowerCase(Locale.US);

            if (lower.contains("#ext-x-endlist") || lower.contains("playlist-type:vod")) {
                return false;
            }

            return lower.contains("#extinf") ? Boolean.TRUE : null;
        } catch (Exception e) {
            return null;
        }
    }

    private static String hostOf(String url) {
        try {
            return new URI(url).getHost();
        } catch (Exception e) {
            return url;
        }
    }
}
