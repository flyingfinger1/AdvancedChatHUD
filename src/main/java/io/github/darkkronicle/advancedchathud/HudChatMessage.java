/*
 * Copyright (C) 2021 DarkKronicle
 *
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.github.darkkronicle.advancedchathud;

import io.github.darkkronicle.advancedchatcore.chat.ChatMessage;
import io.github.darkkronicle.advancedchathud.tabs.AbstractChatTab;
import io.github.darkkronicle.advancedchathud.tabs.CustomChatTab;
import lombok.Getter;
import lombok.Setter;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

import java.util.ArrayList;
import java.util.List;

@Environment(EnvType.CLIENT)
public class HudChatMessage {

    @Setter @Getter private List<AbstractChatTab> tabs;

    @Getter private final ChatMessage message;

    public HudChatMessage(ChatMessage message) {
        this(message, new ArrayList<>());

        this.tabs = computeTabs(message.getOriginalText());
        for (AbstractChatTab tab : tabs) {
            tab.addNewUnread();
        }
    }

    public HudChatMessage(ChatMessage message, List<AbstractChatTab> tabs) {
        this.message = message;
        this.tabs = tabs;
    }

    /**
     * Works out which tabs a message belongs to, honouring each matching custom tab's {@code forward}
     * flag: the main chat tab is only included when no matching custom tab is set to not forward. This
     * is the single source of truth for message→tab routing, used both when a message first arrives and
     * when tabs are rebuilt (e.g. after a config reload or a server switch); keeping them in sync stops
     * non-forwarded messages (e.g. private messages in their own window) from leaking back into the main
     * chat on a rebuild.
     */
    public static List<AbstractChatTab> computeTabs(net.minecraft.network.chat.Component text) {
        List<AbstractChatTab> tabs = new ArrayList<>();
        boolean forward = true;
        if (AdvancedChatHud.MAIN_CHAT_TAB.getCustomChatTabs().size() > 0) {
            for (CustomChatTab tab : AdvancedChatHud.MAIN_CHAT_TAB.getCustomChatTabs()) {
                if (!tab.shouldAdd(text)) {
                    continue;
                }
                if (tabs.contains(tab)) {
                    continue;
                }
                tabs.add(tab);
                if (!tab.isForward()) {
                    forward = false;
                    break;
                }
            }
        }
        if (forward) {
            tabs.add(AdvancedChatHud.MAIN_CHAT_TAB);
        }
        return tabs;
    }
}
