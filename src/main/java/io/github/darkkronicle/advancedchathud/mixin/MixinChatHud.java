/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.mixin;

import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchathud.HudChatMessage;
import io.github.darkkronicle.advancedchathud.HudChatMessageHolder;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.gui.WindowManager;
import io.github.darkkronicle.advancedchathud.itf.IChatHud;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.components.ComponentRenderUtils;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.client.multiplayer.chat.GuiMessageSource;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Iterator;
import java.util.List;

@Mixin(value = ChatComponent.class, priority = 1050)
@Environment(EnvType.CLIENT)
public abstract class MixinChatHud implements IChatHud {

    // 26.2 field renames (verified against javap of ChatComponent):
    //   client                -> minecraft (Minecraft)
    //   messages              -> allMessages    (List<GuiMessage>)
    //   visibleMessages       -> trimmedMessages(List<GuiMessage.Line>)
    //   scrolledLines         -> chatScrollbarPos
    //   hasUnreadNewMessages  -> newMessageSinceScroll
    @Shadow @Final private Minecraft minecraft;
    @Shadow @Final private List<GuiMessage> allMessages;
    @Shadow @Final private List<GuiMessage.Line> trimmedMessages;

    @Shadow private int chatScrollbarPos;
    @Shadow private boolean newMessageSinceScroll;

    private AbstractChatTab tab;

    // 26.2: getWidth()/getHeight()/getScale() are private in ChatComponent. A @Shadow of a private
    // method must be a concrete private method with a stub body (private + abstract is illegal Java);
    // Mixin swaps in the real body at load time.
    @Shadow
    private int getWidth() { throw new AssertionError(); }

    @Shadow
    private double getScale() { throw new AssertionError(); }

    @Shadow
    public abstract boolean isChatFocused();

    // 26.2: Yarn scroll(int) is now scrollChat(int).
    @Shadow
    public abstract void scrollChat(int amount);

    @Shadow
    private int getHeight() { throw new AssertionError(); }

    @Inject(at = @At("HEAD"), method = "scrollChat", cancellable = true)
    private void scroll(int amount, CallbackInfo ci) {
        // Only scroll if nothing is focused
        if (WindowManager.getInstance().getSelected() != null) {
            ci.cancel();
        }
    }

    // 26.2: the imperative render(DrawContext, ...) path is gone; the chat is drawn during render-state
    // extraction via extractRenderState(GuiGraphicsExtractor, Font, int, int, int, DisplayMode, boolean).
    // Cancelling at HEAD suppresses vanilla chat drawing exactly like the old render cancel did.
    @Inject(
            at = @At("HEAD"),
            method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V",
            cancellable = true)
    private void render(GuiGraphicsExtractor guiGraphics, Font font, int currentTick, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean bl, CallbackInfo ci) {
        // Ignore rendering vanilla chat if disabled
        if (!HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()) {
            ci.cancel();
        }
    }

    // NOTE (integration): Yarn's getTextStyleAt(double,double) no longer exists in 26.2. Vanilla now
    // resolves hovered/clicked chat text through ChatComponent.captureClickableText(ActiveTextCollector,
    // ...) and the ActiveTextCollector.ClickableStyleFinder model (see Core's AdvancedChatScreen). The
    // two old @Inject hooks that redirected vanilla's text-style lookup to WindowManager.getText were
    // therefore removed: there is no single method to intercept. AdvancedChat's own windows still resolve
    // clickable/hover text directly via ChatWindow.getText (called from WindowManager.mouseClicked), so
    // window text interaction is preserved; only vanilla-HUD-disabled hover-passthrough is dropped.

    @Override
    public AbstractChatTab getTab() {
        return tab;
    }

    @Override
    public void setTab(AbstractChatTab tab) {
        this.tab = tab;
        this.allMessages.clear();
        this.trimmedMessages.clear();

        List<HudChatMessage> messages = HudChatMessageHolder.getInstance().getMessages();
        for (int i = messages.size() - 1; i >= 0; i--) {
            addMessage(messages.get(i));
        }
    }

    @Override
    public void removeMessage(ChatMessage remove) {
        // Reset messages that exist
        setTab(this.tab);
    }

    @Override
    public void addMessage(HudChatMessage hudMsg) {
        if (tab == null || !hudMsg.getTabs().contains(tab)) {
            return;
        }
        if (HudConfigStorage.General.VANILLA_HUD.config.getBooleanValue()) {
            tab.resetUnread();
        }

        // 26.2: Yarn MathHelper -> Mth, getChatScale() -> getScale().
        int width = Mth.floor((double) this.getWidth() / this.getScale());

        ChatMessage msg = hudMsg.getMessage();

        // 26.2: ChatMessages.breakRenderedChatMessageLines(text, width, font) ->
        // ComponentRenderUtils.wrapComponents(FormattedText, int, Font). client.textRenderer -> minecraft.font.
        List<FormattedCharSequence> list =
                ComponentRenderUtils.wrapComponents(
                        msg.getDisplayText(), width, this.minecraft.font);

        // 26.2: ChatHudLine.Visible(creationTick, orderedText, indicator, endOfEntry) ->
        // GuiMessage.Line(GuiMessage parent, FormattedCharSequence content, boolean endOfEntry).
        // The visible/trimmed line now references a parent GuiMessage instead of carrying tick + tag.
        GuiMessage parent = new GuiMessage(
                msg.getCreationTick(),
                msg.getDisplayText(),
                msg.getSignature(),
                GuiMessageSource.SYSTEM_SERVER,
                msg.getIndicator());

        FormattedCharSequence orderedText;
        for (Iterator<FormattedCharSequence> text = list.iterator();
                text.hasNext();
                this.trimmedMessages.add(0, new GuiMessage.Line(parent, orderedText, !text.hasNext()))) {
            orderedText = text.next();
            if (this.isChatFocused() && this.chatScrollbarPos > 0) {
                this.newMessageSinceScroll = true;
                this.scrollChat(1);
            }
        }

        while (this.trimmedMessages.size()
                > HudConfigStorage.General.STORED_LINES.config.getIntegerValue()) {
            this.trimmedMessages.remove(this.trimmedMessages.size() - 1);
        }

        // 26.2: ChatHudLine(creationTick, text, signature, indicator) ->
        // GuiMessage(addedTime, content, signature, GuiMessageSource, tag).
        this.allMessages.add(0, new GuiMessage(
                msg.getCreationTick(),
                msg.getDisplayText(),
                msg.getSignature(),
                GuiMessageSource.SYSTEM_SERVER,
                msg.getIndicator()));
        while (this.allMessages.size()
                > HudConfigStorage.General.STORED_LINES.config.getIntegerValue()) {
            this.allMessages.remove(this.allMessages.size() - 1);
        }
    }

    // 26.2: Yarn clear(boolean) is now clearMessages(boolean).
    @Shadow
    public abstract void clearMessages(boolean clearHistory);

    // IChatHud.clear(boolean) bridges to vanilla's renamed clearMessages.
    @Override
    public void clear(boolean clearHistory) {
        clearMessages(clearHistory);
    }

    @Override
    public boolean isOver(double mouseX, double mouseY) {
        double minX = 4 - (4 * getScale());
        double maxX = 4 + (getWidth() + 4 * getScale());

        // 26.2: Window.getScaledHeight() -> getGuiScaledHeight().
        mouseY = (minecraft.getWindow().getGuiScaledHeight() - mouseY - 40) / getScale();
        return mouseX >= minX && mouseX < maxX && mouseY >= 0 && mouseY < getHeight();
    }
}
