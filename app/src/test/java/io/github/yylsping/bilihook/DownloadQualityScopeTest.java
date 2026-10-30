package io.github.yylsping.bilihook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import org.junit.Test;
import java.util.concurrent.FutureTask;

public final class DownloadQualityScopeTest {
    @Test public void requiresLoggedInVideoAndKnownPremiumQuality() {
        for (int qn : new int[]{112, 116, 120, 125, 126}) {
            assertTrue(DownloadQualityScope.shouldGrant(true, qn, false));
            assertFalse(DownloadQualityScope.shouldGrant(false, qn, false));
            assertFalse(DownloadQualityScope.shouldGrant(true, qn, true));
        }
        for (int qn : new int[]{-1, 0, 16, 32, 64, 74, 80, 111, 113, 127, 999}) {
            assertFalse(DownloadQualityScope.shouldGrant(true, qn, false));
        }
    }

    @Test public void inactiveNestedSubmissionDoesNotInheritPremiumScope() {
        DownloadQualityScope scope = new DownloadQualityScope();
        assertFalse(scope.isActive());
        scope.enter(true, 120, false);
        assertTrue(scope.isActive());
        scope.enter(true, 120, true);
        assertFalse(scope.isActive());
        scope.exit();
        assertTrue(scope.isActive());
        scope.exit();
        assertFalse(scope.isActive());
        scope.exit();
        assertFalse(scope.isActive());
    }

    @Test public void cachedVideoAccessKeepsPgcAudioAndLoggedOutRestrictions() {
        assertTrue(DownloadQualityScope.canUseCachedVideo(true, true, 120, 0));
        assertFalse(DownloadQualityScope.canUseCachedVideo(true, false, 120, 0));
        assertFalse(DownloadQualityScope.canUseCachedVideo(false, true, 120, 0));
        assertFalse(DownloadQualityScope.canUseCachedVideo(true, true, 120, 1));
        assertFalse(DownloadQualityScope.canUseCachedVideo(true, true, 120, 2));
        assertFalse(DownloadQualityScope.canUseCachedVideo(true, true, 120, -1));
        assertFalse(DownloadQualityScope.canUseCachedVideo(true, true, 80, 0));
    }

    @Test public void scopeIsThreadConfined() throws Exception {
        DownloadQualityScope scope = new DownloadQualityScope();
        scope.enter(true, 120, false);
        FutureTask<Boolean> check = new FutureTask<>(() -> {
            boolean clean = !scope.isActive();
            scope.enter(true, 116, false);
            boolean active = scope.isActive();
            scope.exit();
            return clean && active && !scope.isActive();
        });
        try {
            new Thread(check).start();
            assertTrue(check.get());
            assertTrue(scope.isActive());
        } finally {
            scope.exit();
        }
        assertFalse(scope.isActive());
    }
}
