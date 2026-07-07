package net.minescript.fabric.fluxus;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSink;
import net.minescript.common.mixin.ChatComponentAccessor;

public final class FluxusChatTools {
  private FluxusChatTools() {}

  public static String getVisibleChatTranscript(Minecraft minecraft) {
    List<String> lines = getVisibleChatLines(minecraft);
    return String.join("\n", lines);
  }

  public static String getLatestVisibleChatLine(Minecraft minecraft) {
    List<String> lines = getVisibleChatLines(minecraft);
    return lines.isEmpty() ? "" : lines.getFirst();
  }

  public static boolean copyVisibleChat(Minecraft minecraft) {
    String transcript = getVisibleChatTranscript(minecraft);
    if (transcript.isBlank()) {
      return false;
    }
    minecraft.keyboardHandler.setClipboard(transcript);
    return true;
  }

  public static boolean copyLatestChat(Minecraft minecraft) {
    String latest = getLatestVisibleChatLine(minecraft);
    if (latest.isBlank()) {
      return false;
    }
    minecraft.keyboardHandler.setClipboard(latest);
    return true;
  }

  public static boolean openChatWithClipboard(Minecraft minecraft) {
    String clipboard = minecraft.keyboardHandler.getClipboard();
    if (clipboard == null || clipboard.isBlank()) {
      return false;
    }
    minecraft.setScreen(new ChatScreen(clipboard, false));
    return true;
  }

  private static List<String> getVisibleChatLines(Minecraft minecraft) {
    List<String> lines = new ArrayList<>();
    if (minecraft == null || minecraft.gui == null) {
      return lines;
    }

    ChatComponent chat = minecraft.gui.getChat();
    List<GuiMessage.Line> trimmed = ((ChatComponentAccessor) chat).minescript$getTrimmedMessages();
    for (GuiMessage.Line line : trimmed) {
      String text = formattedToString(line.content()).trim();
      if (!text.isEmpty()) {
        lines.add(text);
      }
    }
    return lines;
  }

  private static String formattedToString(net.minecraft.util.FormattedCharSequence content) {
    var collector = new CollectingCharacterVisitor();
    content.accept(collector);
    return collector.collect();
  }

  private static final class CollectingCharacterVisitor implements FormattedCharSink {
    private final StringBuilder builder = new StringBuilder();

    @Override
    public boolean accept(int index, Style style, int codePoint) {
      builder.appendCodePoint(codePoint);
      return true;
    }

    public String collect() {
      return builder.toString();
    }
  }
}
