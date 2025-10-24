package com.viscript.npc.npc;

import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.render.CustomNpcRender;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

@EventBusSubscriber(modid = ViScriptNpc.MOD_ID)
public class NpcRegister {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES = DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, ViScriptNpc.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<CustomNpc>> CUSTOM_NPC;

    @SubscribeEvent
    public static void registerEntityRender(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(NpcRegister.CUSTOM_NPC.get(), (context) -> new CustomNpcRender<>(context, new PlayerModel<>(context.bakeLayer(ModelLayers.PLAYER), true)));
    }

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
    }
}
