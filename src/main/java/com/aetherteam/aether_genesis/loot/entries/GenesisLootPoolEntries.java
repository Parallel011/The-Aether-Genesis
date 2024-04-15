package com.aetherteam.aether_genesis.loot.entries;

import com.aetherteam.aether_genesis.Genesis;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.storage.loot.entries.CompositeEntryBase;
import net.minecraft.world.level.storage.loot.entries.LootPoolEntryType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class GenesisLootPoolEntries {
    public static final DeferredRegister<LootPoolEntryType> LOOT_POOL_ENTRY_TYPES = DeferredRegister.create(Registries.LOOT_POOL_ENTRY_TYPE, Genesis.MODID);

    public static final DeferredHolder<LootPoolEntryType, LootPoolEntryType> RANDOM = LOOT_POOL_ENTRY_TYPES.register("random", () -> new LootPoolEntryType(CompositeEntryBase.createSerializer(RandomEntry::new)));
}
