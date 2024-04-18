package com.aetherteam.aether_genesis.network.packet;

import com.aetherteam.aether.Aether;
import com.aetherteam.aether_genesis.Genesis;
import com.aetherteam.aether_genesis.capability.GenesisDataAttachments;
import com.aetherteam.aether_genesis.capability.ZephyrColorAttachment;
import com.aetherteam.nitrogen.attachment.INBTSynchable;
import com.aetherteam.nitrogen.network.packet.SyncEntityPacket;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.attachment.AttachmentType;
import oshi.util.tuples.Quartet;

import java.util.function.Supplier;

/**
 * Sync packet for values in the {@link ZephyrColorAttachment} class.
 */
public class ZephyrColorSyncPacket extends SyncEntityPacket<ZephyrColorAttachment> {
    public static final ResourceLocation ID = new ResourceLocation(Genesis.MODID, "sync_zephyr_color_attachment");

    public ZephyrColorSyncPacket(Quartet<Integer, String, INBTSynchable.Type, Object> values) {
        super(values);
    }

    public ZephyrColorSyncPacket(int playerID, String key, INBTSynchable.Type type, Object value) {
        super(playerID, key, type, value);
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public static ZephyrColorSyncPacket decode(FriendlyByteBuf buf) {
        return new ZephyrColorSyncPacket(SyncEntityPacket.decodeEntityValues(buf));
    }

    @Override
    public Supplier<AttachmentType<ZephyrColorAttachment>> getAttachment() {
        return GenesisDataAttachments.ZEPHYR_COLOR;
    }
}