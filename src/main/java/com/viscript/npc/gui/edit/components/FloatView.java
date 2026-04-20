package com.viscript.npc.gui.edit.components;

import com.lowdragmc.lowdraglib2.gui.ui.UIElement;
import com.lowdragmc.lowdraglib2.gui.ui.data.Horizontal;
import com.lowdragmc.lowdraglib2.gui.ui.data.Vertical;
import com.lowdragmc.lowdraglib2.gui.ui.elements.Label;
import com.lowdragmc.lowdraglib2.gui.ui.event.UIEvents;
import com.lowdragmc.lowdraglib2.gui.ui.styletemplate.Sprites;
import com.viscript.npc.gui.edit.view.NPCPreviewView;
import dev.vfyjxf.taffy.style.*;
import lombok.Getter;
import net.minecraft.network.chat.Component;
import org.joml.Vector2f;

import java.util.function.Supplier;

public class FloatView extends UIElement {
    public final NPCPreviewView sceneView;
    public final UIElement titleBar;
    public final UIElement contentContainer;

    //runtime
    @Getter
    private boolean isHidden;

    public FloatView(NPCPreviewView sceneView, Component title) {
        this.sceneView = sceneView;
        getLayout().positionType(TaffyPosition.ABSOLUTE);
        getLayout().width(150);
        getLayout().leftPercent(100);
        getLayout().topPercent(100);

        this.titleBar = new UIElement();
        this.contentContainer = new UIElement();

        this.titleBar.layout(layout -> {
            layout.widthPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.paddingAll(5);
        }).style(style -> style.backgroundTexture(Sprites.BORDER1_RT1));
        titleBar.addChild(new Label()
                .textStyle(style -> style
                        .textAlignVertical(Vertical.CENTER)
                        .textAlignHorizontal(Horizontal.CENTER)
                        .adaptiveWidth(true))
                .setText(title));

        titleBar.addEventListener(UIEvents.MOUSE_DOWN, event -> {
            titleBar.startDrag(new Vector2f(this.getLayoutX(), this.getLayoutY()), null);
        });
        titleBar.addEventListener(UIEvents.DRAG_SOURCE_UPDATE, event -> {
            if (event.currentElement == titleBar && event.dragHandler.draggingObject instanceof Vector2f initialPos) {
                var newPos = new Vector2f(initialPos).add(event.x - event.dragStartX, event.y - event.dragStartY);
                this.layout(layout -> {
                    layout.left(newPos.x);
                    layout.top(newPos.y);
                });
            }
        });

        titleBar.addEventListener(UIEvents.DOUBLE_CLICK, event -> {
            if (isHidden()) show();
            else hide();
        });

        this.contentContainer.layout(layout -> {
            layout.widthPercent(100);
            layout.alignItems(AlignItems.CENTER);
            layout.justifyContent(AlignContent.CENTER);
            layout.paddingAll(4);
            layout.gapAll(2);
        }).style(style -> style.backgroundTexture(Sprites.RECT_SOLID));

        addChildren(titleBar, contentContainer);
    }

    public void show() {
        if (isHidden) {
            isHidden = false;
            contentContainer.setDisplay(TaffyDisplay.FLEX);
        }
    }

    public void hide() {
        if (!isHidden) {
            isHidden = true;
            contentContainer.setDisplay(TaffyDisplay.NONE);
        }
    }

    public UIElement createInformation(Component title, Supplier<Component> info) {
        return new UIElement().layout(layout -> {
            layout.widthPercent(100);
            layout.flexDirection(FlexDirection.ROW);
            layout.height(9);
        }).addChildren(
                new Label().setText(title).textStyle(style -> style
                        .adaptiveWidth(true)
                        .textAlignVertical(Vertical.CENTER)),
                new Label().setText(info.get()).textStyle(style -> style
                        .adaptiveWidth(true)
                        .textAlignVertical(Vertical.CENTER)
                        .textAlignHorizontal(Horizontal.RIGHT)).layout(layout -> {
                    layout.flex(1);
                }).addEventListener(UIEvents.TICK, event -> ((Label) event.currentElement).setText(info.get()))
        );
    }

    @Override
    protected void onLayoutChanged() {
        super.onLayoutChanged();
        adaptPositionToElement(sceneView.sceneEditor.scene);
    }

}
