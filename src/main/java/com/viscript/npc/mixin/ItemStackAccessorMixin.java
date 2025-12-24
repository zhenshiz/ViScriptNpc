package com.viscript.npc.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.lowdragmc.lowdraglib2.configurator.accessors.ItemStackAccessor;
import com.lowdragmc.lowdraglib2.configurator.ui.Configurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ConfiguratorGroup;
import com.lowdragmc.lowdraglib2.configurator.ui.DataComponentConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.viscript.npc.gui.edit.components.SelectItemDialog;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.function.Consumer;
import java.util.function.Supplier;

@Mixin(value = ItemStackAccessor.class, remap = false)
public class ItemStackAccessorMixin {

    @WrapOperation(method = "create", at = @At(value = "INVOKE", target = "Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;addChild(Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;)Lcom/lowdragmc/lowdraglib2/gui/ui/UIElement;"))
    public UIElement create(UIElement instance, UIElement child, Operation<UIElement> original) {
        return instance;
    }

    @Inject(method = "create", at = @At(value = "RETURN"))
    public void addSlot(String name, Supplier<ItemStack> supplier, Consumer<ItemStack> consumer, boolean forceUpdate, Field field, Object owner, CallbackInfoReturnable<Configurator> cir, @Local(name = "group") ConfiguratorGroup group, @Local(name = "slot") ItemSlot slot, @Local(name = "updater") Consumer<ItemStack> updater, @Local(name = "componentsConfigurator") DataComponentConfigurator component) {
        slot.addEventListener(UIEvents.CLICK, event -> {
            var dialog = new SelectItemDialog(updater.andThen(s -> component.setPrototype(s.getComponents())));
            dialog.show(slot.getModularUI());
        });
        group.inlineContainer.addChild(slot);
    }
}
