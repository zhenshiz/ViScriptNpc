package com.viscript.npc.compat.curios;

import com.mojang.blaze3d.vertex.PoseStack;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.inventory.NpcCuriosIntegration;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.client.CuriosRendererRegistry;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Comparator;
import java.util.Map;

public final class NpcCuriosRenderCompat {
    private static final String GENERIC_SLOT = "curio";

    private NpcCuriosRenderCompat() {
    }

    public static <T extends CustomNpc, M extends EntityModel<T>> RenderLayer<T, M> createLayer(RenderLayerParent<T, M> parent) {
        return new NpcCuriosLayer<>(parent);
    }

    public static boolean renderDynamicModelWithCurios(CustomNpc npc, Entity entity, EntityRenderer<? super Entity> renderer,
                                                       float entityYaw, float partialTicks, PoseStack poseStack,
                                                       MultiBufferSource buffer, int packedLight) {
        if (entity instanceof LivingEntity living && renderer instanceof LivingEntityRenderer<?, ?> livingRenderer) {
            renderDynamicLivingModelWithCurios(npc, living, livingRenderer, entityYaw, partialTicks, poseStack, buffer, packedLight);
            return true;
        }
        return false;
    }

    @SuppressWarnings({"unchecked"})
    private static <T extends LivingEntity, M extends EntityModel<T>> void renderDynamicLivingModelWithCurios(
            CustomNpc npc, LivingEntity entity, LivingEntityRenderer<?, ?> renderer, float entityYaw, float partialTicks,
            PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        LivingEntityRenderer<T, M> typedRenderer = (LivingEntityRenderer<T, M>) renderer;
        T typedEntity = (T) entity;
        RenderLayer<T, M> layer = new DynamicCuriosLayer<>(typedRenderer, npc);
        var layers = typedRenderer.layers;
        layers.add(layer);
        try {
            typedRenderer.render(typedEntity, entityYaw, partialTicks, poseStack, buffer, packedLight);
        } finally {
            layers.remove(layer);
        }
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> void renderNpcCurios(
            CustomNpc npc, T entity, RenderLayerParent<T, M> parent, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        if (!renderEquippedCurios(npc, entity, parent, poseStack, buffer, packedLight, limbSwing,
                limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch)) {
            renderConfiguredCurios(npc, entity, parent, poseStack, buffer, packedLight, limbSwing,
                    limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> boolean renderEquippedCurios(
            CustomNpc npc, T entity, RenderLayerParent<T, M> parent, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        boolean[] hasEquippedCurio = {false};
        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> handler.getCurios().forEach((id, stacksHandler) -> {
            IDynamicStackHandler stacks = stacksHandler.getStacks();
            IDynamicStackHandler cosmeticStacks = stacksHandler.getCosmeticStacks();
            NonNullList<Boolean> renderStates = stacksHandler.getRenders();
            for (int index = 0; index < stacks.getSlots(); index++) {
                ItemStack stack = cosmeticStacks.getStackInSlot(index);
                boolean cosmetic = true;
                boolean renderable = renderStates.size() > index && renderStates.get(index);
                if (stack.isEmpty() && renderable) {
                    stack = stacks.getStackInSlot(index);
                    cosmetic = false;
                }
                if (stack.isEmpty()) continue;
                hasEquippedCurio[0] = true;
                SlotContext slotContext = new SlotContext(id, entity, index, cosmetic, renderable);
                renderCurio(stack, slotContext, poseStack, parent, buffer, packedLight, limbSwing,
                        limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
            }
        }));
        return hasEquippedCurio[0];
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> void renderConfiguredCurios(
            CustomNpc npc, T entity, RenderLayerParent<T, M> parent, PoseStack poseStack,
            MultiBufferSource buffer, int packedLight, float limbSwing, float limbSwingAmount,
            float partialTicks, float ageInTicks, float netHeadYaw, float headPitch) {
        int index = 0;
        for (ItemStack stack : npc.getNpcAttachment(NpcCuriosIntegration.class).getCurios()) {
            if (stack.isEmpty()) continue;
            SlotContext slotContext = new SlotContext(getPreferredSlot(npc, stack), entity, index++, false, true);
            renderCurio(stack, slotContext, poseStack, parent, buffer, packedLight, limbSwing,
                    limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static String getPreferredSlot(CustomNpc npc, ItemStack stack) {
        Map<String, ISlotType> slots = CuriosApi.getItemStackSlots(stack, npc);
        if (slots.isEmpty()) {
            slots = CuriosApi.getItemStackSlots(stack, true);
        }
        return slots.values().stream()
                .filter(slotType -> !GENERIC_SLOT.equals(slotType.getIdentifier()))
                .sorted(Comparator.naturalOrder())
                .map(ISlotType::getIdentifier)
                .findFirst()
                .orElse(GENERIC_SLOT);
    }

    private static <T extends LivingEntity, M extends EntityModel<T>> void renderCurio(ItemStack stack, SlotContext slotContext,
                                                                                     PoseStack poseStack, RenderLayerParent<T, M> parent,
                                                                                     MultiBufferSource buffer, int packedLight,
                                                                                     float limbSwing, float limbSwingAmount,
                                                                                     float partialTicks, float ageInTicks,
                                                                                     float netHeadYaw, float headPitch) {
        CuriosRendererRegistry.getRenderer(stack.getItem()).ifPresent(curioRenderer ->
                curioRenderer.render(stack, slotContext, poseStack, parent, buffer, packedLight, limbSwing,
                        limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch));
    }

    private static class NpcCuriosLayer<T extends CustomNpc, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private final RenderLayerParent<T, M> parent;

        private NpcCuriosLayer(RenderLayerParent<T, M> parent) {
            super(parent);
            this.parent = parent;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            renderNpcCurios(entity, entity, parent, poseStack, buffer, packedLight, limbSwing,
                    limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }

    private static class DynamicCuriosLayer<T extends LivingEntity, M extends EntityModel<T>> extends RenderLayer<T, M> {
        private final RenderLayerParent<T, M> parent;
        private final CustomNpc npc;

        private DynamicCuriosLayer(RenderLayerParent<T, M> parent, CustomNpc npc) {
            super(parent);
            this.parent = parent;
            this.npc = npc;
        }

        @Override
        public void render(PoseStack poseStack, MultiBufferSource buffer, int packedLight, T entity,
                           float limbSwing, float limbSwingAmount, float partialTicks, float ageInTicks,
                           float netHeadYaw, float headPitch) {
            renderNpcCurios(npc, entity, parent, poseStack, buffer, packedLight, limbSwing,
                    limbSwingAmount, partialTicks, ageInTicks, netHeadYaw, headPitch);
        }
    }
}
