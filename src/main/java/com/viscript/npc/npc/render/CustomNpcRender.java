package com.viscript.npc.npc.render;

import com.google.common.collect.Lists;
import com.mojang.blaze3d.MethodsReturnNonnullByDefault;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.viscript.npc.mixin.aw.LivingEntityRendererAwMixin;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.basics.setting.NpcBasicsSetting;
import com.viscript.npc.npc.layer.CapeLayer;
import com.viscript.npc.npc.layer.INpcAppearancePart;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.Model;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityAttachment;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.ClientHooks;
import org.joml.Matrix4f;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
@MethodsReturnNonnullByDefault
public class CustomNpcRender<T extends CustomNpc, M extends HumanoidModel<T>> extends LivingEntityRenderer<T, M> {
    private LivingEntity livingEntity;
    private LivingEntityRenderer<T, M> livingEntityRenderer;
    private CustomNpc npc;
    // 默认的模型
    public M npcModel;
    // 外部替换的模型
    public Model otherModel;
    // npc默认的layers
    private final List<RenderLayer<T, M>> npcLayers = Lists.newArrayList();
    private final RenderLayer<T, M> renderLayer = new RenderLayer<T, M>((RenderLayerParent) null) {
        public void render(PoseStack mStack, MultiBufferSource typeBuffer, int lightMapUV, T entity, float limbSwing,
                           float limbSwingAmount, float partialTicks, float age, float netHeadYaw, float headPitch) {
            for (Object layer : ((LivingEntityRendererAwMixin) CustomNpcRender.this.livingEntityRenderer).getLayers()) {
                ((RenderLayer) layer).render(mStack, typeBuffer, lightMapUV, CustomNpcRender.this.livingEntity, limbSwing,
                        limbSwingAmount, partialTicks, age, netHeadYaw, headPitch);
            }
        }
    };
    private final HumanoidModel<T> renderModel;
    private float partialTicks;

    public CustomNpcRender(EntityRendererProvider.Context context, M model) {
        super(context, model, 0.5f);
        this.npcModel = model;
        this.npcLayers.addAll(this.layers);
        this.npcLayers.add(new CapeLayer<>(this));
        this.layers.clear();
        this.layers.addAll(this.npcLayers);
        this.renderModel = new HumanoidModel<>(context.bakeLayer(ModelLayers.PLAYER)) {
            // 修改模型的颜色渲染
            public void renderToBuffer(PoseStack mStack, VertexConsumer iVertex, int lightMapUV, int packedOverlayIn,
                                       int color) {
                CustomNpcRender.this.otherModel.renderToBuffer(mStack, iVertex, lightMapUV, packedOverlayIn,
                        color);
            }

            // 保证npc正常的动画
            public void setupAnim(T entityIn, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw,
                                  float headPitch) {
                if (CustomNpcRender.this.otherModel instanceof EntityModel entityModel) {
                    entityModel.setupAnim(CustomNpcRender.this.livingEntity, limbSwing, limbSwingAmount,
                            ((LivingEntityRendererAwMixin) CustomNpcRender.this.livingEntityRenderer).callGetBob(
                                    CustomNpcRender.this.livingEntity,
                                    Minecraft.getInstance().getTimer().getGameTimeDeltaPartialTick(true)),
                            netHeadYaw, headPitch);
                }

            }

            // 保证npc各种状态下(蹲、游泳、骑乘、攻击)与实体保持一致
            public void prepareMobModel(T npc, float animationPos, float animationSpeed, float partialTicks) {
                if (CustomNpcRender.this.otherModel instanceof HumanoidModel humanoidModel) {
                    humanoidModel.swimAmount = npc.getSwimAmount(partialTicks);
                    humanoidModel.crouching = CustomNpcRender.this.npcModel.crouching;
                }

                if (CustomNpcRender.this.otherModel instanceof EntityModel entityModel) {
                    entityModel.riding = CustomNpcRender.this.livingEntity.isPassenger()
                            && CustomNpcRender.this.livingEntity.getVehicle() != null;
                    entityModel.young = CustomNpcRender.this.livingEntity.isBaby();
                    entityModel.attackTime = CustomNpcRender.this.getAttackAnim((T) npc, partialTicks);
                    entityModel.prepareMobModel(CustomNpcRender.this.livingEntity, animationPos, animationSpeed, partialTicks);
                }
            }
        };
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
                    font.drawInBatch(customName, mainX, 0, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0,
                            packedLight);
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
                        font.drawInBatch(title, titleX, titleY, -1, false, matrix4f, bufferSource, Font.DisplayMode.NORMAL, 0,
                                packedLight);
                    }
                }

                poseStack.popPose();
            }
        }
    }

    @Override
    public void render(T npc, float entityYaw, float partialTicks, PoseStack poseStack, MultiBufferSource buffer,
                       int packedLight) {
        this.npc = npc;
        this.partialTicks = partialTicks;
        this.livingEntity = npc.getNpcDynamicModel().getEntity(this.npc);
        if (this.livingEntity == null) {
            // 恢复默认npc的模型
            this.model = this.npcModel;
            this.livingEntityRenderer = null;
            this.layers.clear();
            this.layers.addAll(this.npcLayers);
            npc.getNpcDynamicModel().updateNpcModelPart(this.model);
        } else {
            EntityRenderer<? super LivingEntity> render = this.entityRenderDispatcher.getRenderer(this.livingEntity);

            if (render instanceof LivingEntityRenderer) {
                this.livingEntityRenderer = (LivingEntityRenderer) render;
                this.otherModel = this.livingEntityRenderer.getModel();

                this.model = (M) this.renderModel;
                this.layers.clear();
                this.layers.add(this.renderLayer);
                if (render instanceof CustomNpcRender) {
                    for (Object layer : ((LivingEntityRendererAwMixin) this.livingEntityRenderer).getLayers()) {
                        if (layer instanceof INpcAppearancePart) {
                            ((INpcAppearancePart) layer).preRender((CustomNpc) this.livingEntity);
                        }
                    }
                }
            } else {
                this.livingEntityRenderer = null;
                this.livingEntity = null;
                this.model = this.npcModel;
                this.layers.clear();
                this.layers.addAll(this.npcLayers);
            }
        }

        this.npcModel.rightArmPose = this.getPose(npc, npc.getMainHandItem());
        this.npcModel.leftArmPose = this.getPose(npc, npc.getOffhandItem());
        super.render(npc, entityYaw, partialTicks, poseStack, buffer, packedLight);
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }

    @Override
    public ResourceLocation getTextureLocation(T t) {
        if (this.livingEntity != null) {
            // 使用对应实体的皮肤材质
            EntityRenderer<? super LivingEntity> render = this.entityRenderDispatcher.getRenderer(this.livingEntity);
            return render.getTextureLocation(this.livingEntity);
        } else {
            return NpcBasicsSetting.SkinType.getSkinType(t.getNpcBasicsSetting());
        }
    }

    public HumanoidModel.ArmPose getPose(T npc, ItemStack item) {
        if (item.isEmpty() || item == ItemStack.EMPTY) {
            return HumanoidModel.ArmPose.EMPTY;
        } else {
            if (npc.getUseItemRemainingTicks() > 0) {
                UseAnim enumAction = item.getUseAnimation();
                if (enumAction == UseAnim.BLOCK) {
                    return HumanoidModel.ArmPose.BLOCK;
                }

                if (enumAction == UseAnim.BOW) {
                    return HumanoidModel.ArmPose.BOW_AND_ARROW;
                }
            }

            return HumanoidModel.ArmPose.ITEM;
        }
    }
}
