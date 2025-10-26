package com.viscript.npc.npc.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.util.common.BeanUtil;
import net.minecraft.client.model.ArmedModel;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;

@OnlyIn(Dist.CLIENT)
public class ItemInHandLayer<T extends CustomNpc, M extends EntityModel<T> & ArmedModel> extends RenderLayer<T, M> {
    private final ItemInHandRenderer itemInHandRenderer;

    public ItemInHandLayer(RenderLayerParent<T, M> renderer, ItemInHandRenderer itemInHandRenderer) {
        super(renderer);
        this.itemInHandRenderer = itemInHandRenderer;
    }

    public void render(@NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean flag = livingEntity.getMainArm() == HumanoidArm.RIGHT;
        NpcInventory npcInventory = livingEntity.getNpcInventory();
        ItemStack offHandItem = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getOffHand())), Items.AIR).getDefaultInstance();
        ItemStack mainHandItem = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getMainHand())), Items.AIR).getDefaultInstance();
        ItemStack itemstack = flag ? offHandItem : mainHandItem;
        ItemStack itemstack1 = flag ? mainHandItem : offHandItem;
        if (!itemstack.isEmpty() || !itemstack1.isEmpty()) {
            poseStack.pushPose();
            if (this.getParentModel().young) {
                float f = 0.5F;
                poseStack.translate(0.0F, 0.75F, 0.0F);
                poseStack.scale(0.5F, 0.5F, 0.5F);
            }

            this.renderArmWithItem(livingEntity, itemstack1, ItemDisplayContext.THIRD_PERSON_RIGHT_HAND, HumanoidArm.RIGHT, poseStack, buffer, packedLight);
            this.renderArmWithItem(livingEntity, itemstack, ItemDisplayContext.THIRD_PERSON_LEFT_HAND, HumanoidArm.LEFT, poseStack, buffer, packedLight);
            poseStack.popPose();
        }

    }

    protected void renderArmWithItem(LivingEntity livingEntity, ItemStack itemStack, ItemDisplayContext displayContext, HumanoidArm arm, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        if (!itemStack.isEmpty()) {
            poseStack.pushPose();
            ((ArmedModel) this.getParentModel()).translateToHand(arm, poseStack);
            poseStack.mulPose(Axis.XP.rotationDegrees(-90.0F));
            poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            boolean flag = arm == HumanoidArm.LEFT;
            poseStack.translate((float) (flag ? -1 : 1) / 16.0F, 0.125F, -0.625F);
            this.itemInHandRenderer.renderItem(livingEntity, itemStack, displayContext, flag, poseStack, buffer, packedLight);
            poseStack.popPose();
        }
    }
}