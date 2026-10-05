/*
 * Copyright (C) 2022 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import fi.dy.masa.malilib.render.GuiContext;
import io.github.darkkronicle.advancedchatcore.chat.AdvancedChatScreen;
import io.github.darkkronicle.advancedchathud.config.HudConfigStorage;
import io.github.darkkronicle.advancedchathud.gui.WindowManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 26.2+: MaLiLib draws its registered in-game GUI renderers at the TAIL of
 * {@code Gui.extractRenderState}, i.e. AFTER the current screen's own render state has been extracted.
 * Because the command-suggestion popup is part of the screen, that made the AdvancedChat chat windows
 * draw ON TOP of the suggestions whenever a window overlapped them — the suggestions became unreadable
 * (in 26.1's immediate-mode renderer the HUD overlay was drawn before the screen, so this never
 * happened).
 *
 * <p>To restore the pre-26.2 layering, render the chat windows here — right before the screen extracts
 * its render state — so they behave like the vanilla HUD: on the AdvancedChat screen (which does not
 * blur) they sit under the chat so the command suggestions stay on top; under screens that blur/darken
 * their background (pause menu, options, config) they are blurred behind the menu, as in the old
 * versions — instead of being drawn sharp on top at the MaLiLib tail. Over non-AdvancedChat screens
 * the windows are only drawn when {@code RENDER_IN_OTHER_GUI} ("always on top") is enabled; each
 * window's own visibility (always / fade / focus-only) still applies in {@code render()}.
 * {@link WindowManager#onExtractGuiOverlayPost} then only handles the plain in-game HUD (no screen).
 */
@Mixin(Gui.class)
public class MixinGuiExtractRenderState {

    @Inject(
            method = "extractRenderState(Lnet/minecraft/client/DeltaTracker;ZZ)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/gui/screens/Screen;extractRenderStateWithTooltipAndSubtitles(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIF)V"))
    private void advancedchathud$renderWindowsBeforeScreen(CallbackInfo ci, @Local GuiGraphicsExtractor extractor) {
        Minecraft client = Minecraft.getInstance();
        if (client.level == null || client.player == null) {
            return;
        }
        Screen screen = client.gui.screen();
        if (screen == null) {
            return;
        }
        // AdvancedSleepingChatScreen extends AdvancedChatScreen, so this covers the sleeping chat too.
        boolean advancedChatScreen = screen instanceof AdvancedChatScreen;
        if (!advancedChatScreen && !HudConfigStorage.General.RENDER_IN_OTHER_GUI.config.getBooleanValue()) {
            return;
        }
        WindowManager.getInstance().renderWindows(GuiContext.fromGuiGraphics(extractor), advancedChatScreen);
    }
}
