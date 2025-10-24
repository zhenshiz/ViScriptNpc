package com.viscript.npc.npc;

import com.mojang.serialization.Codec;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.data.attributes.NpcAttributes;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.npc.data.mod.integrations.NpcModIntegrations;
import com.viscript.npc.util.common.StrUtil;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public class NpcAttachmentType {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ViScriptNpc.MOD_ID);

    public static final Supplier<AttachmentType<NpcBasicsSetting>> NPC_BASICS_SETTING = register(
            NpcBasicsSetting.class,
            NpcBasicsSetting::new,
            NpcBasicsSetting.CODEC,
            NpcBasicsSetting.STREAM_CODEC
    );

    public static final Supplier<AttachmentType<NpcDynamicModel>> NPC_DYNAMIC_MODEL = register(
            NpcDynamicModel.class,
            NpcDynamicModel::new,
            NpcDynamicModel.CODEC,
            NpcDynamicModel.STREAM_CODEC
    );

    public static final Supplier<AttachmentType<NpcAttributes>> NPC_ATTRIBUTES = register(
            NpcAttributes.class,
            NpcAttributes::new,
            NpcAttributes.CODEC,
            NpcAttributes.STREAM_CODEC
    );

    public static final Supplier<AttachmentType<NpcInventory>> NPC_INVENTORY = register(
            NpcInventory.class,
            NpcInventory::new,
            NpcInventory.CODEC,
            NpcInventory.STREAM_CODEC
    );

    public static final Supplier<AttachmentType<NpcModIntegrations>> NPC_MOD_INTEGRATIONS = register(
            NpcModIntegrations.class,
            NpcModIntegrations::new,
            NpcModIntegrations.CODEC,
            NpcModIntegrations.STREAM_CODEC
    );


    public static <T> Supplier<AttachmentType<T>> register(Class<T> clazz, Supplier<T> supplier, Codec<T> codec, StreamCodec<ByteBuf, T> streamCodec) {
        return ATTACHMENT_TYPES.register(
                StrUtil.toSnakeCase(clazz.getSimpleName()),
                () -> AttachmentType.builder(supplier)
                        .serialize(codec)
                        .sync(streamCodec)
                        .copyOnDeath()
                        .build()
        );
    }
}
