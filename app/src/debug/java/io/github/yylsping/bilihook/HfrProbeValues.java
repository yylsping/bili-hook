package io.github.yylsping.bilihook;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.List;

/** Pure sanitization helpers: the probe must never emit a signed media URL. */
final class HfrProbeValues {
    private HfrProbeValues() {}

    static String safeFrameRate(String value) {
        if (value == null || value.length() > 48 || !value.matches("[0-9. /fFpPsS]*")) return "unknown";
        return value.trim().isEmpty() ? "unknown" : value.trim().replace(' ', '_');
    }

    static float parseFrameRate(String value) {
        if (value == null) return 0f;
        try {
            String clean = value.toLowerCase(Locale.ROOT).replace('_', ' ').trim();
            if (clean.endsWith("fps")) clean = clean.substring(0, clean.length() - 3).trim();
            String[] parts = clean.split("/", -1);
            if (parts.length > 2) return 0f;
            double numerator = Double.parseDouble(parts[0].trim());
            double denominator = parts.length == 2 ? Double.parseDouble(parts[1].trim()) : 1d;
            double fps = numerator / denominator;
            return numerator > 0 && denominator > 0 && Double.isFinite(fps)
                    && fps <= Float.MAX_VALUE ? (float) fps : 0f;
        } catch (RuntimeException ignored) { return 0f; }
    }

    static String urlFingerprint(String url) {
        if (url == null || url.isEmpty()) return "none";
        int end = url.length();
        int query = url.indexOf('?');
        int fragment = url.indexOf('#');
        if (query >= 0) end = Math.min(end, query);
        if (fragment >= 0) end = Math.min(end, fragment);
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(
                    url.substring(0, end).getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(16);
            for (int i = 0; i < 8; i++) out.append(String.format(Locale.ROOT, "%02x", hash[i] & 255));
            return out.toString();
        } catch (Exception ignored) { return "unknown"; }
    }

    /** Only a single concrete URL match can identify a representation. */
    static int uniqueUrlMatch(List<String> candidateFingerprints, String openedFingerprint) {
        if (candidateFingerprints == null || openedFingerprint == null
                || openedFingerprint.equals("none") || openedFingerprint.equals("unknown")) return -1;
        int match = -1;
        for (int index = 0; index < candidateFingerprints.size(); index++) {
            if (!openedFingerprint.equals(candidateFingerprints.get(index))) continue;
            if (match >= 0) return -1;
            match = index;
        }
        return match;
    }
}
