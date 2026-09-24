package com.agridabao.api.farm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import static java.util.Map.entry;

public final class DistrictSeedPools {

    private DistrictSeedPools() {
    }

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

    private static final Set<String> FALLBACK = Set.of("Coconut", "Banana", "Cacao", "Corn");

    public static boolean isRestricted(String itemType) {
        return itemType != null && CROP_OF_MATERIAL.containsKey(itemType);
    }

    public static boolean isAvailableIn(String itemType, String districtName) {
        if (!isRestricted(itemType)) {
            return true;
        }
        return cropsFor(districtName).contains(CROP_OF_MATERIAL.get(itemType));
    }

    public static Set<String> cropsFor(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return FALLBACK;
        }
        Set<String> crops = CROPS.get(districtName.trim());
        return crops != null ? crops : FALLBACK;
    }

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
