package net.minescript.fabric.fluxus;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.ClientCommands;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.network.chat.Component;

public final class FluxusClientCommands {
  private FluxusClientCommands() {}

  public static void register(
      CommandDispatcher<FabricClientCommandSource> dispatcher, CommandBuildContext registryAccess) {
    dispatcher.register(
        ClientCommands.literal("windowspy")
            .executes(
                context -> {
                  openWindowSpy();
                  return 1;
                }));
    dispatcher.register(
        ClientCommands.literal("shulkrspy")
            .executes(
                context -> {
                  openWindowSpy();
                  return 1;
                }));
    dispatcher.register(
        ClientCommands.literal("shulkroverlay")
            .executes(
                context -> {
                  toggleOverlayEditor();
                  return 1;
                })
            .then(
                ClientCommands.literal("show")
                    .executes(
                        context -> {
                          setOverlayVisible(true);
                          return 1;
                        }))
            .then(
                ClientCommands.literal("hide")
                    .executes(
                        context -> {
                          setOverlayVisible(false);
                          return 1;
                        }))
            .then(
                ClientCommands.literal("edit")
                    .executes(
                        context -> {
                          toggleOverlayEditor();
                          return 1;
                        }))
            .then(
                ClientCommands.literal("reset")
                    .executes(
                        context -> {
                          resetOverlay();
                          return 1;
                        })));
  }

  private static void openWindowSpy() {
    var minecraft = Minecraft.getInstance();
    WindowSpyOverlay.toggle();
    if (minecraft.player != null) {
      minecraft.player.sendOverlayMessage(
          Component.literal(
              WindowSpyOverlay.isVisible()
                  ? "WindowSpy enabled. Press TAB to focus and drag."
                    : "WindowSpy disabled."));
    }
  }

  public static void onEndClientTick(Minecraft minecraft) {
    WindowSpyOverlay.onClientTick(minecraft);
    ShulkrHudOverlay.onClientTick(minecraft);
  }

  private static void toggleOverlayEditor() {
    var minecraft = Minecraft.getInstance();
    ShulkrHudOverlay.setRendererActive(true);
    ShulkrHudOverlay.setEditMode(!ShulkrHudOverlay.editMode());
    if (minecraft.player != null) {
      minecraft.player.sendOverlayMessage(
          Component.literal(
              ShulkrHudOverlay.editMode()
                  ? "Shulkr overlay edit mode enabled. Drag widgets around."
                  : "Shulkr overlay edit mode disabled."));
    }
  }

  private static void setOverlayVisible(boolean visible) {
    var minecraft = Minecraft.getInstance();
    ShulkrHudOverlay.setRendererActive(visible);
    if (minecraft.player != null) {
      minecraft.player.sendOverlayMessage(
          Component.literal(visible ? "Shulkr overlay HUD shown." : "Shulkr overlay HUD hidden."));
    }
  }

  private static void resetOverlay() {
    var minecraft = Minecraft.getInstance();
    ShulkrHudOverlay.resetLayout();
    if (minecraft.player != null) {
      minecraft.player.sendOverlayMessage(Component.literal("Shulkr overlay layout reset."));
    }
  }
}
