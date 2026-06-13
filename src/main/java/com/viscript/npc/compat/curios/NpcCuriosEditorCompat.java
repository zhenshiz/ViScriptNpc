package com.viscript.npc.compat.curios;

import com.lowdragmc.lowdraglib2.configurator.accessors.ItemStackAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.RegistrySearchComponent;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class NpcCuriosEditorCompat {
    private NpcCuriosEditorCompat() {
    }

    public static Configurator createCurioStackConfigurator(Supplier<ItemStack> getter, Consumer<ItemStack> setter) {
        Configurator[] holder = new Configurator[1];
        Configurator configurator = new ItemStackAccessor().create("", getter, stack -> {
            if (isCurioStack(stack)) {
                setter.accept(stack);
            } else if (holder[0] != null) {
                resetDisplayedStack(holder[0], getter.get());
            }
        }, true, null, null);
        holder[0] = configurator;
        configurator.allChildrenStream()
                .filter(RegistrySearchComponent.Item.class::isInstance)
                .map(RegistrySearchComponent.Item.class::cast)
                .forEach(itemConfigurator -> itemConfigurator.setFilter(item ->
                        item == Items.AIR || isCurioStack(item.getDefaultInstance())));
        return configurator;
    }

    private static void resetDisplayedStack(Configurator configurator, ItemStack stack) {
        configurator.allChildrenStream()
                .filter(ItemSlot.class::isInstance)
                .map(ItemSlot.class::cast)
                .forEach(slot -> slot.setItem(stack));
    }

    private static boolean isCurioStack(ItemStack stack) {
        if (stack.isEmpty()
                || !CuriosApi.getItemStackSlots(stack, true).isEmpty()) return true;
        CuriosApi.isStackValid(new SlotContext("curio", null, 0, false, true), stack);
        return false;
    }
}
