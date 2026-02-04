package com.viscript.npc.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.npc.data.basics.setting.INpcRenderer;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.render.CustomNpcRender;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import software.bernie.geckolib.animatable.GeoAnimatable;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.GeoRenderer;

@Mixin(GeoEntityRenderer.class)
public abstract class GeoRendererMixin<T extends Entity & GeoAnimatable>
        extends EntityRenderer<T> implements GeoRenderer<T>, INpcRenderer {
    @Unique private int viScriptNpc$skinColor = -1;
    @Unique private NpcDynamicModel viScriptNpc$dynamicModel = null;

    protected GeoRendererMixin(EntityRendererProvider.Context context) {super(context);}

    @Override
    public int getSkinColor() {return viScriptNpc$skinColor;}

    @Override
    public void setSkinColor(int color) {viScriptNpc$skinColor = color;}

    @Override
    public NpcDynamicModel getDynamicModel() {return viScriptNpc$dynamicModel;}

    @Override
    public void setDynamicModel(NpcDynamicModel dynamicModel) {viScriptNpc$dynamicModel = dynamicModel;}

    @Inject(method = "actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/world/entity/Entity;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V", at = @At(value = "INVOKE", target = "Lsoftware/bernie/geckolib/renderer/GeoRenderer;actuallyRender(Lcom/mojang/blaze3d/vertex/PoseStack;Lsoftware/bernie/geckolib/animatable/GeoAnimatable;Lsoftware/bernie/geckolib/cache/object/BakedGeoModel;Lnet/minecraft/client/renderer/RenderType;Lnet/minecraft/client/renderer/MultiBufferSource;Lcom/mojang/blaze3d/vertex/VertexConsumer;ZFIII)V"), cancellable = true)
    public void before(PoseStack poseStack, T animatable, BakedGeoModel model, @Nullable RenderType renderType, MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay, int colour, CallbackInfo ci) {
        CustomNpcRender.updateGeoPart(getDynamicModel(), model, false);
        GeoRenderer.super.actuallyRender(poseStack, animatable, model, renderType, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, getSkinColor());
        CustomNpcRender.updateGeoPart(getDynamicModel(), model, true);
        poseStack.popPose();
        ci.cancel();
    }
}
