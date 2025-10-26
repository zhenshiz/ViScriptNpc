package com.viscript.npc.npc.layer;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.inventory.NpcInventory;
import com.viscript.npc.util.common.BeanUtil;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.ModelManager;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.*;
import net.minecraft.world.item.armortrim.ArmorTrim;
import net.minecraft.world.item.armortrim.TrimPattern;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

@OnlyIn(Dist.CLIENT)
public class ArmorLayer<T extends CustomNpc, M extends HumanoidModel<T>, A extends HumanoidModel<T>> extends RenderLayer<T, M> {
    public final A innerModel;
    public final A outerModel;
    private final TextureAtlas armorTrimAtlas;

    public ArmorLayer(RenderLayerParent<T, M> renderer, A innerModel, A outerModel, ModelManager modelManager) {
        super(renderer);
        this.innerModel = innerModel;
        this.outerModel = outerModel;
        this.armorTrimAtlas = modelManager.getAtlas(Sheets.ARMOR_TRIMS_SHEET);
    }

    public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T livingEntity, float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        NpcInventory npcInventory = livingEntity.getNpcInventory();
        ItemStack head = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getHelmet())), Items.AIR).getDefaultInstance();
        ItemStack chestplate = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getChestplate())), Items.AIR).getDefaultInstance();
        ItemStack leggings = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getLeggings())), Items.AIR).getDefaultInstance();
        ItemStack boots = BeanUtil.getValueOrDefault(BuiltInRegistries.ITEM.get(ResourceLocation.parse(npcInventory.getBoots())), Items.AIR).getDefaultInstance();
        this.renderArmorPiece(poseStack, buffer, livingEntity, EquipmentSlot.HEAD, head, packedLight, this.getArmorModel(EquipmentSlot.HEAD), limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, livingEntity, EquipmentSlot.CHEST, chestplate, packedLight, this.getArmorModel(EquipmentSlot.CHEST), limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, livingEntity, EquipmentSlot.LEGS, leggings, packedLight, this.getArmorModel(EquipmentSlot.LEGS), limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        this.renderArmorPiece(poseStack, buffer, livingEntity, EquipmentSlot.FEET, boots, packedLight, this.getArmorModel(EquipmentSlot.FEET), limbSwing, limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
    }

    private void renderArmorPiece(PoseStack poseStack, MultiBufferSource bufferSource, T livingEntity, EquipmentSlot slot, ItemStack itemstack, int packedLight, A p_model, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Item var15 = itemstack.getItem();
        if (var15 instanceof ArmorItem armoritem) {
            if (armoritem.getEquipmentSlot() == slot) {
                this.getParentModel().copyPropertiesTo(p_model);
                this.setPartVisibility(p_model, slot);
                Model model = this.getArmorModelHook(livingEntity, itemstack, slot, p_model);
                boolean flag = this.usesInnerModel(slot);
                ArmorMaterial armormaterial = (ArmorMaterial) armoritem.getMaterial().value();
                IClientItemExtensions extensions = IClientItemExtensions.of(itemstack);
                extensions.setupModelAnimations(livingEntity, itemstack, slot, model, limbSwing, limbSwingAmount, partialTick, ageInTicks, netHeadYaw, headPitch);
                int fallbackColor = extensions.getDefaultDyeColor(itemstack);

                for (int layerIdx = 0; layerIdx < armormaterial.layers().size(); ++layerIdx) {
                    ArmorMaterial.Layer armormaterial$layer = (ArmorMaterial.Layer) armormaterial.layers().get(layerIdx);
                    int j = extensions.getArmorLayerTintColor(itemstack, livingEntity, armormaterial$layer, layerIdx, fallbackColor);
                    if (j != 0) {
                        ResourceLocation texture = ClientHooks.getArmorTexture(livingEntity, itemstack, armormaterial$layer, flag, slot);
                        this.renderModel(poseStack, bufferSource, packedLight, model, j, texture);
                    }
                }

                ArmorTrim armortrim = itemstack.get(DataComponents.TRIM);
                if (armortrim != null) {
                    this.renderTrim(armoritem.getMaterial(), poseStack, bufferSource, packedLight, armortrim, model, flag);
                }

                if (itemstack.hasFoil()) {
                    this.renderGlint(poseStack, bufferSource, packedLight, model);
                }
            }
        }

    }

    protected void setPartVisibility(A model, EquipmentSlot slot) {
        model.setAllVisible(false);
        switch (slot) {
            case HEAD:
                model.head.visible = true;
                model.hat.visible = true;
                break;
            case CHEST:
                model.body.visible = true;
                model.rightArm.visible = true;
                model.leftArm.visible = true;
                break;
            case LEGS:
                model.body.visible = true;
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
                break;
            case FEET:
                model.rightLeg.visible = true;
                model.leftLeg.visible = true;
        }

    }

    private void renderModel(PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, A model, int dyeColor, ResourceLocation textureLocation) {
        this.renderModel(poseStack, bufferSource, packedLight, model, dyeColor, textureLocation);
    }

    private void renderModel(PoseStack p_289664_, MultiBufferSource p_289689_, int p_289681_, Model p_289658_, int p_350798_, ResourceLocation p_324344_) {
        VertexConsumer vertexconsumer = p_289689_.getBuffer(RenderType.armorCutoutNoCull(p_324344_));
        p_289658_.renderToBuffer(p_289664_, vertexconsumer, p_289681_, OverlayTexture.NO_OVERLAY, p_350798_);
    }

    private void renderTrim(Holder<ArmorMaterial> armorMaterial, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, ArmorTrim trim, A model, boolean innerTexture) {
        this.renderTrim(armorMaterial, poseStack, bufferSource, packedLight, trim, model, innerTexture);
    }

    private void renderTrim(Holder<ArmorMaterial> p_323506_, PoseStack p_289687_, MultiBufferSource p_289643_, int p_289683_, ArmorTrim p_289692_, Model p_289663_, boolean p_289651_) {
        TextureAtlasSprite textureatlassprite = this.armorTrimAtlas.getSprite(p_289651_ ? p_289692_.innerTexture(p_323506_) : p_289692_.outerTexture(p_323506_));
        VertexConsumer vertexconsumer = textureatlassprite.wrap(p_289643_.getBuffer(Sheets.armorTrimsSheet(((TrimPattern) p_289692_.pattern().value()).decal())));
        p_289663_.renderToBuffer(p_289687_, vertexconsumer, p_289683_, OverlayTexture.NO_OVERLAY);
    }

    private void renderGlint(PoseStack p_289673_, MultiBufferSource p_289654_, int p_289649_, Model p_289659_) {
        p_289659_.renderToBuffer(p_289673_, p_289654_.getBuffer(RenderType.armorEntityGlint()), p_289649_, OverlayTexture.NO_OVERLAY);
    }

    private A getArmorModel(EquipmentSlot slot) {
        return this.usesInnerModel(slot) ? this.innerModel : this.outerModel;
    }

    private boolean usesInnerModel(EquipmentSlot slot) {
        return slot == EquipmentSlot.LEGS;
    }

    protected Model getArmorModelHook(T entity, ItemStack itemStack, EquipmentSlot slot, A model) {
        return ClientHooks.getArmorModel(entity, itemStack, slot, model);
    }
}
