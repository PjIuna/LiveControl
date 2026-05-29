# LiveControl

Control your Minecraft player directly from livestream chat while AFK.

LiveControl allows creators to connect their **YouTube**, **Twitch**, or **Kick** livestream chat directly to Minecraft, letting viewers interact with and control the player while the streamer is away.

---

## ✨ Features

* YouTube Live chat support
* Twitch chat support
* Kick chat support
* AFK mode system
* Chat-controlled movement & actions
* Baritone-assisted mining and farming
* Designed for livestream interaction & chaos

---

## 📺 How It Works

When you're going AFK, type:

```mcfunction
/afk
```

This enables livestream chat control.

While AFK, viewers can control your player directly through livestream chat using the commands below.

### 💬 Viewer Chat Commands

| Chat Command | Action                                                |
| ------------ | ----------------------------------------------------- |
| `Mine`       | Mines ores                                            |
| `Wood`       | Collects wood                                         |
| `Jump`       | Makes the player jump                                 |
| `Inventory`  | Opens inventory                                       |
| `Explore`    | Explores the area                                     |
| `Loot`       | Searches for loot                                     |
| `Sleep`      | Go to the nearest bed and sleep                       |
| `Farm`       | Starts farming                                        |
| `Nether`     | Travel in and out of the Nether                       |
| `Gold`       | **Nether-only command** that mines gold in the Nether |
| `Home`       | Returns home                                          |
| `Close`      | Stops current task and returns nearby                 |
| `Stop`       | Completely stops all actions                          |
- 💡 You can use the #sethome command to set homes for the Home chat command.


When you return, type:

```mcfunction
/back
```

This disables livestream chat control and gives control back to you.

---

## ⚠️ Baritone Required

> **Baritone is REQUIRED for this mod to function properly.**

LiveControl depends on Baritone for pathfinding, mining, and automated movement systems used while chat is controlling the player.

Without Baritone installed, the mod will not work correctly.

---

## ⚠️ Important Warning

This mod is **NOT** intended for cheating or abuse.

Do **NOT** use this on large or public multiplayer servers.

This mod was created for:

* Singleplayer
* Private SMPs
* Friend servers
* Viewer interaction streams
* AFK farming entertainment

Using this mod on public servers may result in:

* Bans
* Kicks
* Punishments from server staff

The Auto Mine functionality only exists to help livestream chat interact with gameplay while the player is AFK.

---

## ❗ Disclaimer

Everything you do with this mod is **AT YOUR OWN RISK.**

The developers of LiveControl are **not responsible** for:

* Server bans
* Punishments
* Misuse of the mod
* Damage caused by improper usage

Use responsibly.

---

## 🛠 Installation

1. Install Fabric Loader
2. Install Fabric API
3. Install Baritone from the linked GitHub
4. Download LiveControl through GitHub or Modrinth
5. Launch Minecraft

---

## 💬 Mod Commands

| Command | Description                      |
| ------- | -------------------------------- |
| `/afk`  | Enables livestream chat control  |
| `/back` | Disables livestream chat control |

---

## 🌎 Supported Platforms

* YouTube Live
* Twitch
* Kick
