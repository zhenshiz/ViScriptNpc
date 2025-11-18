package com.viscript.npc.configurator.configurator;

import com.lowdragmc.lowdraglib2.configurator.annotation.ConfigNumber;
import com.lowdragmc.lowdraglib2.configurator.ui.NumberConfigurator;
import com.lowdragmc.lowdraglib2.configurator.ui.ValueConfigurator;
import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.elements.ItemSlot;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.viscript.npc.gui.edit.components.SelectItemDialog;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import org.appliedenergistics.yoga.YogaFlexDirection;
import org.appliedenergistics.yoga.YogaGutter;
import org.jetbrains.annotations.NotNull;

import java.util.function.Consumer;
import java.util.function.Supplier;

public class ItemStackConfigurator extends ValueConfigurator<ItemStack> {
    protected ItemSlot itemSlot;
    protected NumberConfigurator numberConfigurator;
    public final SelectItemDialog selectItemDialog;

    public ItemStackConfigurator(String name, Supplier<ItemStack> supplier, Consumer<ItemStack> onUpdate, @NotNull ItemStack defaultValue, boolean forceUpdate) {
        super(name, supplier, onUpdate, defaultValue, forceUpdate);
        setCopiable(value -> value);

        if (value == null) value = defaultValue;
        this.selectItemDialog = new SelectItemDialog(onUpdate);

        UIElement container = new UIElement().layout(layout -> {
            layout.setFlexDirection(YogaFlexDirection.ROW);
            layout.setGap(YogaGutter.ALL, 5);
        });
        this.itemSlot = (ItemSlot) new ItemSlot()
                .setItem(supplier.get())
                .layout(layout -> {
                    layout.setWidth(18);
                    layout.setHeight(18);
                }).addEventListener(UIEvents.CLICK, event -> {
                    var mui = this.getModularUI();
                    if (mui != null) {
                        var root = mui.ui.rootElement;
                        this.selectItemDialog.show(root);
                    }
                });
        this.numberConfigurator = (NumberConfigurator) new NumberConfigurator("viscript_npc.configurator.item.count",
                () -> value.getCount(),
                count -> value.setCount((Integer) count),
                1, true
        ).setRange(1, 99).setType(ConfigNumber.Type.INTEGER).layout(layout -> {
            layout.setWidth(50);
        });
        inlineContainer.addChild(
                container.addChildren(
                        this.itemSlot,
                        this.numberConfigurator
                )
        );
    }

    public void setTag(String[] tags) {
        for (String tag : tags) {
            if (tag == null || tag.isEmpty()) continue;
            selectItemDialog.tags.add(TagKey.create(Registries.ITEM, ResourceLocation.parse(tag)));
        }
    }

    @Override
    protected void onValueUpdatePassively(ItemStack newValue) {
        if (newValue == null) newValue = defaultValue;
        if (value != null && ItemStack.isSameItemSameComponents(value, newValue) && value.getCount() == newValue.getCount()) {
            return;
        }

        super.onValueUpdatePassively(newValue);
        if (itemSlot != null) itemSlot.setItem(newValue);

    }
}
