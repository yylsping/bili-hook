package io.github.yylsping.bilihook;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

public class HfrProbeValuesTest {
    @Test public void signedQueryAndFragmentNeverAffectFingerprint() {
        assertEquals(HfrProbeValues.urlFingerprint("https://media.invalid/video.m4s"),
                HfrProbeValues.urlFingerprint("https://media.invalid/video.m4s?token=secret#fragment"));
        assertNotEquals(HfrProbeValues.urlFingerprint("https://media.invalid/a.m4s"),
                HfrProbeValues.urlFingerprint("https://media.invalid/b.m4s"));
        assertTrue(HfrProbeValues.urlFingerprint("secret").matches("[a-f0-9]{16}"));
    }
    @Test public void parsesRationalAndRejectsInvalidRates() {
        assertEquals(119.88012f, HfrProbeValues.parseFrameRate("120000/1001"), .001f);
        assertEquals(120f, HfrProbeValues.parseFrameRate("120fps"), 0f);
        for (String value : new String[]{"NaN", "Infinity", "0", "-120", "120/0", "1/2/3", "1fps20", ""})
            assertEquals(0f, HfrProbeValues.parseFrameRate(value), 0f);
    }
    @Test public void rejectsInjectedMetadataInsteadOfLoggingIt() {
        assertEquals("unknown", HfrProbeValues.safeFrameRate("120\nAuthorization: secret"));
        assertEquals("unknown", HfrProbeValues.safeFrameRate("https://secret.invalid/?token=secret"));
        assertEquals("120fps", HfrProbeValues.safeFrameRate("120fps"));
    }
    @Test public void nativeUrlIdentifiesOneOfTwoSameQualityRepresentations() {
        String avc = HfrProbeValues.urlFingerprint("https://media.invalid/80-avc.m4s");
        String hevc = HfrProbeValues.urlFingerprint("https://media.invalid/80-hevc.m4s");
        String opened = HfrProbeValues.urlFingerprint("https://media.invalid/80-hevc.m4s?signature=private");
        assertEquals(1, HfrProbeValues.uniqueUrlMatch(Arrays.asList(avc, hevc), opened));
    }
    @Test public void duplicateMissingOrUnknownUrlsRemainAmbiguous() {
        assertEquals(-1, HfrProbeValues.uniqueUrlMatch(Arrays.asList("abc", "abc"), "abc"));
        assertEquals(-1, HfrProbeValues.uniqueUrlMatch(Arrays.asList("abc", "def"), "other"));
        assertEquals(-1, HfrProbeValues.uniqueUrlMatch(Arrays.asList("none"), "none"));
        assertEquals(-1, HfrProbeValues.uniqueUrlMatch(Arrays.asList("unknown"), "unknown"));
    }
}
