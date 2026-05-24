package com.viscript.npc.npc;

import com.lowdragmc.lowdraglib2.registry.AutoRegistry;
import com.lowdragmc.lowdraglib2.registry.annotation.LDLRegister;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.ViScriptNpcRegistries;
import com.viscript.npc.npc.data.INpcData;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

public class NpcAttachmentType {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, ViScriptNpc.MOD_ID);

    private static final Map<Class<? extends INpcData>, Supplier<AttachmentType<INpcData>>> NPC_DATA_ATTACHMENTS = new LinkedHashMap<>();
    private static final Map<Class<? extends INpcData>, String> NPC_DATA_ATTACHMENT_NAMES = new LinkedHashMap<>();

    static {
        for (AutoRegistry.Holder<LDLRegister, INpcData, Supplier<INpcData>> npcData : ViScriptNpcRegistries.NPC_DATA) {
            register(npcData.annotation().name(), npcData.clazz(), npcData.value());
        }
    }

    public static AttachmentType<INpcData> getAttachment(Class<? extends INpcData> clazz) {
        Supplier<AttachmentType<INpcData>> attachmentType = NPC_DATA_ATTACHMENTS.get(clazz);
        if (attachmentType == null) {
            throw new IllegalArgumentException("Unknown npc data type: " + clazz.getName());
        }
        return attachmentType.get();
    }

    public static Set<Class<? extends INpcData>> getAttachmentClasses() {
        return Collections.unmodifiableSet(NPC_DATA_ATTACHMENTS.keySet());
    }

    public static String getAttachmentName(Class<? extends INpcData> clazz) {
        String name = NPC_DATA_ATTACHMENT_NAMES.get(clazz);
        if (name == null) {
            throw new IllegalArgumentException("Unknown npc data type: " + clazz.getName());
        }
        return name;
    }

    private static void register(String name, Class<? extends INpcData> clazz, Supplier<INpcData> supplier) {
        DeferredHolder<AttachmentType<?>, AttachmentType<INpcData>> attachmentTypeDeferredHolder = ATTACHMENT_TYPES.register(
                name,
                () -> AttachmentType.builder(supplier)
                        .serialize(INpcData.CODEC)
                        .sync(INpcData.STREAM_CODEC)
                        .copyOnDeath()
                        .build()
        );
        NPC_DATA_ATTACHMENTS.put(clazz, attachmentTypeDeferredHolder);
        NPC_DATA_ATTACHMENT_NAMES.put(clazz, name);
    }
}
