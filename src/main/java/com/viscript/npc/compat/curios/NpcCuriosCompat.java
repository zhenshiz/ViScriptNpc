package com.viscript.npc.compat.curios;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.viscript.npc.ViScriptNpc;
import com.viscript.npc.npc.CustomNpc;
import com.viscript.npc.npc.data.inventory.NpcCuriosIntegration;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.type.ISlotType;
import top.theillusivec4.curios.api.type.capability.ICuriosItemHandler;
import top.theillusivec4.curios.api.type.inventory.ICurioStacksHandler;
import top.theillusivec4.curios.api.type.inventory.IDynamicStackHandler;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class NpcCuriosCompat {
    private static final String GENERIC_SLOT = "curio";
    private static final ResourceLocation SIZE_MODIFIER_ID = ViScriptNpc.id("npc_curios_size");

    private NpcCuriosCompat() {
    }

    public static void applyCurios(CustomNpc npc) {
        if (npc.level().isClientSide()) return;
        CuriosApi.getCuriosInventory(npc).ifPresent(handler -> applyCurios(npc, handler));
    }

    private static void applyCurios(CustomNpc npc, ICuriosItemHandler handler) {
        NpcCuriosIntegration curiosIntegration = npc.getNpcAttachment(NpcCuriosIntegration.class);
        List<ItemStack> configuredCurios = curiosIntegration.getCurios().stream()
                .filter(stack -> !stack.isEmpty())
                .map(ItemStack::copy)
                .toList();
        ensureGenericSlotCapacity(handler, configuredCurios.size());
        clearEquippedCurios(handler);
        for (ItemStack stack : configuredCurios) {
            equipFirstValid(handler, stack);
        }
    }

    private static void ensureGenericSlotCapacity(ICuriosItemHandler handler, int configuredSize) {
        Optional<ICurioStacksHandler> genericHandler = handler.getStacksHandler(GENERIC_SLOT);
        if (genericHandler.isEmpty()) {
            return;
        }
        int baseSize = getGenericSlotBaseSize(handler, genericHandler.get());
        int extraSlots = Math.max(0, configuredSize - baseSize);
        handler.removeSlotModifier(GENERIC_SLOT, SIZE_MODIFIER_ID);
        if (extraSlots > 0) {
            handler.addTransientSlotModifier(GENERIC_SLOT, SIZE_MODIFIER_ID, extraSlots, AttributeModifier.Operation.ADD_VALUE);
            genericHandler.get().getStacks();
        }
    }

    private static int getGenericSlotBaseSize(ICuriosItemHandler handler, ICurioStacksHandler genericHandler) {
        ISlotType entitySlot = CuriosApi.getEntitySlots(handler.getWearer()).get(GENERIC_SLOT);
        if (entitySlot != null) {
            return entitySlot.getSize();
        }
        return CuriosApi.getSlot(GENERIC_SLOT, handler.getWearer().level())
                .map(ISlotType::getSize)
                .orElse(genericHandler.getSlots());
    }

    private static void clearEquippedCurios(ICuriosItemHandler handler) {
        for (Map.Entry<String, ICurioStacksHandler> entry : handler.getCurios().entrySet()) {
            IDynamicStackHandler stacks = entry.getValue().getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                stacks.setStackInSlot(index, ItemStack.EMPTY);
            }
        }
    }

    private static boolean equipFirstValid(ICuriosItemHandler handler, ItemStack stack) {
        for (String slotId : preferredSlotOrder(handler, stack)) {
            ICurioStacksHandler stacksHandler = handler.getCurios().get(slotId);
            if (stacksHandler == null) continue;
            IDynamicStackHandler stacks = stacksHandler.getStacks();
            for (int index = 0; index < stacks.getSlots(); index++) {
                if (!stacks.getStackInSlot(index).isEmpty()) continue;
                if (!stacks.isItemValid(index, stack)) continue;
                handler.setEquippedCurio(slotId, index, stack.copy());
                return true;
            }
        }
        return false;
    }

    private static List<String> preferredSlotOrder(ICuriosItemHandler handler, ItemStack stack) {
        Multimap<Integer, String> ordered = LinkedHashMultimap.create();
        Map<String, ISlotType> validSlots = CuriosApi.getItemStackSlots(stack, handler.getWearer());
        Map<String, ISlotType> entitySlots = CuriosApi.getEntitySlots(handler.getWearer());
        validSlots.values().stream()
                .filter(slotType -> !GENERIC_SLOT.equals(slotType.getIdentifier()))
                .filter(slotType -> entitySlots.containsKey(slotType.getIdentifier()))
                .sorted(Comparator.naturalOrder())
                .forEach(slotType -> ordered.put(slotType.getOrder(), slotType.getIdentifier()));
        if (handler.getCurios().containsKey(GENERIC_SLOT)) {
            ordered.put(Integer.MAX_VALUE, GENERIC_SLOT);
        }
        return ordered.values().stream().toList();
    }
}
