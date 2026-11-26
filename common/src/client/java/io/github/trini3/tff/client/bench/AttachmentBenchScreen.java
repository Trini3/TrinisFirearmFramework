package io.github.trini3.tff.client.bench;

import io.github.trini3.tff.Tff;
import io.github.trini3.tff.bench.AttachmentBenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Gun slot on the left, scope slot on the right, drawn in the vanilla container style. */
public class AttachmentBenchScreen extends AbstractContainerScreen<AttachmentBenchMenu> {
	private static final Identifier BACKGROUND = Tff.id("textures/gui/container/attachment_bench.png");

	public AttachmentBenchScreen(AttachmentBenchMenu menu, Inventory inventory, Component title) {
		super(menu, inventory, title);
	}

	@Override
	public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
		super.extractBackground(graphics, mouseX, mouseY, partialTick);
		graphics.blit(RenderPipelines.GUI_TEXTURED, BACKGROUND, this.leftPos, this.topPos, 0.0F, 0.0F, this.imageWidth, this.imageHeight, 256, 256);
	}
}
