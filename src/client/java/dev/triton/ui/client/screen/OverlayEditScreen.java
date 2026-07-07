package dev.triton.ui.client.screen;

import com.mojang.blaze3d.platform.InputConstants;
import dev.triton.ui.client.modern.TritonModernFragment;
import icyllis.modernui.mc.MuiModApi;
import net.minescript.fabric.fluxus.ShulkrHudOverlay;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

public final class OverlayEditScreen extends Screen {
    public OverlayEditScreen() {
        super(Component.literal("Edit Shulkr overlays"));
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Deliberately transparent: the HUD renderer supplies the edit frames and hint.
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float tickDelta) {
        // Minecraft screens blur their in-world background by default. Editing must stay sharp.
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && ShulkrHudOverlay.beginDrag(event.x(), event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        return event.button() == InputConstants.MOUSE_BUTTON_LEFT
                && ShulkrHudOverlay.dragTo(event.x(), event.y());
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == InputConstants.MOUSE_BUTTON_LEFT) {
            ShulkrHudOverlay.endDrag();
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (event.key() == InputConstants.KEY_ESCAPE || event.key() == InputConstants.KEY_U) {
            finishEditing();
            return true;
        }
        return true;
    }

    @Override
    public void onClose() {
        finishEditing();
    }

    private void finishEditing() {
        ShulkrHudOverlay.endDrag();
        ShulkrHudOverlay.setEditMode(false);
        MuiModApi.openScreen(new TritonModernFragment("Overlays"));
    }
}
