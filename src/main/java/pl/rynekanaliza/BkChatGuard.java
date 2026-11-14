package pl.rynekanaliza;

import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;

/**
 * Obejście błędu BK-Rynku 3.0, który wysypywał grę (StackOverflowError w AutoMarketController.sendFeedback).
 * <p>
 * BK czyta czat przez zdarzenie Fabric GAME i miał pomijać własne komunikaty ("[BK Client] ..."), ale sprawdza tekst
 * z kodami kolorów ("§8[§2B§aK §fClient§8]"), więc ich nie rozpoznaje. Gdy serwer odpisze, że brakuje pieniędzy na zakup,
 * BK pisze "Brak środków na zakup: X", czyta to jako kolejny brak pieniędzy i pisze znowu - w kółko, aż skończy się stos.
 * <p>
 * Tu komunikaty BK ("[BK Client]" po zdjęciu kolorów) trafiają prosto do okna czatu, z pominięciem zdarzeń czatu,
 * więc BK ich nie czyta (tak, jak sam zamierzał) - pętla się nie zaczyna, a komunikat i tak jest widoczny.
 * Komunikaty serwera, graczy i tego moda przechodzą bez zmian.
 */
final class BkChatGuard {
    static final String BK_PREFIX = "[BK Client]";

    private BkChatGuard() {}

    /**
     * Zwroty komunikatu serwera do sprzedającego, że jego przedmiot się sprzedał (jak w BK: "Twój przedmiot ... został
     * kupiony", "Sprzedałeś ..."; porównywane bez polskich znaków). Czat graczy ("sprzedam miecz") się nie liczy.
     */
    static final String[] SALE_PHRASES = {"twoj przedmiot", "zostal kupiony", "sprzedales"};
    private static final java.util.ArrayDeque<Object[]> recentSales = new java.util.ArrayDeque<>();

    /** Zapamiętuje komunikat serwera o sprzedaży (ostatnie 10) - z nazwy w nim widać, co się sprzedało. */
    static void noteSale(String text, long now) {
        if (text == null) return;
        String low = strip(text).toLowerCase(java.util.Locale.ROOT);
        String plain = plain(low);
        boolean sale = false;
        for (String w : SALE_PHRASES) if (plain.contains(w)) { sale = true; break; }
        if (!sale) return;
        recentSales.addLast(new Object[]{now, low});
        while (recentSales.size() > 10) recentSales.removeFirst();
    }

    /** Komunikaty o sprzedaży z ostatnich maxAgeMs (małe litery, bez kolorów). */
    static java.util.List<String> recentSaleTexts(long now, long maxAgeMs) {
        return recentSaleTexts(now, maxAgeMs, Long.MIN_VALUE);
    }

    /** Jak wyżej, ale tylko nowsze niż after (komunikaty sprzedaży już rozliczonych się nie liczą). */
    static java.util.List<String> recentSaleTexts(long now, long maxAgeMs, long after) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (Object[] o : recentSales) {
            long t = (Long) o[0];
            if (t > after && Math.abs(now - t) <= maxAgeMs) out.add((String) o[1]);
        }
        return out;
    }

    /** Tekst bez polskich znaków (ą -> a, ł -> l, ...). */
    static String plain(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            int k = "ąćęłńóśźż".indexOf(c);
            b.append(k >= 0 ? "acelnoszz".charAt(k) : c);
        }
        return b.toString();
    }

    /** Zdarzenie ALLOW_GAME: false = komunikat BK pokazany od razu w oknie czatu, a dalsze zdarzenia czatu (BK) go nie widzą. */
    static boolean allow(Text message, boolean overlay) {
        try {
            if (overlay || message == null) return true;
            String s = message.getString();
            if (!isBkMessage(s)) {
                noteSale(s, System.currentTimeMillis());
                return true;
            }
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.inGameHud == null) return true;
            mc.inGameHud.getChatHud().addMessage(message);
            return false;
        } catch (Throwable t) {
            return true; // w razie kłopotu zwykła droga komunikatu
        }
    }

    /** Czy to komunikat BK-Rynku: po zdjęciu kodów kolorów zaczyna się od "[BK Client]". */
    static boolean isBkMessage(String s) {
        return s != null && !s.isEmpty() && strip(s).startsWith(BK_PREFIX);
    }

    /** Tekst bez kodów formatowania (§ i znak po nim). */
    static String strip(String s) {
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '§') {
                i++;
                continue;
            }
            b.append(c);
        }
        return b.toString().trim();
    }
}
