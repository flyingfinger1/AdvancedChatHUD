/*
 * Copyright (C) 2022 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import fi.dy.masa.malilib.gui.GuiBase;
import fi.dy.masa.malilib.interfaces.IRenderer;
import io.github.darkkronicle.advancedchatcore.chat.AdvancedChatScreen;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.util.SyncTaskQueue;
import io.github.darkkronicle.advancedchathud.AdvancedChatHud;
import io.github.darkkronicle.advancedchathud.HudChatMessage;
import io.github.darkkronicle.advancedchathud.ResolutionEventHandler;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.config.gui.ChatWindowEditor;
import io.github.darkkronicle.advancedchathud.itf.IChatHud;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import io.github.darkkronicle.advancedchathud.tabs.CustomChatTab;
import io.github.darkkronicle.advancedchathud.tabs.MainChatTab;
import fi.dy.masa.malilib.render.GuiContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Style;
import net.minecraft.util.profiling.ProfilerFiller;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class WindowManager implements IRenderer, ResolutionEventHandler {

    private static final WindowManager INSTANCE = new WindowManager();
    private final Minecraft client;
    private final List<ChatWindow> windows = new ArrayList<>(8);
    private int dragX = 0;
    private int dragY = 0;
    private ChatWindow drag = null;
    private boolean resize = false;

    public static WindowManager getInstance() {
        return INSTANCE;
    }

    private WindowManager() {
        client = Minecraft.getInstance();
    }

    public void reset() {
        windows.clear();
    }

    private void addWindow(ChatWindow window) {
        // Remove duplicates being spawned from somewhere
        windows.removeIf(w -> w == window);
        windows.add(window);
    }

    public void loadFromJson(JsonArray array) {
        reset();
        if (!HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()) {
            if (array == null || array.size() == 0) {
                ChatWindow base = new ChatWindow(AdvancedChatHud.MAIN_CHAT_TAB);
                base.setSelected(true);
                addWindow(base);
                return;
            }
        } else {
            if (array == null || array.size() == 0) {
                return;
            }
        }
        ChatWindow.ChatWindowSerializer serializer = new ChatWindow.ChatWindowSerializer();
        for (JsonElement e : array) {
            if (!e.isJsonObject()) {
                continue;
            }
            ChatWindow w;
            try {
                w = serializer.load(e.getAsJsonObject());
                if (w == null) {
                    continue;
                }
            } catch (Exception err) {
                AdvancedChatHud.LOGGER.error("Error while loading in ChatWindow ", err);
                continue;
            }
            addWindow(w);
        }
    }

    public JsonArray saveJson() {
        JsonArray array = new JsonArray();
        ChatWindow.ChatWindowSerializer serializer = new ChatWindow.ChatWindowSerializer();
        for (ChatWindow w : windows) {
            array.add(serializer.save(w));
        }
        return array;
    }

    // 26.2: MaLiLib's IRenderer.onRenderGameOverlayPost(DrawContext) is now
    // onExtractGuiOverlayPost(GuiContext, float partialTick, ProfilerFiller) — the HUD overlay is drawn
    // during render-state extraction. GuiContext extends GuiGraphicsExtractor, so ChatWindow.render draws
    // through it directly. inGameHud.getTicks() -> gui.hud.getGuiTicks(); currentScreen -> gui.screen().
    @Override
    public void onExtractGuiOverlayPost(GuiContext drawContext, float partialTick, ProfilerFiller profiler) {
        // Only render the chat windows while actually in a world — without this, pinned
        // (always-visible) windows would also draw on the title screen / main menu where there is
        // no chat. (registerInGameGuiRenderer fires there too in 26.x.)
        if (client.level == null || client.player == null) {
            return;
        }
        boolean isFocused = isChatFocused();
        int ticks = client.gui.hud.getGuiTicks();
        if (!HudConfigStorage.General.RENDER_IN_OTHER_GUI.config.getBooleanValue() && !isFocused && client.gui.screen() != null) {
            return;
        }
        for (int i = windows.size() - 1; i >= 0; i--) {
            windows.get(i).render(drawContext, ticks, isFocused);
        }
    }

    public void resetScroll() {
        for (ChatWindow w : windows) {
            w.resetScroll();
        }
    }

    public boolean scroll(double amount, double mouseX, double mouseY) {
        for (ChatWindow w : windows) {
            // Prioritize mouse over first
            if (w.isMouseOver(mouseX, mouseY)) {
                w.scroll(amount);
                return true;
            }
        }
        for (ChatWindow w : windows) {
            if (w.isSelected()) {
                w.scroll(amount);
                return true;
            }
        }
        return false;
    }

    public Style getText(double mouseX, double mouseY) {
        for (ChatWindow w : windows) {
            if (w.isMouseOver(mouseX, mouseY)) {
                return w.getText(mouseX, mouseY);
            }
        }
        return null;
    }

    public ChatMessage getMessage(double mouseX, double mouseY) {
        for (ChatWindow w : windows) {
            if (w.isMouseOver(mouseX, mouseY)) {
                return w.getMessage(mouseX, mouseY);
            }
        }
        return null;
    }

    public boolean isChatFocused() {
        return this.client.gui.screen() instanceof AdvancedChatScreen;
    }

    public ChatWindow getSelected() {
        for (ChatWindow w : windows) {
            if (w.isSelected()) {
                return w;
            }
        }
        return null;
    }

    public void unSelect() {
        for (ChatWindow w : windows) {
            w.setSelected(false);
        }
    }

    public void setSelected(ChatWindow window) {
        for (ChatWindow w : windows) {
            w.setSelected(window.equals(w));
        }
        windows.removeIf(w -> w == window);
        windows.add(0, window);

        // 26.2: Minecraft.currentScreen -> gui.screen(); AdvancedTextField has no getText() (it extends
        // EditBox), so read its content via getValue().
        if (!HudConfigStorage.General.CHANGE_START_MESSAGE.config.getBooleanValue() || !(client.gui.screen() instanceof AdvancedChatScreen screen)) {
            return;
        }
        if (window.getTab() instanceof MainChatTab) {
            for (ChatWindow w : windows) {
                if (w.getTab() instanceof CustomChatTab tab2) {
                    if (screen.getChatField().getValue().startsWith(tab2.getStartingMessage()) && tab2.getStartingMessage().length() > 0) {
                        screen.getChatField().setText(screen.getChatField().getValue().substring(tab2.getStartingMessage().length()));
                        break;
                    }
                }
            }
        } else if (window.getTab() instanceof CustomChatTab tab) {
            boolean replaced = false;

            for (ChatWindow w : windows) {
                if (w.getTab() instanceof CustomChatTab tab2) {
                    if (screen.getChatField().getValue().startsWith(tab2.getStartingMessage()) && tab2.getStartingMessage().length() > 0) {
                        screen.getChatField().setText(tab.getStartingMessage() + screen.getChatField().getValue().substring(tab2.getStartingMessage().length()));

                        replaced = true;

                        break;
                    }
                }
            }

            if (!replaced) {
                screen.getChatField().setText(tab.getStartingMessage() + screen.getChatField().getValue());
            }
        }
    }

    // 26.2: the caller (HudSection) always passes an AdvancedChatScreen, and the 26.x click pipeline
    // (Screen.defaultHandleClickEvent) is protected/static and unreachable from here, so the parameter is
    // narrowed to AdvancedChatScreen to reach its chat field for command suggestion/insertion.
    public boolean mouseClicked(AdvancedChatScreen screen, double mouseX, double mouseY, int button) {
        ChatWindow over = null;
        for (ChatWindow w : windows) {
            if (w.isMouseOver(mouseX, mouseY)) {
                over = w;
                break;
            }
        }
        if (over == null) {
            if (HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()
                    && overVanillaHud(mouseX, mouseY)) {
                unSelect();
            }
            return false;
        }
        if (button == 0) {
            setSelected(over);
            if (over.isMouseOverDragBar(mouseX, mouseY)) {
                drag = over;
                dragX = (int) mouseX - over.getConvertedX();
                dragY = (int) mouseY - over.getConvertedY();
                resize = false;
            } else if (over.isMouseOverResize(mouseX, mouseY)) {
                drag = over;
                dragX = (int) mouseX - over.getConvertedWidth();
                dragY = (int) mouseY + over.getConvertedHeight();
                resize = true;
            }
            Style style = over.getText(mouseX, mouseY);
            if (style != null && handleStyleClick(screen, style)) {
                return true;
            }
            if (over.onMouseClicked(mouseX, mouseY, button)) {
                return true;
            }
        }
        return true;
    }

    /**
     * 26.2 replacement for {@code Screen.handleTextClick(Style)} (removed). Vanilla now dispatches chat
     * click events through the protected/static {@code Screen.defaultHandleClickEvent}, which is not
     * reachable from this non-Screen class. We dispatch the standard chat actions on the window text
     * with public APIs instead.
     *
     * <p>INTEGRATION NOTE: this covers OPEN_URL / RUN_COMMAND / SUGGEST_COMMAND / COPY_TO_CLIPBOARD —
     * the actions reachable from chat text. If full parity with vanilla's confirmation/link-trust flow is
     * needed, add a {@code public boolean handleChatWindowClick(Style)} to AdvancedChatScreen that calls
     * {@code defaultHandleClickEvent(...)} and delegate to it here.
     */
    private boolean handleStyleClick(AdvancedChatScreen screen, Style style) {
        ClickEvent clickEvent = style.getClickEvent();
        if (clickEvent == null) {
            return false;
        }
        if (clickEvent instanceof ClickEvent.OpenUrl openUrl) {
            net.minecraft.util.Util.getPlatform().openUri(openUrl.uri());
            return true;
        }
        if (clickEvent instanceof ClickEvent.RunCommand runCommand) {
            String command = runCommand.command();
            if (command.startsWith("/")) {
                command = command.substring(1);
            }
            if (client.player != null) {
                client.player.connection.sendCommand(command);
            }
            return true;
        }
        if (clickEvent instanceof ClickEvent.SuggestCommand suggestCommand) {
            screen.getChatField().setText(suggestCommand.command());
            return true;
        }
        if (clickEvent instanceof ClickEvent.CopyToClipboard copy) {
            client.keyboardHandler.setClipboard(copy.value());
            return true;
        }
        return false;
    }

    private boolean overVanillaHud(double mouseX, double mouseY) {
        return IChatHud.getInstance().isOver(mouseX, mouseY);
    }

    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (drag != null && !resize) {
            int x = Math.max((int) mouseX - dragX, 0);
            int y = Math.max((int) mouseY - dragY, drag.getActualHeight());
            x = Math.min(x, client.getWindow().getGuiScaledWidth() - drag.getConvertedWidth());
            y = Math.min(y, client.getWindow().getGuiScaledHeight());
            drag.setPosition(x, y);
            return true;
        } else if (drag != null) {
            int width = Math.max((int) mouseX - dragX, 80);
            int height = Math.max(dragY - (int) mouseY, 40);
            drag.setDimensions(width, height);
            return true;
        }
        return false;
    }

    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        if (drag != null) {
            drag = null;
            return true;
        }
        return false;
    }

    public void onTabButton(AbstractChatTab tab) {
        for (ChatWindow w : windows) {
            if (w.isSelected()) {
                w.setTab(tab);
                return;
            }
        }
        // Set it if no other window is selected
        IChatHud.getInstance().setTab(tab);
    }

    public void onTabAddButton(AbstractChatTab tab) {
        ChatWindow window = new ChatWindow(tab);
        ChatWindow sel = getSelected();
        if (sel == null) {
            sel = window;
        }
        window.setPosition(sel.getConvertedX() + 15, sel.getConvertedY() + 15);
//        windows.add(window);
        setSelected(window);
    }

    public void deleteWindow(ChatWindow chatWindow) {
        windows.remove(chatWindow);
        if (!windows.isEmpty()) {
            for (ChatWindow w : windows) {
                w.setSelected(false);
            }
            windows.get(0).setSelected(true);
        }
    }

    public void onStackedMessage(HudChatMessage message) {
        for (ChatWindow w : windows) {
            w.stackMessage(message);
        }
    }

    public void onNewMessage(HudChatMessage message) {
        IChatHud.getInstance().addMessage(message);
        for (ChatWindow w : windows) {
            w.addMessage(message);
        }
    }

    public void clear() {
        IChatHud.getInstance().clear(false);
        for (ChatWindow w : windows) {
            w.clearLines();
        }
    }

    @Override
    public void onResolutionChange() {
        // Delay resolution change because when toggling full screen it can take a render cycle for it to apply
        SyncTaskQueue.getInstance().add(2, () -> {
            for (ChatWindow w : windows) {
                w.onResolutionChange();
            }
        });
    }

    public void onRemoveMessage(ChatMessage remove) {
        IChatHud.getInstance().removeMessage(remove);
        for (ChatWindow w : windows) {
            w.removeMessage(remove);
        }
    }

    public void duplicateTab(ChatWindow hovered, int x, int y) {
        ChatWindow window = new ChatWindow(IChatHud.getInstance().getTab());
        window.setRelativeDimensions(hovered.getWidthPercent(), hovered.getHeightPercent());
        window.setVisibility(hovered.getVisibility());
        window.setPosition(x, y);
        setSelected(window);
    }

    public ChatWindow getHovered(int x, int y) {
        int windowHeight = client.getWindow().getGuiScaledHeight();
        for (ChatWindow w : windows) {
            int wX = w.getConvertedX();
            int wY = w.getConvertedY();
            if (x >= wX && x <= wX + w.getConvertedWidth() && y <= wY && y >= wY - w.getConvertedHeight()) {
                return w;
            }
        }
        return null;
    }

    public void configureTab(AdvancedChatScreen screen, ChatWindow window) {
        GuiBase.openGui(new ChatWindowEditor(screen, window));
    }
}
