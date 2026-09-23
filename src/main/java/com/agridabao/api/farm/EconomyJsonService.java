package com.agridabao.api.farm;

import com.agridabao.api.error.BadRequestException;
import com.agridabao.api.error.ConflictException;
import com.agridabao.api.error.NotFoundException;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class EconomyJsonService {
    private static final Set<String> LIQUID_ITEMS = Set.of(
            "InsecticideLiter", "DisinfectantLiter", "NeemSoapLiter",
            "BtBioInsecticideLiter", "CopperFungicideLiter"
    );

    /**
     * Must match {@code SocialMarketplaceCatalog.TradableItems} in the game. The
     * five retired seed items (banana, coconut, mango, pineapple and strawberry
     * seed) stay tradable here only so older versions of the game keep working;
     * the current game turns any it receives into the material that replaced it.
     */
    private static final Set<String> TRADABLE_ITEMS = Set.of(
            "CoconutSeed", "CoconutSeednut", "Coconut",
            "BananaSeed", "BananaPlantlet", "BananaSucker", "Banana",
            "DurianSeed", "Durian", "PomeloSeed", "Pomelo",
            "CacaoSeed", "Cacao", "PineappleSeed", "PineappleSucker", "Pineapple",
            "MangosteenSeed", "Mangosteen",
            "MangoSeed", "MangoGraftedSeedling", "MangoLiso", "Mango",
            "CornSeed", "Corn", "EggplantSeed", "Eggplant",
            "SquashSeed", "SquashSeedling", "Squash",
            "StrawberrySeed", "StrawberryRunner", "Strawberry",
            "TomatoSeed", "Tomato", "AphidTrap", "InsecticideLiter",
            "DisinfectantLiter", "NeemSoapLiter", "BtBioInsecticideLiter",
            "CopperFungicideLiter", "PheromoneTrap", "FruitBag",
            "DrainageKit", "TermiteBaitStation"
    );

    private static final Map<String, Integer> BASE_VALUES = buildBaseValues();

    private final FarmSaveRepository farmRepository;
    private final ObjectMapper objectMapper;

    public EconomyJsonService(FarmSaveRepository farmRepository, ObjectMapper objectMapper) {
        this.farmRepository = farmRepository;
        this.objectMapper = objectMapper;
    }

    public FarmSave requireFarmForUpdate(UUID userId) {
        return farmRepository.findForUpdate(userId)
                .orElseThrow(() -> new NotFoundException(
                        "The player must create and save a farm before using social economy features."));
    }

    public FarmSave requireFarm(UUID userId) {
        return farmRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException(
                        "The player must create and save a farm before using social economy features."));
    }

    public ObjectNode editableSnapshot(FarmSave farm) {
        JsonNode source = farm.getSnapshot();
        if (source == null || !source.isObject()) {
            throw new ConflictException("The saved farm snapshot is missing or invalid.");
        }
        return (ObjectNode) source.deepCopy();
    }

    public void saveChangedSnapshot(FarmSave farm, ObjectNode snapshot) {
        farm.replace(
                farm.getRevision() + 1,
                farm.getSchemaVersion(),
                farm.getGeneratorVersion(),
                snapshot,
                Instant.now()
        );
        farmRepository.save(farm);
    }

    public int getMoney(JsonNode snapshot) {
        return inventory(snapshot).path("money").asInt(0);
    }

    public void setMoney(ObjectNode snapshot, int value) {
        inventory(snapshot).put("money", Math.max(0, value));
    }

    public int countItem(JsonNode snapshot, String itemType) {
        int total = 0;
        ArrayNode slots = slots(snapshot);
        for (JsonNode slot : slots) {
            if (itemType.equals(slot.path("itemType").asText()) && slot.path("amount").asInt() > 0) {
                total += slot.path("amount").asInt();
            }
        }
        return total;
    }

    public void removeItem(ObjectNode snapshot, String itemType, int quantity) {
        validateTradableItem(itemType);
        if (quantity <= 0) {
            throw new BadRequestException("Quantity must be greater than zero.");
        }
        if (countItem(snapshot, itemType) < quantity) {
            throw new ConflictException("The player no longer has enough " + itemType + ". Save the farm and try again.");
        }

        int remaining = quantity;
        ArrayNode slots = slots(snapshot);
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            JsonNode raw = slots.get(i);
            if (!raw.isObject() || !itemType.equals(raw.path("itemType").asText())) {
                continue;
            }
            ObjectNode slot = (ObjectNode) raw;
            int amount = slot.path("amount").asInt(0);
            int take = Math.min(amount, remaining);
            amount -= take;
            remaining -= take;
            if (amount <= 0) {
                clearSlot(slot);
            } else {
                slot.put("amount", amount);
            }
        }
    }

    public boolean canAddItem(JsonNode snapshot, String itemType, int quantity) {
        validateTradableItem(itemType);
        if (quantity <= 0) {
            return false;
        }
        int remaining = quantity;
        int maxStack = maxStack(itemType);
        ArrayNode slots = slots(snapshot);

        for (JsonNode slot : slots) {
            if (remaining <= 0) break;
            if (itemType.equals(slot.path("itemType").asText()) && slot.path("amount").asInt() > 0) {
                remaining -= Math.max(0, maxStack - slot.path("amount").asInt());
            }
        }
        for (JsonNode slot : slots) {
            if (remaining <= 0) break;
            if (isEmptySlot(slot)) {
                remaining -= maxStack;
            }
        }
        return remaining <= 0;
    }

    public void addItem(ObjectNode snapshot, String itemType, int quantity) {
        validateTradableItem(itemType);
        if (!canAddItem(snapshot, itemType, quantity)) {
            throw new ConflictException("There is not enough inventory space for " + quantity + " x " + itemType + ".");
        }

        int remaining = quantity;
        int maxStack = maxStack(itemType);
        ArrayNode slots = slots(snapshot);
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            JsonNode raw = slots.get(i);
            if (!raw.isObject() || !itemType.equals(raw.path("itemType").asText())) continue;
            ObjectNode slot = (ObjectNode) raw;
            int amount = slot.path("amount").asInt(0);
            if (amount <= 0 || amount >= maxStack) continue;
            int add = Math.min(maxStack - amount, remaining);
            slot.put("amount", amount + add);
            remaining -= add;
        }
        for (int i = 0; i < slots.size() && remaining > 0; i++) {
            JsonNode raw = slots.get(i);
            if (!raw.isObject() || !isEmptySlot(raw)) continue;
            ObjectNode slot = (ObjectNode) raw;
            int add = Math.min(maxStack, remaining);
            slot.put("itemType", itemType);
            slot.put("amount", add);
            slot.put("liquidMl", LIQUID_ITEMS.contains(itemType) ? 5000 : 0);
            slot.put("sprayerLiquid", "None");
            remaining -= add;
        }
    }

    public Map<String, Integer> inventoryCounts(JsonNode snapshot) {
        Map<String, Integer> result = new LinkedHashMap<>();
        for (String item : TRADABLE_ITEMS) {
            int count = countItem(snapshot, item);
            if (count > 0) result.put(item, count);
        }
        return result;
    }

    public String district(JsonNode snapshot) {
        return snapshot.path("area").path("districtName").asText("");
    }

    /**
     * The district a player's saved farm is in, or an empty string when they have
     * no saved farm or it does not record one - which {@link DistrictSeedPools}
     * treats as its fallback pool, exactly as the game does.
     */
    public String districtOf(UUID userId) {
        return farmRepository.findById(userId)
                .map(FarmSave::getSnapshot)
                .map(this::district)
                .orElse("");
    }

    /** Whether a farm in this district may obtain the item. Only crop seeds are limited. */
    public boolean isItemAvailableInDistrict(String itemType, String districtName) {
        return DistrictSeedPools.isAvailableIn(itemType, districtName);
    }

    public boolean isTradable(String itemType) {
        return itemType != null && TRADABLE_ITEMS.contains(itemType);
    }

    public void validateTradableItem(String itemType) {

        if (WeatherMitigationTradableItems.isTradable(itemType)) {
            return;
        }

        if (!isTradable(itemType)) {
            throw new BadRequestException(
                    "This item cannot be traded or sold. Owned tools such as Machete, WateringCan, Shovel, and SprayerPump are excluded.");
        }
    }

    /**
     * An item's marketplace base value, per item, in centavos. Turn a total into
     * whole pesos with {@link PesoRounding#toWholePesos}.
     */
    public int baseValueCentavos(String itemType) {

        Integer weatherMitigationBaseValue =
        WeatherMitigationTradableItems.baseValueOrNull(itemType);
        if (weatherMitigationBaseValue != null) {
            // That table is still in whole pesos.
            return weatherMitigationBaseValue * 100;
        }

        validateTradableItem(itemType);
        return BASE_VALUES.getOrDefault(itemType, 100);
    }

    public Set<String> tradableItems() {
        return TRADABLE_ITEMS;
    }

    private ObjectNode inventory(JsonNode snapshot) {
        if (snapshot == null || !snapshot.isObject()) {
            throw new ConflictException("The farm snapshot is invalid.");
        }
        ObjectNode root = (ObjectNode) snapshot;
        JsonNode raw = root.get("inventory");
        if (raw == null || !raw.isObject()) {
            ObjectNode created = objectMapper.createObjectNode();
            created.put("selectedSlotIndex", 0);
            created.put("money", 0);
            created.set("slots", objectMapper.createArrayNode());
            root.set("inventory", created);
            raw = created;
        }
        return (ObjectNode) raw;
    }

    private ArrayNode slots(JsonNode snapshot) {
        ObjectNode inventory = inventory(snapshot);
        JsonNode raw = inventory.get("slots");
        if (raw == null || !raw.isArray()) {
            ArrayNode created = objectMapper.createArrayNode();
            for (int i = 0; i < 36; i++) created.add(emptySlot());
            inventory.set("slots", created);
            return created;
        }
        ArrayNode result = (ArrayNode) raw;
        while (result.size() < 36) result.add(emptySlot());
        return result;
    }

    private ObjectNode emptySlot() {
        ObjectNode slot = objectMapper.createObjectNode();
        clearSlot(slot);
        return slot;
    }

    private static boolean isEmptySlot(JsonNode slot) {
        return slot == null || !slot.isObject() ||
                "None".equals(slot.path("itemType").asText("None")) ||
                slot.path("amount").asInt(0) <= 0;
    }

    private static void clearSlot(ObjectNode slot) {
        slot.put("itemType", "None");
        slot.put("amount", 0);
        slot.put("liquidMl", 0);
        slot.put("sprayerLiquid", "None");
    }

    /** Must match {@code PlayerInventory.GetMaxStack} in the game. */
    private static int maxStack(String itemType) {
        if (LIQUID_ITEMS.contains(itemType)) {
            return 1;
        }
        // The game caps a stack of aphid traps at 99. A bigger stack written here
        // was loaded into one slot anyway, past the game's own limit.
        return "AphidTrap".equals(itemType) ? 99 : 999;
    }

    /**
     * Marketplace base values, per item, in centavos.
     *
     * <p>Seeds follow the Davao City government seed prices and produce follows
     * the shipping bin, both of which have centavos in them; the tools keep the
     * NPC shop prices. Must match {@code SocialMarketplaceCatalog.BaseValues} in
     * the game client, or the listing fee a player is shown is not the one charged.
     */
    private static Map<String, Integer> buildBaseValues() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("PineappleSeed", 1000);              // P10
        values.put("BananaSeed", 1500);                 // P15
        values.put("CacaoSeed", 2500);                  // P25
        values.put("CoconutSeed", 1500);                // P15
        values.put("PomeloSeed", 5000);                 // P50
        values.put("MangoSeed", 3000);                  // P30
        values.put("MangosteenSeed", 7500);             // P75
        values.put("DurianSeed", 6000);                 // P60
        values.put("CornSeed", 38889);                  // P388.89
        values.put("EggplantSeed", 820000);             // P8200
        values.put("SquashSeed", 300000);               // P3000
        values.put("StrawberrySeed", 300);              // P3
        values.put("TomatoSeed", 950000);               // P9500
        // Planting materials. The five that replaced the retired seeds above keep
        // that seed's price; the plantlet, the grafted seedling and the ready
        // squash seedling are priced above the material they skip ahead of.
        values.put("BananaPlantlet", 3000);             // P30
        values.put("BananaSucker", 1500);               // P15
        values.put("MangoGraftedSeedling", 8000);       // P80
        values.put("MangoLiso", 3000);                  // P30
        values.put("CoconutSeednut", 1500);             // P15
        values.put("PineappleSucker", 1000);            // P10
        values.put("StrawberryRunner", 300);            // P3
        values.put("SquashSeedling", 330000);           // P3300
        values.put("Coconut", 1689);                    // P16.89
        values.put("Banana", 5100);                     // P51
        values.put("Durian", 15000);                    // P150
        values.put("Pomelo", 20000);                    // P200
        values.put("Cacao", 27527);                     // P275.27
        values.put("Pineapple", 5000);                  // P50
        values.put("Mangosteen", 5000);                 // P50
        values.put("Mango", 11800);                     // P118
        values.put("Corn", 1500);                       // P15
        values.put("Eggplant", 6200);                   // P62
        values.put("Squash", 4200);                     // P42
        values.put("Strawberry", 35000);                // P350
        values.put("Tomato", 15600);                    // P156
        values.put("AphidTrap", 3500);                  // P35
        values.put("InsecticideLiter", 9000);           // P90
        values.put("DisinfectantLiter", 8000);          // P80
        values.put("NeemSoapLiter", 10000);             // P100
        values.put("BtBioInsecticideLiter", 13000);     // P130
        values.put("CopperFungicideLiter", 12000);      // P120
        values.put("PheromoneTrap", 9000);              // P90
        values.put("FruitBag", 2500);                   // P25
        values.put("DrainageKit", 15000);               // P150
        values.put("TermiteBaitStation", 11000);        // P110
        return Map.copyOf(values);
    }
}
