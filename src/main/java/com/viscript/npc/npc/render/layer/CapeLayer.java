package com.viscript.npc.npc.render.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.render.CustomNpcRender;
import com.viscript.npc.util.RenderUtil;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

import javax.annotation.ParametersAreNonnullByDefault;

@ParametersAreNonnullByDefault
@OnlyIn(Dist.CLIENT)
public class CapeLayer<T extends CustomNpc, M extends HumanoidModel<T>> extends RenderLayer<T, M> {
    private final CustomNpcRender<T, M> customNpcRender;

    public CapeLayer(CustomNpcRender<T, M> renderer) {
        super(renderer);
        this.customNpcRender = renderer;
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T npc, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!npc.isInvisible() && !npc.getItemBySlot(EquipmentSlot.CHEST).is(Items.ELYTRA)
                && npc.getNpcDynamicModel().getEntity() == null) { // 为null说明是玩家
            ResourceLocation capeTexture = npc.getNpcBasicsSetting().getCapeTexture();
            if (!ViScriptNpc.isPresentResource(capeTexture))
                capeTexture = RenderUtil.getSkin(npc.getNpcBasicsSetting().getTexturePlayerName()).capeTexture();
            if (capeTexture == null) return;
            poseStack.pushPose();
            poseStack.translate(0.0F, 0.0F, 0.125F);
            double d0 = Mth.lerp(partialTicks, npc.xCloakO, npc.xCloak) - Mth.lerp(partialTicks, npc.xo, npc.getX());
            double d1 = Mth.lerp(partialTicks, npc.yCloakO, npc.yCloak) - Mth.lerp(partialTicks, npc.yo, npc.getY());
            double d2 = Mth.lerp(partialTicks, npc.zCloakO, npc.zCloak) - Mth.lerp(partialTicks, npc.zo, npc.getZ());
            float f = Mth.rotLerp(partialTicks, npc.yBodyRotO, npc.yBodyRot);
            double d3 = Mth.sin(f * (float) (Math.PI / 180.0));
            double d4 = -Mth.cos(f * (float) (Math.PI / 180.0));
            float f1 = (float) d1 * 10.0F;
            f1 = Mth.clamp(f1, -6.0F, 32.0F);
            float f2 = (float) (d0 * d3 + d2 * d4) * 100.0F;
            f2 = Mth.clamp(f2, 0.0F, 150.0F);
            float f3 = (float) (d0 * d4 - d2 * d3) * 100.0F;
            f3 = Mth.clamp(f3, -20.0F, 20.0F);
            if (f2 < 0.0F) {
                f2 = 0.0F;
            }

            float f4 = Mth.lerp(partialTicks, npc.oBob, npc.bob);
            f1 += Mth.sin(Mth.lerp(partialTicks, npc.walkDistO, npc.walkDist) * 6.0F) * 32.0F * f4;

            poseStack.mulPose(Axis.XP.rotationDegrees(6.0F + f2 / 2.0F + f1));
            poseStack.mulPose(Axis.ZP.rotationDegrees(f3 / 2.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - f3 / 2.0F));
            VertexConsumer vertexconsumer = buffer.getBuffer(RenderType.entitySolid(capeTexture));
            ((PlayerModel<?>) customNpcRender.getModel()).renderCloak(poseStack, vertexconsumer, packedLight, OverlayTexture.NO_OVERLAY);
            poseStack.popPose();
        }
    }
}

