package com.viscript.npc.npc;

import com.viscript.npc.ViScriptNpc;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public class NpcRegister {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ViScriptNpc.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CustomNpc>> CUSTOM_NPC;
    public static final DeferredHolder<EntityType<?>, EntityType<NpcProjectile>> CUSTOM_NPC_PROJECTILE;

    @SubscribeEvent
    public static void registerAttributes(EntityAttributeCreationEvent event) {
        event.put(CUSTOM_NPC.get(), Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 20)
                .build());
    }

    static {
        CUSTOM_NPC = ENTITY_TYPES.register(
                "custom_npc",
                () -> EntityType.Builder.of(CustomNpc::new, MobCategory.MISC)
                        .sized(0.6f, 2f)
                        .build(ViScriptNpc.id("custom_npc").toString())
        );
        CUSTOM_NPC_PROJECTILE = ENTITY_TYPES.register(
                "custom_npc_projectile",
                () -> EntityType.Builder.<NpcProjectile>of(NpcProjectile::new, MobCategory.MISC)
                        .sized(0.25f, 0.25f)
                        .clientTrackingRange(4)
                        .updateInterval(10)
                        .build(ViScriptNpc.id("custom_npc_projectile").toString())
        );
    }
}
