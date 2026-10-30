package io.github.yylsping.bilihook;

import java.util.ArrayDeque;

/** A synchronous video-download gate only; never an account flag or a request-quality source. */
final class DownloadQualityScope {
    private final ThreadLocal<ArrayDeque<Boolean>> frames = new ThreadLocal<>();

    static boolean shouldGrant(boolean loggedIn, int quality, boolean audioOnly) {
        // Exact 7420400 UGC VIP qn defaults (lib.media.util.c.c()). Audio has separate rights.
        return loggedIn && !audioOnly && isSupportedVideoQuality(quality);
    }

    static boolean isSupportedVideoQuality(int quality) {
        return quality == 112 || quality == 116 || quality == 120
                || quality == 125 || quality == 126;
    }

    static boolean canUseCachedVideo(boolean loggedIn, boolean ugc, int quality, int audioQuality) {
        return loggedIn && ugc && audioQuality == 0 && isSupportedVideoQuality(quality);
    }

    boolean enter(boolean loggedIn, int quality, boolean audioOnly) {
        boolean grant = shouldGrant(loggedIn, quality, audioOnly);
        ArrayDeque<Boolean> stack = frames.get();
        if (stack == null) {
            stack = new ArrayDeque<>();
            frames.set(stack);
        }
        stack.push(grant);
        return grant;
    }

    void exit() {
        ArrayDeque<Boolean> stack = frames.get();
        if (stack == null || stack.isEmpty()) return;
        stack.pop();
        if (stack.isEmpty()) frames.remove();
    }

    boolean isActive() {
        ArrayDeque<Boolean> stack = frames.get();
        return stack != null && !stack.isEmpty() && stack.peek();
    }
}
