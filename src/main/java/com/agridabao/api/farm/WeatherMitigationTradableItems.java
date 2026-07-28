package com.agridabao.api.farm;

/**
 * Additional marketplace/trade item rules for climate and weather mitigation.
 *
 * This class is deliberately package-private. EconomyJsonService calls it before
 * applying its original item whitelist and base-value rules.
 */
final class WeatherMitigationTradableItems {
    private WeatherMitigationTradableItems() {
    }

    static boolean isTradable(String itemType) {
        return baseValueOrNull(itemType) != null;
    }

    static Integer baseValueOrNull(String itemType) {
        if (itemType == null) {
            return null;
        }

        return switch (itemType) {
            case "MulchBag" -> 25;
            case "OrganicCompostBag" -> 45;
            case "SupportStakeKit" -> 60;
            case "TrellisKit" -> 75;
            case "RaisedBedKit" -> 140;
            case "IrrigationSystemKit" -> 450;
            case "WaterStorageTankKit" -> 600;
            case "ShadeNetKit" -> 260;
            case "WindbreakKit" -> 180;
            case "GreenhouseKit" -> 800;
            case "DrainageCanalKit" -> 320;
            default -> null;
        };
    }
}
