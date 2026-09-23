package com.agridabao.api.farm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.Map.entry;

/**
 * Which crops a farm in each Davao district grows, and so which planting
 * materials it is allowed to obtain.
 *
 * <p>A farm can only get the planting materials of crops its own district
 * grows - every material of such a crop, so a Calinan farm can have both the
 * banana plantlet and the banana sucker. The game enforces that in the shop,
 * but a marketplace purchase and a trade are carried out here, so this is where
 * they are refused - a modified or older client cannot talk its way around it.
 *
 * <p>This must stay identical to {@code DistrictCropPools} in the game client,
 * fallback included. A farm with no district recorded, or one this table does not
 * know, uses the fallback pool on both sides.
 *
 * <p>Only planting material is restricted. Produce, tools and kits can be bought
 * and traded by anyone. The five seed items the planting materials replaced
 * (banana, coconut, mango, pineapple and strawberry seed) are still limited by
 * their crop, because an older version of the game can still list and trade them.
 */
public final class DistrictSeedPools {

    private DistrictSeedPools() {
    }

    /** Each planting material, and the crop it grows into. */
    private static final Map<String, String> CROP_OF_MATERIAL = Map.ofEntries(
            entry("CacaoSeed", "Cacao"),
            entry("DurianSeed", "Durian"),
            entry("MangosteenSeed", "Mangosteen"),
            entry("PomeloSeed", "Pomelo"),
            entry("BananaPlantlet", "Banana"),
            entry("BananaSucker", "Banana"),
            entry("MangoGraftedSeedling", "Mango"),
            entry("MangoLiso", "Mango"),
            entry("CoconutSeednut", "Coconut"),
            entry("PineappleSucker", "Pineapple"),
            entry("StrawberryRunner", "Strawberry"),
            entry("TomatoSeed", "Tomato"),
            entry("EggplantSeed", "Eggplant"),
            entry("SquashSeed", "Squash"),
            entry("SquashSeedling", "Squash"),
            entry("CornSeed", "Corn"),
            // Retired seed items, still sent by older versions of the game.
            entry("BananaSeed", "Banana"),
            entry("CoconutSeed", "Coconut"),
            entry("MangoSeed", "Mango"),
            entry("PineappleSeed", "Pineapple"),
            entry("StrawberrySeed", "Strawberry"));

    private static final Map<String, Set<String>> CROPS = caseInsensitive(Map.of(
            "Calinan", Set.of("Pineapple", "Pomelo", "Mango", "Durian",
                    "Banana", "Coconut", "Cacao", "Mangosteen"),
            "Toril", Set.of("Coconut", "Banana", "Cacao", "Mango",
                    "Pomelo", "Durian", "Pineapple"),
            "Baguio", Set.of("Coconut", "Mangosteen", "Banana", "Cacao",
                    "Durian", "Corn"),
            "Paquibato", Set.of("Corn", "Banana", "Coconut", "Cacao"),
            "Marilog", Set.of("Tomato", "Squash", "Eggplant", "Strawberry",
                    "Mangosteen"),
            "Buhangin", Set.of("Coconut", "Cacao", "Banana", "Corn"),
            "Tugbok", Set.of("Banana", "Cacao", "Coconut", "Mangosteen",
                    "Corn", "Durian", "Mango")));

    /** For a farm whose district is missing or unrecognised - the same four as the game. */
    private static final Set<String> FALLBACK = Set.of("Coconut", "Banana", "Cacao", "Corn");

    /** Whether the item is planting material, and so subject to district pools at all. */
    public static boolean isRestricted(String itemType) {
        return itemType != null && CROP_OF_MATERIAL.containsKey(itemType);
    }

    /** Whether a farm in {@code districtName} may obtain the item. */
    public static boolean isAvailableIn(String itemType, String districtName) {
        if (!isRestricted(itemType)) {
            return true;
        }
        return cropsFor(districtName).contains(CROP_OF_MATERIAL.get(itemType));
    }

    /** The crops a farm in {@code districtName} grows, or the fallback pool. */
    public static Set<String> cropsFor(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return FALLBACK;
        }
        Set<String> crops = CROPS.get(districtName.trim());
        return crops != null ? crops : FALLBACK;
    }

    /** Every planting material a farm in {@code districtName} may obtain. */
    public static Set<String> poolFor(String districtName) {
        Set<String> crops = cropsFor(districtName);
        Set<String> materials = new HashSet<>();
        for (Map.Entry<String, String> material : CROP_OF_MATERIAL.entrySet()) {
            if (crops.contains(material.getValue())) {
                materials.add(material.getKey());
            }
        }
        return Collections.unmodifiableSet(materials);
    }

    private static Map<String, Set<String>> caseInsensitive(Map<String, Set<String>> source) {
        TreeMap<String, Set<String>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.putAll(source);
        return Collections.unmodifiableMap(map);
    }
}
