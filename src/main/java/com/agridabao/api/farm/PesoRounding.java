package com.agridabao.api.farm;

/**
 * Centavo totals to whole pesos.
 *
 * <p>Seed and produce values follow the Davao City government price list, which
 * has centavos in it (a coconut is P16.89), so marketplace base values are held
 * in centavos. Wallets stay in whole pesos, so a total is rounded once, to the
 * nearest peso, with halves rounding up.
 *
 * <p>The game client repeats this exact rule in {@code PesoPrice.TotalPesos}
 * when it shows a player the listing fee. If the two rounded differently the fee
 * shown would not be the fee charged, which is why this is whole-number
 * arithmetic rather than {@code Math.round}: C#'s default rounding sends halves
 * to the nearest even number, and matching that from here would be fragile.
 *
 * <p>Kept in a class of its own with no dependencies, so it can be compiled and
 * checked against the client's copy without starting the application.
 */
public final class PesoRounding {

    private PesoRounding() {
    }

    /** A centavo total as whole pesos, to the nearest peso, halves rounding up. */
    public static long toWholePesos(long centavos) {
        return centavos <= 0 ? 0 : (centavos + 50L) / 100L;
    }
}
