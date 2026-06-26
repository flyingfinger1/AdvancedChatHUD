/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.gui;

import fi.dy.masa.malilib.util.InfoUtils;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.darkkronicle.advancedchatcore.chat.AdvancedChatScreen;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.gui.ContextMenu;
import io.github.darkkronicle.advancedchatcore.gui.IconButton;
import io.github.darkkronicle.advancedchatcore.interfaces.AdvancedChatScreenSection;
import io.github.darkkronicle.advancedchatcore.util.Color;
import io.github.darkkronicle.advancedchatcore.util.RowList;
import io.github.darkkronicle.advancedchatcore.util.TextBuilder;
import io.github.darkkronicle.advancedchathud.AdvancedChatHud;
import io.github.darkkronicle.advancedchathud.HudChatMessageHolder;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.itf.IChatHud;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import io.github.darkkronicle.advancedchathud.tabs.CustomChatTab;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import com.mojang.blaze3d.platform.InputConstants;
import fi.dy.masa.malilib.render.GuiContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.resources.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;

@Environment(EnvType.CLIENT)
public class HudSection extends AdvancedChatScreenSection {

    private static final Identifier ADD_ICON =
            Identifier.fromNamespaceAndPath(AdvancedChatHud.MOD_ID, "textures/gui/chatwindow/add_window.png");

    private static final Identifier RESET_ICON =
            Identifier.fromNamespaceAndPath(AdvancedChatHud.MOD_ID, "textures/gui/chatwindow/reset_windows.png");

    private ContextMenu menu = null;

    private ChatMessage message = null;

    public HudSection(AdvancedChatScreen screen) {
        super(screen);
    }

    private Color getColor() {
        Color baseColor;
        ChatWindow sel = WindowManager.getInstance().getSelected();
        if (sel == null) {
            baseColor = HudConfigStorage.MAIN_TAB.getInnerColor().config.get();
        } else {
            baseColor = sel.getTab().getInnerColor();
        }
        return baseColor;
    }

    @Override
    public void initGui() {
        boolean left = !HudConfigStorage.General.TAB_BUTTONS_ON_RIGHT.config.getBooleanValue();
        List<AbstractChatTab> tabs = new ArrayList<>(AdvancedChatHud.MAIN_CHAT_TAB.getAllChatTabs());
        if (!left) {
            Collections.reverse(tabs);
        }
        // 26.2: AdvancedChatScreen's side-button RowList is now typed RowList<IconButton>, but tabs
        // are TabButtons (CleanButton, not IconButton). RowList only stores/iterates and the screen
        // renders/dispatches via CleanButton-level virtuals, so a raw RowList stays runtime-safe.
        @SuppressWarnings({"unchecked", "rawtypes"})
        RowList rows = left ? getScreen().getLeftSideButtons() : getScreen().getRightSideButtons();
        rows.createSection("tabs", 0);
        for (AbstractChatTab tab : tabs) {
            rows.add("tabs", TabButton.fromTab(tab, 0, 0));
        }
        IconButton window = new IconButton(0, 0, 14, 32, ADD_ICON, (button) -> WindowManager.getInstance().onTabAddButton(IChatHud.getInstance().getTab()));
        IconButton reset = new IconButton(0, 0, 14, 32, RESET_ICON, (button) -> WindowManager.getInstance().reset());
        if (left) {
            rows.add("tabs", window);
            rows.add("tabs", reset);
        } else {
            rows.add("tabs", window, 0);
            rows.add("tabs", reset, 0);
        }

        if (getScreen().getChatField().getValue().isEmpty()) {
            ChatWindow chatWindow = WindowManager.getInstance().getSelected();
            if (chatWindow == null) {
                return;
            }
            AbstractChatTab tab = chatWindow.getTab();
            if (tab instanceof CustomChatTab custom) {
                getScreen().getChatField().setText(custom.getStartingMessage());
                getScreen().getChatField().moveCursorTo(custom.getStartingMessage().length(), false);
            }
        }
    }

    @Override
    public void render(GuiGraphicsExtractor context, int mouseX, int mouseY, float deltaTicks) {
        if (menu != null) {
            menu.render(GuiContext.fromGuiGraphics(context), mouseX, mouseY, true);
        }
    }

    public void createContextMenu(int mouseX, int mouseY) {
        LinkedHashMap<Component, ContextMenu.ContextConsumer> actions = new LinkedHashMap<>();
        message = WindowManager.getInstance().getMessage(mouseX, mouseY);
        if (message != null) {
            TextBuilder data = new TextBuilder();
            try {
                data.append(
                        message.getTime().format(DateTimeFormatter.ofPattern(ConfigStorage.General.TIME_FORMAT.config.getStringValue())), Style.EMPTY.applyFormat(ChatFormatting.AQUA)
                );
            } catch (IllegalArgumentException e) {
                AdvancedChatHud.LOGGER.warn("Can't format time for context menu!", e);
            }
            if (message.getOwner() != null) {
                data.append(" - ", Style.EMPTY.applyFormat(ChatFormatting.GRAY));
                if (message.getOwner().getEntry().getTabListDisplayName() != null) {
                    data.append(message.getOwner().getEntry().getTabListDisplayName());
                } else {
                    data.append(message.getOwner().getEntry().getProfile().name());
                }
            }
            if (!data.build().getString().isBlank())  {
                actions.put(data.build(), (x, y) -> {
                    InfoUtils.printActionbarMessage("advancedchathud.context.nothing");
                });
            }
            actions.put(Component.literal(StringUtils.translate("advancedchathud.context.copy")), (x, y) -> {
                Minecraft.getInstance().keyboardHandler.setClipboard(message.getOriginalText().getString());
                InfoUtils.printActionbarMessage("advancedchathud.context.copied");
            });
            actions.put(Component.literal(StringUtils.translate("advancedchathud.context.delete")), (x, y) -> {
                HudChatMessageHolder.getInstance().removeChatMessage(message);
            });
            if (message.getOwner() != null) {
                actions.put(Component.literal(StringUtils.translate("advancedchathud.context.messageowner")), (x, y) -> {
                    getScreen().getChatField().setText("/msg " + message.getOwner().getEntry().getProfile().name() + " ");
                });
            }
        }
        ChatWindow hovered = WindowManager.getInstance().getHovered(mouseX, mouseY);
        actions.put(Component.literal(StringUtils.translate("advancedchathud.context.removeallwindows")), (x, y) -> WindowManager.getInstance().reset());
        actions.put(Component.literal(StringUtils.translate("advancedchathud.context.clearallmessages")), (x, y) -> WindowManager.getInstance().clear());
        if (hovered != null) {
            actions.put(Component.literal(StringUtils.translate("advancedchathud.context.duplicatewindow")), (x, y) -> WindowManager.getInstance().duplicateTab(hovered, x, y));
            actions.put(Component.literal(StringUtils.translate("advancedchathud.context.configurewindow")), (x, y) -> WindowManager.getInstance().configureTab(getScreen(), hovered));
            actions.put(Component.literal(StringUtils.translate("advancedchathud.context.minimalist")), (x, y) -> hovered.toggleMinimalist());
        }
        menu = new ContextMenu(mouseX, mouseY, actions, () -> menu = null);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 1) {
            createContextMenu((int) mouseX, (int) mouseY);
            return true;
        }
        if (menu != null && menu.onMouseClicked(
                new MouseButtonEvent(mouseX, mouseY, new MouseButtonInfo(button, 0)), false)) {
            return true;
        }
        return WindowManager.getInstance().mouseClicked(getScreen(), mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int mouseButton) {
        return WindowManager.getInstance().mouseReleased(mouseX, mouseY, mouseButton);
    }

    @Override
    public boolean mouseDragged(
            double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        return WindowManager.getInstance().mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        if (amount > 1.0D) {
            amount = 1.0D;
        }

        if (amount < -1.0D) {
            amount = -1.0D;
        }
        // 26.2: static Screen.hasShiftDown() is gone; query the window directly (mirrors
        // AdvancedChatScreen.mouseScrolled).
        com.mojang.blaze3d.platform.Window window = Minecraft.getInstance().getWindow();
        boolean shift = InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
        if (!shift) {
            amount *= 7.0D;
        }

        return WindowManager.getInstance().scroll(amount, mouseX, mouseY);
    }
}
