package com.viscript.npc.mixin.aw;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.List;

@Mixin(LivingEntityRenderer.class)
public interface LivingEntityRendererAwMixin<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayerParent<T,M> {
    @Invoker
    float callGetBob(T livingBase, float partialTick);

    @Accessor("layers")
    List<RenderLayer<T, M>> getLayers();
}
