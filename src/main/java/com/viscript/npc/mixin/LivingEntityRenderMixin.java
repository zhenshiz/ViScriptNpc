package com.viscript.npc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.npc.data.basics.setting.INpcRenderer;
import com.viscript.npc.npc.data.dynamic.model.NpcDynamicModel;
import com.viscript.npc.npc.render.CustomNpcRender;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRenderMixin<T extends LivingEntity, M extends EntityModel<T>>
        extends EntityRenderer<T> implements INpcRenderer {
    @Unique private int viScriptNpc$skinColor = -1;
    @Unique private NpcDynamicModel viScriptNpc$dynamicModel = null;

    protected LivingEntityRenderMixin(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public int getSkinColor() {return viScriptNpc$skinColor;}

    @Override
    public void setSkinColor(int color) {viScriptNpc$skinColor = color;}

    @Override
    public NpcDynamicModel getDynamicModel() {return viScriptNpc$dynamicModel;}

    @Override
    public void setDynamicModel(NpcDynamicModel dynamicModel) {viScriptNpc$dynamicModel = dynamicModel;}

    @WrapOperation(method = "render(Lnet/minecraft/world/entity/LivingEntity;FFLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/model/EntityModel;renderToBuffer(Lcom/mojang/blaze3d/vertex/PoseStack;Lcom/mojang/blaze3d/vertex/VertexConsumer;III)V"))
    public void render(EntityModel<?> instance, PoseStack poseStack, VertexConsumer vertexConsumer, int packedLight, int packedOverlay, int color, Operation<Void> original, @Coerce T entity, @Local(name = "flag1") boolean flag1) {
        int colour = flag1 ? 654311423 : getSkinColor();
        if (instance instanceof HumanoidModel<?> model) {
            CustomNpcRender.updateNpcModelPart(getDynamicModel(), model, false);
            original.call(instance, poseStack, vertexConsumer, packedLight, packedOverlay, colour);
            CustomNpcRender.updateNpcModelPart(getDynamicModel(), model, true);
            return;
        }
        original.call(instance, poseStack, vertexConsumer, packedLight, packedOverlay, colour);
    }
}
