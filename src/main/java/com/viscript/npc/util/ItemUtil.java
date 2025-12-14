package com.viscript.npc.util;

import com.lowdragmc.lowdraglib2.Platform;
import com.mojang.serialization.Codec;
import com.mojang.serialization.Dynamic;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.loading.FMLEnvironment;

public class ItemUtil {
    public static final Codec<ItemStack> ITEM_STACK_CODEC = Codec.PASSTHROUGH.xmap(
            dynamic -> {
                CompoundTag tag = (CompoundTag) dynamic.getValue();
                return ItemStack.parseOptional(Platform.getFrozenRegistry(), tag);
            },
            itemStack -> {
                if (itemStack == null || itemStack.isEmpty()) {
                    return new Dynamic<>(NbtOps.INSTANCE, new CompoundTag());
                }
                return new Dynamic<>(NbtOps.INSTANCE, itemStack.saveOptional(FMLEnvironment.dist.isClient() ? Minecraft.getInstance().level.registryAccess() : Platform.getFrozenRegistry()));
            }
    );

    public static final StreamCodec<ByteBuf, ItemStack> ITEM_STACK_STREAM_CODEC = ByteBufCodecs.fromCodec(ITEM_STACK_CODEC);
}
