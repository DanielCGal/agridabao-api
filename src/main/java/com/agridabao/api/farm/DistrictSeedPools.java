package com.agridabao.api.farm;

import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * Which crop seeds a farm in each Davao district is allowed to obtain.
 *
 * <p>A farm can only get the seeds its own district grows. The game enforces
 * that in the shop, but a marketplace purchase and a trade are carried out
 * here, so this is where they are refused - a modified or older client cannot
 * talk its way around it.
 *
 * <p>This must stay identical to {@code DistrictCropPools} in the game client,
 * fallback included. A farm with no district recorded, or one this table does not
 * know, uses the fallback pool on both sides.
 *
 * <p>Only crop seeds are restricted. Produce, tools and kits can be bought and
 * traded by anyone.
 */
public final class DistrictSeedPools {

    private DistrictSeedPools() {
    }

    private static final Map<String, Set<String>> POOLS = caseInsensitive(Map.of(
            "Calinan", Set.of("PineappleSeed", "PomeloSeed", "MangoSeed", "DurianSeed",
                    "BananaSeed", "CoconutSeed", "CacaoSeed", "MangosteenSeed"),
            "Toril", Set.of("CoconutSeed", "BananaSeed", "CacaoSeed", "MangoSeed",
                    "PomeloSeed", "DurianSeed", "PineappleSeed"),
            "Baguio", Set.of("CoconutSeed", "MangosteenSeed", "BananaSeed", "CacaoSeed",
                    "DurianSeed", "CornSeed"),
            "Paquibato", Set.of("CornSeed", "BananaSeed", "CoconutSeed", "CacaoSeed"),
            "Marilog", Set.of("TomatoSeed", "SquashSeed", "EggplantSeed", "StrawberrySeed",
                    "MangosteenSeed"),
            "Buhangin", Set.of("CoconutSeed", "CacaoSeed", "BananaSeed", "CornSeed")));

    /** For a farm whose district is missing or unrecognised - the same four as the game. */
    private static final Set<String> FALLBACK =
            Set.of("CoconutSeed", "BananaSeed", "CacaoSeed", "CornSeed");

    /** Every seed that belongs to some district, which is every crop seed. */
    private static final Set<String> RESTRICTED = allSeeds();

    /** Whether the item is a crop seed, and so subject to district pools at all. */
    public static boolean isRestricted(String itemType) {
        return itemType != null && RESTRICTED.contains(itemType);
    }

    /** Whether a farm in {@code districtName} may obtain the item. */
    public static boolean isAvailableIn(String itemType, String districtName) {
        if (!isRestricted(itemType)) {
            return true;
        }
        return poolFor(districtName).contains(itemType);
    }

    /** The seeds a farm in {@code districtName} may obtain, or the fallback pool. */
    public static Set<String> poolFor(String districtName) {
        if (districtName == null || districtName.isBlank()) {
            return FALLBACK;
        }
        Set<String> pool = POOLS.get(districtName.trim());
        return pool != null ? pool : FALLBACK;
    }

    private static Map<String, Set<String>> caseInsensitive(Map<String, Set<String>> source) {
        TreeMap<String, Set<String>> map = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        map.putAll(source);
        return Collections.unmodifiableMap(map);
    }

    private static Set<String> allSeeds() {
        Set<String> all = new HashSet<>();
        for (Set<String> pool : POOLS.values()) {
            all.addAll(pool);
        }
        all.addAll(FALLBACK);
        return Collections.unmodifiableSet(all);
    }
}
