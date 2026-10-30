package io.github.yylsping.bilihook;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.Test;

public final class Bili742AdHooksTest {
    public enum CardCase { AV, CM, GAME, CARDITEM_NOT_SET }
    public static final class Card {
        final CardCase kind;
        Card(CardCase kind) { this.kind = kind; }
        public CardCase getCardItemCase() { return kind; }
    }

    @Test public void searchKeepsOrganicCardsAndOrderWithoutMutatingResponse() throws Exception {
        Card first = new Card(CardCase.AV);
        Card second = new Card(CardCase.GAME);
        List<?> response = Collections.unmodifiableList(Arrays.asList(
                new Card(CardCase.CM), first, null, new Card(CardCase.CM), second));
        List<Object> filtered = Bili742AdHooks.filterSearchCards(response,
                Card.class.getMethod("getCardItemCase"));
        assertEquals(Arrays.asList(first, null, second), filtered);
        assertEquals(5, response.size());
    }

    @Test public void searchNoAdsOrUnknownItemsLeaveOriginalListInUse() throws Exception {
        List<?> response = Arrays.asList(new Card(CardCase.AV), new Object(), null,
                new Card(CardCase.CARDITEM_NOT_SET));
        assertNull(Bili742AdHooks.filterSearchCards(response,
                Card.class.getMethod("getCardItemCase")));
        assertEquals(Collections.emptyList(), Bili742AdHooks.filterSearchCards(
                Arrays.asList(new Card(CardCase.CM)), Card.class.getMethod("getCardItemCase")));
    }

    @Test
    public void recognizesOnlyExplicitPegasusAdCases() {
        assertTrue(Bili742AdHooks.isAdEnumName("SMALL_COVER_V5_AD"));
        assertTrue(Bili742AdHooks.isAdEnumName("AD_BANNER"));
        assertTrue(Bili742AdHooks.isAdEnumName("FOO_AD_BAR"));
        assertFalse(Bili742AdHooks.isAdEnumName("POPULAR_TOP_ENTRANCE"));
        assertFalse(Bili742AdHooks.isAdEnumName(null));
    }
}
