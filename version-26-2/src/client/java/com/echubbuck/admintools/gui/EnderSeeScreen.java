package com.echubbuck.admintools.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

public class EnderSeeScreen extends AbstractContainerScreen<EnderSeeScreenHandler> {
    private static final Identifier CONTAINER_BACKGROUND = Identifier.withDefaultNamespace("textures/gui/container/generic_54.png");
    private static final int ROWS = 3;

    public EnderSeeScreen(EnderSeeScreenHandler handler, Inventory inventory, Component title) {
        super(handler, inventory, title, 176, 114 + ROWS * 18);
        this.inventoryLabelY = this.imageHeight - 94;
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        int x = (width - imageWidth) / 2;
        int y = (height - imageHeight) / 2;
        context.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, x, y, 0.0f, 0.0f, imageWidth, ROWS * 18 + 17, 256, 256);
        context.blit(RenderPipelines.GUI_TEXTURED, CONTAINER_BACKGROUND, x, y + ROWS * 18 + 17, 0.0f, 125.0f, imageWidth, 96, 256, 256);
    }

    @Override
    public void extractLabels(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        context.text(font, title, titleLabelX, titleLabelY, 0x404040, false);
        context.text(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0x404040, false);
    }
}
