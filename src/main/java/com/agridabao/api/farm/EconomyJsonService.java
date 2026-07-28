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

    private static final Set<String> TRADABLE_ITEMS = Set.of(
            "CoconutSeed", "Coconut", "BananaSeed", "Banana",
            "DurianSeed", "Durian", "PomeloSeed", "Pomelo",
            "CacaoSeed", "Cacao", "PineappleSeed", "Pineapple",
            "MangosteenSeed", "Mangosteen", "MangoSeed", "Mango",
            "CornSeed", "Corn", "EggplantSeed", "Eggplant",
            "SquashSeed", "Squash", "StrawberrySeed", "Strawberry",
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

    public int baseValue(String itemType) {

        Integer weatherMitigationBaseValue =
        WeatherMitigationTradableItems.baseValueOrNull(itemType);
        if (weatherMitigationBaseValue != null) {
            return weatherMitigationBaseValue;
        }

        validateTradableItem(itemType);
        return BASE_VALUES.getOrDefault(itemType, 1);
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

    private static int maxStack(String itemType) {
        return LIQUID_ITEMS.contains(itemType) ? 1 : 999;
    }

    private static Map<String, Integer> buildBaseValues() {
        Map<String, Integer> values = new LinkedHashMap<>();
        values.put("PineappleSeed", 20); values.put("BananaSeed", 90);
        values.put("CacaoSeed", 60); values.put("CoconutSeed", 45);
        values.put("PomeloSeed", 80); values.put("MangoSeed", 220);
        values.put("MangosteenSeed", 180); values.put("DurianSeed", 130);
        values.put("CornSeed", 35); values.put("EggplantSeed", 45);
        values.put("SquashSeed", 50); values.put("StrawberrySeed", 70);
        values.put("TomatoSeed", 55);
        values.put("Coconut", 8); values.put("Banana", 6);
        values.put("Durian", 18); values.put("Pomelo", 10);
        values.put("Cacao", 7); values.put("Pineapple", 9);
        values.put("Mangosteen", 12); values.put("Mango", 11);
        values.put("Corn", 5); values.put("Eggplant", 7);
        values.put("Squash", 8); values.put("Strawberry", 12);
        values.put("Tomato", 6);
        values.put("AphidTrap", 35); values.put("InsecticideLiter", 90);
        values.put("DisinfectantLiter", 80); values.put("NeemSoapLiter", 100);
        values.put("BtBioInsecticideLiter", 130); values.put("CopperFungicideLiter", 120);
        values.put("PheromoneTrap", 90); values.put("FruitBag", 25);
        values.put("DrainageKit", 150); values.put("TermiteBaitStation", 110);
        return Map.copyOf(values);
    }
}
