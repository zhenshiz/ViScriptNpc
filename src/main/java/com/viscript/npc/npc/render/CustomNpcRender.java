package com.viscript.npc.npc.render;

import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.mixin.GeoModelAccessor;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.basics.setting.ILiving;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.layer.CapeLayer;
import com.viscript.npc.npc.layer.INpcAppearancePart;
import com.viscript.npc.util.common.BeanUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.layers.CustomHeadLayer;
import net.minecraft.client.renderer.entity.layers.ElytraLayer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.ParametersAreNonnullByDefault;

@SuppressWarnings({"rawtypes"})
@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomNpcRender<T extends CustomNpc, M extends HumanoidModel<T>> extends LivingEntityRenderer<T, M> {

    public CustomNpcRender(EntityRendererProvider.Context context, M model) {
        super(context, model, 0.5f);
        addLayer(new CapeLayer<>(this));
        addLayer(new CustomHeadLayer<>(this, context.getModelSet(), context.getItemInHandRenderer()));
        addLayer(new ElytraLayer<>(this, context.getModelSet()));
        addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
        addLayer(new HumanoidArmorLayer<>(this,
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));
    }

    @Override
    protected void renderNameTag(T npc, Component displayName, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        if (this.shouldShowName(npc) && ClientHooks.isNameplateInRenderDistance(npc, this.entityRenderDispatcher.distanceToSqr(npc)) && npc.getNpcBasicsSetting().isNameVisible()) {
            Vec3 vec3 = npc.getAttachments().getNullable(EntityAttachment.NAME_TAG, 0, npc.getViewYRot(partialTick));
            if (vec3 != null) {
                boolean seeThrough = !npc.isDiscrete();
                poseStack.pushPose();

                // 获取实体缩放
                float scale = (float) npc.getAttributeValue(Attributes.SCALE);

                // 名字位置偏移
                float yOffset = npc.getBbHeight() * 0.2f;
                poseStack.translate(vec3.x, vec3.y + yOffset, vec3.z);

                // 始终面向摄像机
                poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

                // 缩放名字
                float nameScale = 0.02f * scale;
                poseStack.scale(nameScale, -nameScale, nameScale);

                Matrix4f matrix4f = poseStack.last().pose();
                Font font = this.getFont();

                // 背景透明度
                float opacity = Minecraft.getInstance().options.getBackgroundOpacity(0.25F);
                int bgColor = (int) (opacity * 255.0F) << 24;

                // 主名
                Component customName = Component.translatable(npc.getNpcBasicsSetting().getCustomName());
                float mainX = -font.width(customName) / 2f;
                font.drawInBatch(customName, mainX, 0, 553648127, false, matrix4f, bufferSource,
                        seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, bgColor, packedLight);
                if (seeThrough) {
                    font.drawInBatch(customName, mainX, 0, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
                }

                // 副名 / 称号
                Component titleStr = Component.translatable(npc.getNpcBasicsSetting().getCustomSubName());
                if (!titleStr.getString().isEmpty()) {
                    Component title = Component.literal("<").append(titleStr).append(">");
                    float titleX = -font.width(title) / 2f;
                    float titleY = 10f;
                    font.drawInBatch(title, titleX, titleY, 553648127, false, matrix4f, bufferSource,
                            seeThrough ? Font.DisplayMode.SEE_THROUGH : Font.DisplayMode.NORMAL, bgColor, packedLight);
                    if (seeThrough) {
                        font.drawInBatch(title, titleX, titleY, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0, packedLight);
                    }
                }

                poseStack.popPose();
            }
        }
    }

    @Override
    public void render(T npc, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        Entity entity = npc.getNpcDynamicModel().getEntity(npc);
        int color = npc.getNpcBasicsSetting().getSkinColor();
        ((ILiving) npc).setSkinColor(color);
        if (entity == null) {
            npc.getNpcDynamicModel().updateNpcModelPart(this.model);
        } else {
            // 复制所有需要的npc属性给用于渲染的实体
            BeanUtil.copyProperties(npc, entity);
            if (entity instanceof LivingEntity livingEntity) {
                ((ILiving) entity).setSkinColor(color);
                for (EquipmentSlot value : EquipmentSlot.values()) {
                    livingEntity.setItemSlot(value, npc.getItemBySlot(value));
                }
                var attribute = livingEntity.getAttribute(Attributes.SCALE);
                if (attribute != null) attribute.setBaseValue(npc.getNpcBasicsSetting().getModeSize());
            }

            EntityRenderer<? super Entity> render = this.entityRenderDispatcher.getRenderer(entity);

            if (render instanceof HumanoidMobRenderer humanoidMobRenderer) { // 修改人形模型参数
                HumanoidModel rendererModel = (HumanoidModel) humanoidMobRenderer.getModel();
                npc.getNpcDynamicModel().updateNpcModelPart(rendererModel);
            } else if (ViScriptNpc.isGeckoLibLoaded() && render instanceof GeoEntityRenderer geoEntityRenderer) {
                // 修改gecko模型参数
                BakedGeoModel currentModel = ((GeoModelAccessor) geoEntityRenderer.getGeoModel()).getCurrentModel();
                if (currentModel != null) npc.getNpcDynamicModel().updateGeoPart(currentModel);
            } // 直接调用对应实体的渲染方法
            render.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
            entityRender(npc, partialTicks, poseStack, buffer, packedLight);
            return;
        }

        for (Object layer : layers) {
            if (layer instanceof INpcAppearancePart part) {
                part.preRender(npc);
            }
        }
        super.render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    // 渲染拴绳和名称（java就不能直接调用父类的父类的方法吗？还要复制一下，好麻烦）
    private void entityRender(T npc, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        Entity entity = npc.getLeashHolder();
        if (entity != null) this.renderLeash(npc, partialTick, poseStack, bufferSource, entity);

        var event = new net.neoforged.neoforge.client.event.RenderNameTagEvent(npc, npc.getDisplayName(), this, poseStack, bufferSource, packedLight, partialTick);
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.post(event);
        if (event.canRender().isTrue() || event.canRender().isDefault() && this.shouldShowName(npc)) {
            this.renderNameTag(npc, event.getContent(), poseStack, bufferSource, packedLight, partialTick);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(T t) {
        return NpcBasicsSetting.SkinType.getSkinType(t.getNpcBasicsSetting());
    }
}
