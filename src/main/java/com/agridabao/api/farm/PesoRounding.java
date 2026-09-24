package com.agridabao.api.farm;

public final class PesoRounding {

    private PesoRounding() {
    }

    public static long toWholePesos(long centavos) {
        return centavos <= 0 ? 0 : (centavos + 50L) / 100L;
    }
}
