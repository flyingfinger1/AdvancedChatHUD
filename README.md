# AdvancedChatHUD

AdvancedChatHUD is an overhaul of Minecraft chat HUD adding many new features.

> **Refurbished fork.** DarkKronicle archived the original project. This fork brings AdvancedChatHUD
> up to **Minecraft 26.2** and modernises the codebase: the whole mod (and its two mixins) was ported
> from Yarn to the new Mojang names and the 26.x GUI render-state model. It is a module of the
> refurbished [AdvancedChatCore](https://github.com/flyingfinger1/AdvancedChatCore).

## Requirements

| | Version |
| --- | --- |
| Minecraft | **26.2** |
| Java | **25** (required by Minecraft 26.x) |
| Fabric Loader | 0.19.0+ |

## Dependencies

The following are **required** for this mod to run:

- [AdvancedChatCore](https://github.com/flyingfinger1/AdvancedChatCore) **1.6.1+** (this fork's build)
- [MaLiLib](https://modrinth.com/mod/malilib) — for 26.x use the sakura-ryoko builds
- [Fabric API](https://modrinth.com/mod/fabric-api)

[Mod Menu](https://modrinth.com/mod/modmenu) is recommended to open the configuration screen.

## Features

Many features to completely change the way how to message in Minecraft.

### Redesigned HUD

- Stripe messages
- Stack messages
- Display player heads *on any server*
- Change padding from top, bottom, left, and right
- Change background color
- Change border color
- Spacing between lines
- Spacing between *separate* messages
- Change fade time
- Change fade type
- Compact or full line backgrounds
- Change scale
- Custom visibilities
- Customize stored amount of lines
- Have multiple chat windows
- Easy resize and different visibility for each chat window

### Chat Tabs

- Send messages with specific content to chat tabs
- Customize the main chat tab
- Add as many chat tabs as you want
- Easily add multiple matches per tab
- Change tab name
- Change specific tabs color
- Change background color
- Change border color
- Show amount of unread messages
- Easily export and share tabs with others

## Images

![Multiple Tabs](https://raw.githubusercontent.com/DarkKronicle/AdvancedChatHUD/main/screenshots/all.png)
![Color and Always On](https://raw.githubusercontent.com/DarkKronicle/AdvancedChatHUD/main/screenshots/color_always.png)
![Stripe and Always On](https://raw.githubusercontent.com/DarkKronicle/AdvancedChatHUD/main/screenshots/stripe_always.png)

https://user-images.githubusercontent.com/38167691/139516997-975ba447-6a67-4525-8d6f-2d9c1b58a84d.mp4


## Building

The build needs a **JDK 25** toolchain (Minecraft 26.x). AdvancedChatHUD depends on the refurbished
AdvancedChatCore, which it resolves from your local Maven repository. Publish Core locally first:

```
# in the AdvancedChatCore clone
./gradlew publishToMavenLocal      # publishes io.github.darkkronicle:AdvancedChatCore:1.6.1

# then in AdvancedChatHUD
./gradlew build
```

To run the mod, install it together with AdvancedChatCore, MaLiLib and Fabric API.

## Development

To ensure code consistency the hook `pre-commit.sh` can be used. To install it run:

`ln -s ../../pre-commit.sh .git/hooks/pre-commit`

## Credits n' more

- Code & Mastermind: DarkKronicle
- Language & Proofreading: Chronos22
- 26.2 port & modernisation: community fork
