/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.gui;

import com.google.gson.JsonObject;
import fi.dy.masa.malilib.render.GuiContext;
import fi.dy.masa.malilib.util.StringUtils;
import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchatcore.config.ConfigStorage;
import io.github.darkkronicle.advancedchatcore.interfaces.IJsonSave;
import io.github.darkkronicle.advancedchatcore.util.*;
import io.github.darkkronicle.advancedchathud.AdvancedChatHud;
import io.github.darkkronicle.advancedchathud.HudChatMessage;
import io.github.darkkronicle.advancedchathud.HudChatMessageHolder;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Environment(EnvType.CLIENT)
public class ChatWindow {

    private double scrolledHeight = 0;
    private double scrollStart = 0;
    private double scrollEnd = 0;

    private long lastScroll = 0;
    private int scrollDuration = 200;

    @Getter
    @Setter
    private double yPercent;

    @Getter
    @Setter
    private double xPercent;

    @Getter
    @Setter
    private double widthPercent;

    @Getter
    @Setter
    private double heightPercent;

    @Getter
    @Setter
    private boolean renderRight = false;

    @Getter
    @Setter
    private boolean minimalist = false;

    @Getter
    @Setter
    private boolean renderTopFirst = false;

    private final Minecraft client;

    @Setter
    @Getter
    private HudConfigStorage.Visibility visibility =
            (HudConfigStorage.Visibility)
                    HudConfigStorage.General.VISIBILITY.config.getOptionListValue();

    private List<ChatMessage> lines;

    @Getter
    @Setter
    private boolean selected;

    @Getter
    private AbstractChatTab tab;

    private static final Identifier X_ICON = Identifier.fromNamespaceAndPath(AdvancedChatHud.MOD_ID, "textures/gui/chatwindow/x_icon.png");

    private static final Identifier RESIZE_ICON = Identifier.fromNamespaceAndPath(AdvancedChatHud.MOD_ID, "textures/gui/chatwindow/resize_icon.png");

    public ChatWindow(AbstractChatTab tab) {
        this.client = Minecraft.getInstance();
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        int scaledWidth = client.getWindow().getGuiScaledWidth();
        this.yPercent =
                ((double) (scaledHeight - HudConfigStorage.General.Y.config.getIntegerValue()))
                        / scaledHeight;
        this.xPercent =
                ((double) HudConfigStorage.General.X.config.getIntegerValue()) / scaledWidth;
        this.widthPercent =
                ((double) HudConfigStorage.General.WIDTH.config.getIntegerValue()) / scaledWidth;
        this.heightPercent =
                ((double) HudConfigStorage.General.HEIGHT.config.getIntegerValue()) / scaledHeight;
        this.setTab(tab);
    }

    public void setRelativePosition(double x, double y) {
        if (x > 2) {
            x = 0;
        }
        if (y > 2) {
            y = 0;
        }
        this.xPercent = x;
        this.yPercent = y;
    }

    public void setPosition(int x, int y) {
        int scaledHeight = client.getWindow().getGuiScaledHeight();
        this.xPercent = ((double) x) / client.getWindow().getGuiScaledWidth();
        this.yPercent = ((double) y) / scaledHeight;
    }

    public void setRelativeDimensions(double width, double height) {
        this.widthPercent = width;
        this.heightPercent = height;
    }

    public void setTab(AbstractChatTab tab) {
        this.tab = tab;
        this.lines = new ArrayList<>();
        List<HudChatMessage> messages = HudChatMessageHolder.getInstance().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            addMessage(messages.get(i), false, false);
        }
        WindowManager.getInstance().setSelected(this);
    }

    public void addMessage(HudChatMessage message) {
        this.addMessage(message, false, true);
    }

    public void addMessage(HudChatMessage message, boolean force, boolean setTicks) {
        if (force || message.getTabs().contains(tab)) {
            ChatMessage newMessage = message.getMessage().shallowClone(getPaddedWidth());
            if (setTicks) {
                // 26.2: inGameHud.getTicks() -> gui.hud.getGuiTicks().
                newMessage.setCreationTick(Minecraft.getInstance().gui.hud.getGuiTicks());
            }
            this.lines.add(0, newMessage);
            if (scrolledHeight > 0) {
                scrolledHeight += (HudConfigStorage.General.LINE_SPACE.config.getIntegerValue()) * newMessage.getLineCount() + HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
            }
            int visibleMessagesMaxSize =
                    HudConfigStorage.General.STORED_LINES.config.getIntegerValue();
            while (this.lines.size() > visibleMessagesMaxSize) {
                this.lines.remove(this.lines.size() - 1);
            }
        }
    }

    public int getConvertedX() {
        return (int) ((double) client.getWindow().getGuiScaledWidth() * xPercent);
    }

    public int getConvertedY() {
        return (int) ((double) client.getWindow().getGuiScaledHeight() * yPercent);
    }

    public int getConvertedWidth() {
        return (int) ((double) client.getWindow().getGuiScaledWidth() * widthPercent);
    }

    public int getConvertedHeight() {
        return (int) ((double) client.getWindow().getGuiScaledHeight() * heightPercent);
    }

    public int getTotalLines() {
        int count = 0;
        for (ChatMessage line : lines) {
            count += line.getLineCount();
        }
        return count;
    }

    public void scroll(double amount) {
        this.scrollEnd = this.scrolledHeight + amount * 15;
        this.scrollStart = this.scrolledHeight;
        lastScroll = Util.getMillis();
    }

    public void updateScroll() {
        long time = Util.getMillis();
        scrollDuration = 300;
        scrolledHeight = scrollStart + (
                (scrollEnd - scrollStart) * (1 - ((ConfigStorage.Easing) HudConfigStorage.General.SCROLL_TYPE.config.getOptionListValue()).apply(
                        1 - ((float) (time - lastScroll)) / HudConfigStorage.General.SCROLL_TIME.config.getIntegerValue()
                ))
        );
        int totalHeight = getTotalHeight();
        if (this.scrolledHeight > totalHeight) {
            scrollStart = totalHeight;
            scrollEnd = totalHeight;
            lastScroll = 0;
            this.scrolledHeight = totalHeight;
        }

        if (this.scrolledHeight <= 0) {
            scrollStart = 0;
            scrollEnd = 0;
            lastScroll = 0;
            this.scrolledHeight = 0;
        }
    }

    // 26.2: DrawContext -> MaLiLib GuiContext (extends GuiGraphicsExtractor). GuiGraphicsExtractor.fill
    // takes (x1, y1, x2, y2, argb), so the width/height-based helpers convert to corner coords here.
    public static void drawRect(
            GuiContext ctx, int x, int y, int width, int height, int color) {
        ctx.fill(x, y, x + width, y + height, color);
    }

    public static void fill(GuiContext ctx, int x, int y, int x2, int y2, int color) {
        ctx.fill(x, y, x2, y2, color);
    }

    private static void drawOutline(
            GuiContext ctx, int x, int y, int width, int height, int color) {
        drawRect(ctx, x, y, 1, height, color);
        drawRect(ctx, x + width - 1, y, 1, height, color);
        drawRect(ctx, x + 1, y, width - 2, 1, color);
        drawRect(ctx, x + 1, y + height - 1, width - 2, 1, color);
    }

    public void resetScroll() {
        this.lastScroll = 0;
        this.scrollStart = 0;
        this.scrollEnd = 0;
        this.scrolledHeight = 0;
    }

    public int getPaddedWidth() {
        return (getScaledWidth()
                - HudConfigStorage.General.LEFT_PAD.config.getIntegerValue()
                - HudConfigStorage.General.RIGHT_PAD.config.getIntegerValue()
                - headOffset());
    }

    private int headOffset() {
        return HudConfigStorage.General.CHAT_HEADS.config.getBooleanValue() ? 10 : 0;
    }

    private int getActualY(int y) {
        return (int) Math.ceil(this.getConvertedY() / getScale()) - y;
    }

    private int getLeftX() {
        return (int) Math.ceil(this.getConvertedX() / getScale());
    }

    private int getPaddedLeftX() {
        return (getLeftX()
                + (int)
                Math.ceil(
                        HudConfigStorage.General.LEFT_PAD.config.getIntegerValue()
                                + (renderRight ? 0 : headOffset())));
    }

    private double getScale() {
        return HudConfigStorage.General.CHAT_SCALE.config.getDoubleValue();
    }

    private int getRightX() {
        return getLeftX() + getScaledWidth();
    }

    private int getPaddedRightX() {
        return (getRightX() - HudConfigStorage.General.RIGHT_PAD.config.getIntegerValue() - (renderRight ? headOffset() : 0));
    }

    public int getActualHeight() {
        return getConvertedHeight() + getBarHeight();
    }

    private int getScaledHeight() {
        return (int) Math.ceil(getConvertedHeight() / getScale());
    }

    private int getScaledWidth() {
        return (int) Math.ceil(getConvertedWidth() / getScale());
    }

    private int getBarHeight() {
        return 14;
    }

    private int getScaledBarHeight() {
        return (int) Math.ceil(14 * getScale());
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        int x = getConvertedX();
        int y = getConvertedY();
        return (x <= mouseX
                && x + getConvertedWidth() >= mouseX
                && y >= mouseY
                && y - getActualHeight() <= mouseY);
    }

    public int getTotalHeight() {
        return getTotalLines() * HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() + (lines.size() - 1) * HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
    }

    public void render(GuiContext drawContext, int ticks, boolean focused) {
        if (!focused) {
            resetScroll();
        }
        updateScroll();
        if (visibility == HudConfigStorage.Visibility.FOCUSONLY && !focused) {
            return;
        }

        int totalLines = getTotalLines();

        boolean chatFocused = visibility == HudConfigStorage.Visibility.ALWAYS || focused;
        int totalHeight = getTotalHeight();

        if (scrolledHeight > totalHeight) {
            scrolledHeight = totalHeight;
        }

        int lines = 0;
        int currentHeight = 0;
        int renderedLines = 0;
        int scaledWidth = getScaledWidth();
        int scaledHeight = getScaledHeight();
        int leftX = getLeftX();
        int padLX = getPaddedLeftX();
        int rightX = getRightX();
        int padRX = getPaddedRightX();
        int limit = getScaledHeight() - HudConfigStorage.General.TOP_PAD.config.getIntegerValue() + HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
        int lastY = 0;
        LimitedInteger y =
                new LimitedInteger(
                        getScaledHeight() - HudConfigStorage.General.TOP_PAD.config.getIntegerValue() + (HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue() + HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() * (renderTopFirst ? 2 : 1)),
                        renderTopFirst ? HudConfigStorage.General.TOP_PAD.config.getIntegerValue() + HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() : HudConfigStorage.General.BOTTOM_PAD.config.getIntegerValue());

        // 26.2: ScissorUtil takes a (now nonexistent) DrawContext; GuiGraphicsExtractor.enableScissor
        // takes corner coords (x1, y1, x2, y2). Inline it here so this file does not depend on the
        // not-yet-migrated ScissorUtil.
        {
            int sx = getConvertedX();
            int sy = getConvertedY() - getConvertedHeight();
            drawContext.enableScissor(sx, sy, sx + getConvertedWidth(), sy + getConvertedHeight());
        }

        boolean foundScroll = false;
        for (int j = 0; j < this.lines.size(); j++) {
            ChatMessage message = this.lines.get(j);
            // To get the proper index of reversed
            for (int i = message.getLineCount() - 1; i >= 0; i--) {
                int lineIndex = renderTopFirst ? i : message.getLineCount() - i - 1;
                lines++;
                if (currentHeight < scrolledHeight - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue()) {
                    currentHeight += HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                    continue;
                }
                boolean renderBelow = false;
                if (!foundScroll) {
                    foundScroll = true;
                    y.incrementIfPossible(currentHeight - (int) scrolledHeight - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() + client.font.lineHeight);
                    renderBelow = true;
                }
                currentHeight += HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                if (!y.incrementIfPossible(HudConfigStorage.General.LINE_SPACE.config.getIntegerValue())) {
                    break;
                }
                ChatMessage.AdvancedChatLine line = message.getLines().get(renderTopFirst ? message.getLineCount() - i - 1 : i);
                drawLine(
                        drawContext,
                        line,
                        leftX,
                        renderTopFirst ? limit - y.getValue() + client.font.lineHeight : y.getValue(),
                        padLX,
                        padRX,
                        lineIndex,
                        j,
                        renderedLines,
                        chatFocused,
                        ticks,
                        renderBelow ? y.getValue() - lastY - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() - 1 : 0);
                lastY = y.getValue();
                renderedLines++;
            }
            if (currentHeight >= scrolledHeight) {
                if (lines == totalLines) {
                    break;
                }
                if (!y.isPossible(HudConfigStorage.General.LINE_SPACE.config.getIntegerValue() + HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue()) || !y.incrementIfPossible(HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue())) {
                    break;
                }
            }
            currentHeight += HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
        }
        drawContext.disableScissor();
        if (renderedLines == 0) {
            y.setValue(0);
        }

        if (focused) {
            if (isSelected()) {
                tab.resetUnread();
            }
        }

        if (focused && !isMinimalist()) {
            drawOutline(
                    drawContext,
                    leftX,
                    getActualY(0) - scaledHeight - 1,
                    scaledWidth,
                    scaledHeight + 1,
                    tab.getBorderColor().color());
            int scaledBar = getBarHeight();
            int newY = getScaledHeight() + scaledBar;
            String label = tab.getAbbreviation();
            int labelWidth = StringUtils.getStringWidth(label) + 8;
            drawRect(
                    drawContext,
                    leftX,
                    getActualY(newY),
                    labelWidth,
                    scaledBar,
                    tab.getMainColor().color());
            drawOutline(
                    drawContext,
                    leftX,
                    getActualY(newY),
                    labelWidth,
                    scaledBar,
                    tab.getBorderColor().color());
            drawContext.drawCenteredString(
                    Minecraft.getInstance().font,
                    tab.getAbbreviation(),
                    leftX + (labelWidth) / 2,
                    getActualY(newY - 3),
                    Colors.getInstance().getColorOrWhite("white").color()
            );
            drawRect(
                    drawContext,
                    leftX + labelWidth,
                    getActualY(newY),
                    getScaledWidth() - labelWidth,
                    scaledBar,
                    selected ? tab.getMainColor().color() : tab.getInnerColor().color());
            drawOutline(
                    drawContext,
                    leftX + labelWidth,
                    getActualY(newY),
                    getScaledWidth() - labelWidth,
                    scaledBar,
                    tab.getBorderColor().color());

            drawOutline(
                    drawContext,
                    rightX - scaledBar,
                    getActualY(newY),
                    scaledBar,
                    scaledBar,
                    tab.getBorderColor().color());
            drawOutline(
                    drawContext,
                    rightX - scaledBar * 2 + 1,
                    getActualY(newY),
                    scaledBar,
                    scaledBar,
                    tab.getBorderColor().color());
            drawOutline(
                    drawContext,
                    rightX - scaledBar * 3 + 2,
                    getActualY(newY),
                    scaledBar,
                    scaledBar,
                    tab.getBorderColor().color());

            // 26.2: DrawContext.drawTexture(pipeline, id, x, y, u, v, w, h, texW, texH) drawing the whole
            // 32x32 icon scaled into a (scaledBar-2) square is replaced by GuiGraphicsExtractor.blit(id,
            // x1, y1, x2, y2, minU, maxU, minV, maxV) with corner coords and normalized (0..1) UVs
            // (same scaled-blit pattern Core's IconButton uses).
            int iconSize = scaledBar - 2;

            // Close
            drawContext.blit(
                    X_ICON,
                    rightX - scaledBar + 1,
                    getActualY(newY - 1),
                    rightX - scaledBar + 1 + iconSize,
                    getActualY(newY - 1) + iconSize,
                    0.0f, 1.0f, 0.0f, 1.0f);

            // Resize
            drawContext.blit(
                    RESIZE_ICON,
                    rightX - scaledBar * 2 + 2,
                    getActualY(newY - 1),
                    rightX - scaledBar * 2 + 2 + iconSize,
                    getActualY(newY - 1) + iconSize,
                    0.0f, 1.0f, 0.0f, 1.0f);

            // Visibility
            drawContext.blit(
                    visibility.getTexture(),
                    rightX - scaledBar * 3 + 3,
                    getActualY(newY - 1),
                    rightX - scaledBar * 3 + 3 + iconSize,
                    getActualY(newY - 1) + iconSize,
                    0.0f, 1.0f, 0.0f, 1.0f);

            // 26.2: client.mouse.getX()/getY() -> client.mouseHandler.xpos()/ypos().
            double mouseX = client.mouseHandler.xpos() / 2;
            double mouseY = client.mouseHandler.ypos() / 2;
            if (isMouseOverVisibility(mouseX, mouseY)) {
                drawContext.drawCenteredString(
                        client.font,
                        visibility.getDisplayName(),
                        (int) (mouseX / getScale() + 4),
                        (int) (mouseY / getScale() - 16),
                        Colors.getInstance().getColorOrWhite("white").color());
            }
        }

        if (chatFocused) {
            if (y.getValue() < getScaledHeight()) {
                // Check to see if we've already gone above the boundaries
                fill(
                        drawContext,
                        leftX,
                        getActualY(renderTopFirst ? limit - y.getValue() : y.getValue()),
                        rightX,
                        getActualY(renderTopFirst ? 0 : getScaledHeight()),
                        tab.getInnerColor().color());
            }
            // Scroll bar
            float add = (float) (scrolledHeight) / (getTotalHeight());
            int scrollHeight = (int) (add * (getScaledHeight() - 10));
            drawRect(
                    drawContext,
                    getScaledWidth() + leftX - 1,
                    getActualY(scrollHeight + 10),
                    1,
                    10,
                    Colors.getInstance().getColorOrWhite("white").color());
        }
    }

    private void drawLine(
            GuiContext drawContext,
            ChatMessage.AdvancedChatLine line,
            int x,
            int y,
            int pLX,
            int pRX,
            int lineIndex,
            int messageIndex,
            int renderedLines,
            boolean focused,
            int ticks,
            int renderBelow) {
        int height = HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
        if (renderedLines == 0) {
            if (focused) {
                height += renderTopFirst ? HudConfigStorage.General.TOP_PAD.config.getIntegerValue() + HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue() + 1 : HudConfigStorage.General.BOTTOM_PAD.config.getIntegerValue();
                height += renderBelow;
            }
        } else if (lineIndex == 0) {
            height += HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
            // Start of a line
        }
        Color background = line.getParent().getBackgroundColor();
        Color text = HudConfigStorage.General.EMPTY_TEXT_COLOR.config.get();
        if (background == null) {
            background = tab.getInnerColor();
        }
        if (messageIndex % 2 == 0
                && HudConfigStorage.General.ALTERNATE_LINES.config.getBooleanValue()) {
            if (background.alpha() <= 215) {
                background = background.withAlpha(background.alpha() + 40);
            } else {
                background = background.withAlpha(background.alpha() - 40);
            }
        }
        float applied = 1;
        if (!focused) {
            // Find fade percentage
            int fadeStart = HudConfigStorage.General.FADE_START.config.getIntegerValue();
            int fadeStop = fadeStart + HudConfigStorage.General.FADE_TIME.config.getIntegerValue();
            int timeAlive = ticks - line.getParent().getCreationTick();
            float percent =
                    (float)
                            Math.min(
                                    1,
                                    (double) (timeAlive - fadeStart)
                                            / (double) (fadeStop - fadeStart));
            applied =
                    1
                            - (float)
                            ((EasingMethod)
                                    HudConfigStorage.General.FADE_TYPE.config
                                            .getOptionListValue())
                                    .apply(percent);
            applied = Math.max(0, applied);
            if (applied <= 0) {
                return;
            }
            if (applied < 1) {
                // Adjust color for background and text due to fade
                background = ColorUtil.fade(background, applied);
                text = ColorUtil.fade(text, applied);
            }
        }

        // Get line
        Component render = line.getText();
        if (line.getParent().getStacks() > 0 && lineIndex == 0) {
            TextBuilder toPrint = new TextBuilder().append(render);
            Style style = Style.EMPTY;
            TextColor color =
                    TextColor.fromRgb(Colors.getInstance().getColorOrWhite("gray").color());
            style = style.withColor(color);
            toPrint.append(new RawText(" (" + (line.getParent().getStacks() + 1) + ")", style));
            render = toPrint.build();
        }

        int backgroundWidth;
        int scaledWidth = getScaledWidth();
        // 26.2: TextRenderer.getWidth(Text) -> Font.width(Component).
        int lineWidth = client.font.width(render) + 2;

        if (!focused
                && HudConfigStorage.General.HUD_LINE_TYPE.config.getOptionListValue()
                == HudConfigStorage.HudLineType.COMPACT) {
            backgroundWidth = lineWidth + headOffset();
        } else {
            backgroundWidth = scaledWidth;
        }

//         Draw background
        int backgroundY = getActualY(y);
        if (renderTopFirst && renderedLines == 0) {
            backgroundY -= 1 + HudConfigStorage.General.TOP_PAD.config.getIntegerValue();
        }
        if (renderRight) {
            drawRect(drawContext, x + (scaledWidth - backgroundWidth), backgroundY, backgroundWidth, height, background.color());
        } else {
            drawRect(drawContext, x, backgroundY, backgroundWidth, height, background.color());
        }
        if (lineIndex == line.getParent().getLineCount() - 1
                && line.getParent().getOwner() != null
                && HudConfigStorage.General.CHAT_HEADS.config.getBooleanValue()) {
            Identifier texture = line.getParent().getOwner().getTexture();

            int headX;
            if (renderRight) {
                headX = pRX + 2;
            } else {
                headX = pLX - 10;
            }
            int headY = getActualY(y);
            // 26.2: DrawContext.drawTexture(pipeline, id, x, y, u=8, v=8, w=8, h=8, texW=64, texH=64)
            // sampled the player-face overlay region of a 64x64 skin. blit corner+normalized-UV form:
            // u 8/64..16/64, v 8/64..16/64.
            drawContext.blit(texture, headX, headY, headX + 8, headY + 8,
                    0.125f, 0.25f, 0.125f, 0.25f);
        }

        // 26.2: DrawContext.drawTextWithShadow(font, OrderedText, x, y, color) ->
        // GuiContext.drawString(Font, FormattedCharSequence, x, y, color, shadow=true);
        // Text.asOrderedText() -> Component.getVisualOrderText().
        drawContext.drawString(Minecraft.getInstance().font,
                render.getVisualOrderText(),
                renderRight ? pRX - lineWidth : pLX,
                getActualY(y) + 1,
                text.color(),
                true);
    }

    /**
     * 26.2 replacement for {@code TextRenderer.getTextHandler().getStyleAt(Text, int)}, which no longer
     * exists. The engine now resolves the style under a point through the {@link ActiveTextCollector}
     * model. {@link ActiveTextCollector.ClickableStyleFinder} is constructed with the cursor position
     * and fed the rendered line; it returns the {@link Style} whose rendered span covers that pixel.
     * We feed the single line at origin (0,0) and place the cursor at {@code (x, lineHeight/2)} so the
     * finder's vertical hit-test (top..top+lineHeight) matches.
     */
    private Style styleAtWidth(Component line, int x) {
        ActiveTextCollector.ClickableStyleFinder finder =
                new ActiveTextCollector.ClickableStyleFinder(client.font, x, client.font.lineHeight / 2);
        finder.accept(0, 0, line);
        return finder.result();
    }

    public Style getText(double mouseX, double mouseY) {
        if (!WindowManager.getInstance().isChatFocused()) {
            return null;
        }
        double relX = mouseX;
        double relY = getConvertedY() - mouseY;
        double trueX = relX / getScale() - getPaddedLeftX();
        double trueY = relY / getScale();
        // Divide it by chat scale to get where it actually is
        if (trueX < 0.0D || trueY < 0.0D) {
            return null;
        }
        if (trueY > getScaledHeight() || trueX > getScaledWidth()) {
            return null;
        }

        int lineHeight = 0;
        boolean foundScroll = false;
        LimitedInteger y =
                new LimitedInteger(
                        getScaledHeight() + HudConfigStorage.General.LINE_SPACE.config.getIntegerValue(),
                        HudConfigStorage.General.BOTTOM_PAD.config.getIntegerValue());
        for (ChatMessage message : this.lines) {
            // To get the proper index of reversed
            for (int i = message.getLineCount() - 1; i >= 0; i--) {
                lineHeight += HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                if (lineHeight < scrolledHeight) {
                    continue;
                }
                if (!foundScroll) {
                    foundScroll = true;
                    trueY -= lineHeight - (int) scrolledHeight - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                }
                if (!y.incrementIfPossible(HudConfigStorage.General.LINE_SPACE.config.getIntegerValue())) {
                    break;
                }

                if (trueY <= y.getValue()
                        && trueY
                        >= y.getValue()
                        - HudConfigStorage.General.LINE_SPACE.config
                        .getIntegerValue()) {
                    ChatMessage.AdvancedChatLine line = message.getLines().get(i);
                    double truestX = trueX;
                    if (renderRight) {
                        truestX = trueX - (getScaledWidth() - line.getWidth()) + headOffset() + HudConfigStorage.General.RIGHT_PAD.config.getIntegerValue() + HudConfigStorage.General.LEFT_PAD.config.getIntegerValue();
                    }
                    return styleAtWidth(line.getText(), (int) truestX);
                }
            }
            if (lineHeight >= scrolledHeight) {
                if (lineHeight == getTotalHeight()) {
                    break;
                }
                if (!y.isPossible(
                        HudConfigStorage.General.LINE_SPACE.config.getIntegerValue()
                                + HudConfigStorage.General.MESSAGE_SPACE.config
                                .getIntegerValue())
                        || !y.incrementIfPossible(
                        HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue())) {
                    break;
                }
            }
            lineHeight += HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
        }
        return null;
    }

    public ChatMessage getMessage(double mouseX, double mouseY) {
        if (!WindowManager.getInstance().isChatFocused()) {
            return null;
        }
        double relX = mouseX;
        double relY = getConvertedY() - mouseY;
        if (renderTopFirst) {
            relY = getConvertedHeight() - relY;
        }
        double trueX = relX / getScale() - getPaddedLeftX();
        double trueY = relY / getScale();
        // Divide it by chat scale to get where it actually is
        if (trueX < 0.0D || trueY < 0.0D) {
            return null;
        }
        if (trueY > getScaledHeight() || trueX > getScaledWidth()) {
            return null;
        }

        int lineHeight = 0;
        boolean foundScroll = false;
        LimitedInteger y = new LimitedInteger(getScaledHeight() + HudConfigStorage.General.LINE_SPACE.config.getIntegerValue(), HudConfigStorage.General.BOTTOM_PAD.config.getIntegerValue());
        for (ChatMessage message : this.lines) {
            // To get the proper index of reversed
            for (int i = message.getLineCount() - 1; i >= 0; i--) {
                lineHeight += HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                if (lineHeight < scrolledHeight) {
                    continue;
                }
                if (!foundScroll) {
                    foundScroll = true;
                    trueY -= lineHeight - (int) scrolledHeight - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue();
                }
                if (!y.incrementIfPossible(HudConfigStorage.General.LINE_SPACE.config.getIntegerValue())) {
                    break;
                }
                if (trueY <= y.getValue() && trueY >= y.getValue() - HudConfigStorage.General.LINE_SPACE.config.getIntegerValue()) {
                    return message;
                }
            }
            if (lineHeight >= scrolledHeight) {
                if (lineHeight == getTotalHeight()) {
                    break;
                }
                if (!y.isPossible(
                        HudConfigStorage.General.LINE_SPACE.config.getIntegerValue()
                                + HudConfigStorage.General.MESSAGE_SPACE.config
                                .getIntegerValue())
                        || !y.incrementIfPossible(
                        HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue())) {
                    break;
                }
            }
            lineHeight += HudConfigStorage.General.MESSAGE_SPACE.config.getIntegerValue();
        }
        return null;
    }

    public boolean isMouseOverDragBar(double mouseX, double mouseY) {
        int x = getConvertedX();
        int y = getConvertedY();
        int width = getConvertedWidth();
        int height = getConvertedHeight();
        return (isMouseOver(mouseX, mouseY)
                && mouseX <= x + width - (getScaledBarHeight() * 3)
                && mouseY <= y - height);
    }

    public boolean onMouseClicked(double mouseX, double mouseY, int button) {
        int convX = getConvertedX();
        int width = getConvertedWidth();
        boolean onButtons =
                isMouseOverDragBar(mouseX - (getScaledBarHeight() * 3), mouseY)
                        && mouseX >= convX + width - getScaledBarHeight() * 3;
        if (!onButtons) {
            return false;
        }
        int x = width - (int) (mouseX - convX);
        // Visibility | Resize | Close
        if (x <= getScaledBarHeight()) {
            // Close
            WindowManager.getInstance().deleteWindow(this);
        } else if (x >= getScaledBarHeight() * 2) {
            // Visibility
            visibility = visibility.cycle(true);
        }
        // 26.2: PositionedSoundInstance.master(...) -> SimpleSoundInstance.forUI(...);
        // SoundEvents.UI_BUTTON_CLICK is now a Holder<SoundEvent>, which forUI accepts.
        this.client
                .getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1.0F));
        return true;
    }

    public boolean isMouseOverResize(double mouseX, double mouseY) {
        int x = getConvertedX();
        int y = getConvertedY();
        int width = getConvertedWidth();
        int height = getConvertedHeight();
        return (isMouseOver(mouseX, mouseY)
                && mouseX >= x + width - (getScaledBarHeight() * 2)
                && mouseX <= x + width - (getScaledBarHeight())
                && mouseY <= y - height);
    }

    public boolean isMouseOverVisibility(double mouseX, double mouseY) {
        int x = getConvertedX();
        int y = getConvertedY();
        int width = getConvertedWidth();
        int height = getConvertedHeight();
        return (isMouseOver(mouseX, mouseY)
                && mouseX >= x + width - (getScaledBarHeight() * 3)
                && mouseX <= x + width - (getScaledBarHeight() * 2)
                && mouseY <= y - height);
    }

    public void setDimensions(int width, int height) {
        this.widthPercent = (double) width / client.getWindow().getGuiScaledWidth();
        this.heightPercent = (double) height / client.getWindow().getGuiScaledHeight();
        for (ChatMessage m : lines) {
            m.formatChildren(getPaddedWidth());
        }
    }

    public void stackMessage(HudChatMessage message) {
        ChatMessage toRemove = null;
        for (ChatMessage line : lines) {
            if (message.getMessage().isSimilar(line)) {
                if (!ConfigStorage.General.CHAT_STACK_UPDATE.config.getBooleanValue()) {
                    // Just update the message and don't resend it
                    line.setStacks(message.getMessage().getStacks());
                    return;
                }
                toRemove = line;
                break;
            }
        }
        if (toRemove != null) {
            // Remove and then readd it with the updated stack information
            lines.remove(toRemove);
            addMessage(message, true, true);
        }
    }

    public void clearLines() {
        this.lines.clear();
    }

    public void removeMessage(ChatMessage remove) {
        ChatMessage toRemove = null;
        for (ChatMessage line : lines) {
            if (remove.isSimilar(line) && remove.getTime().equals(line.getTime())) {
                toRemove = line;
                break;
            }
        }
        if (toRemove != null) {
            lines.remove(toRemove);
        }
    }

    public void toggleMinimalist() {
        minimalist = !minimalist;
    }

    public static class ChatWindowSerializer implements IJsonSave<ChatWindow> {

        @Override
        public ChatWindow load(JsonObject obj) {
            if (!obj.has("tabuuid")) {
                return null;
            }
            String uuidEl = obj.get("tabuuid").getAsString();
            UUID uuid = UUID.fromString(uuidEl);
            AbstractChatTab tab = AdvancedChatHud.MAIN_CHAT_TAB.fromUUID(uuid);
            if (tab == null) {
                AdvancedChatHud.LOGGER.warn("Tab with UUID " + uuidEl + " could not be found!");
                return null;
            }
            ChatWindow window = new ChatWindow(tab);
            window.setSelected(obj.get("selected").getAsBoolean());
            if (obj.has("renderRight")) {
                window.setRenderRight(obj.get("renderRight").getAsBoolean());
            }
            if (obj.has("renderTopFirst")) {
                window.setRenderTopFirst(obj.get("renderTopFirst").getAsBoolean());
            }
            if (obj.has("minimalist")) {
                window.setMinimalist(obj.get("minimalist").getAsBoolean());
            }
            window.setRelativePosition(obj.get("x").getAsDouble(), obj.get("y").getAsDouble());
            window.setVisibility(
                    HudConfigStorage.Visibility.fromVisibilityString(
                            obj.get("visibility").getAsString()));
            window.setRelativeDimensions(
                    obj.get("width").getAsDouble(), obj.get("height").getAsDouble());
            return window;
        }

        @Override
        public JsonObject save(ChatWindow chatWindow) {
            JsonObject obj = new JsonObject();
            obj.addProperty("x", chatWindow.getXPercent());
            obj.addProperty("y", chatWindow.getYPercent());
            obj.addProperty("width", chatWindow.getWidthPercent());
            obj.addProperty("height", chatWindow.getHeightPercent());
            obj.addProperty("visibility", chatWindow.getVisibility().getStringValue());
            obj.addProperty("tabuuid", chatWindow.getTab().getUuid().toString());
            obj.addProperty("selected", chatWindow.isSelected());
            obj.addProperty("renderRight", chatWindow.isRenderRight());
            obj.addProperty("renderTopFirst", chatWindow.isRenderTopFirst());
            obj.addProperty("minimalist", chatWindow.isMinimalist());
            return obj;
        }
    }

    public void onResolutionChange() {
        for (ChatMessage m : lines) {
            m.formatChildren(getConvertedWidth());
        }
    }
}
