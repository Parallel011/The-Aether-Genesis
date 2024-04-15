package com.aetherteam.aether_genesis.loot.modifiers;

import com.aetherteam.aether.AetherTags;
import com.aetherteam.aether.item.EquipmentUtil;
import com.aetherteam.aether_genesis.item.GenesisItems;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.common.loot.LootModifier;

public class ChanceDoubleDropsModifier extends LootModifier {
    public static final Codec<ChanceDoubleDropsModifier> CODEC = RecordCodecBuilder.create((instance) -> LootModifier.codecStart(instance).apply(instance, ChanceDoubleDropsModifier::new));

    public ChanceDoubleDropsModifier(LootItemCondition[] conditions) {
        super(conditions);
    }

    @Override
    public ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> lootStacks, LootContext context) {
        Entity entity = context.getParamOrNull(LootContextParams.DIRECT_KILLER_ENTITY);
        Entity target = context.getParamOrNull(LootContextParams.THIS_ENTITY);
        ObjectArrayList<ItemStack> newStacks = new ObjectArrayList<>(lootStacks);
        if (entity instanceof LivingEntity livingEntity && target != null) {
            if (EquipmentUtil.hasCurio(livingEntity, GenesisItems.SKYROOT_RING.get()) && !target.getType().is(AetherTags.Entities.NO_SKYROOT_DOUBLE_DROPS)) {
                if (context.getRandom().nextInt(100) < 15) {
                    for (ItemStack stack : lootStacks) {
                        if (!stack.is(AetherTags.Items.NO_SKYROOT_DOUBLE_DROPS)) {
                            newStacks.add(stack);
                        }
                    }
                }
            }
        }
        return newStacks;
    }

    @Override
    public Codec<? extends IGlobalLootModifier> codec() {
        return ChanceDoubleDropsModifier.CODEC;
    }
}