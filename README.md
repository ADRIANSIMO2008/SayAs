## 🗣️ SayAs

**SayAs** is a lightweight Fabric mod that allows players (with permission) to send chat messages as another player, use custom aliases, or fully mimic other players in real-time.
Perfect for administrators, moderation tools, role-play servers, and custom events.

---

## ✨ Features

Adds multiple commands:

### Basic usage

```
/sayas as <player> <message>
```

Sends a message as if written by the selected player

### Alias system

```
/sayas name <name>
/sayas name show
/sayas name clear
```

Set, view, or clear your custom display name (stored per-player)

### Mimic mode

```
/sayas mimic <on|off|status>
```

When enabled, your normal chat messages are sent as your configured name
Attempts real player mimic if the target is online

### JSON messages

```
/sayas json <player> <json>
```

Send advanced chat messages using raw JSON (tellraw format)

---

## 🔐 Permissions

* `sayas.use` – Basic usage
* `sayas.mimic` – Enable mimic mode
* `sayas.json` – Use JSON messages

✔ Fully compatible with **LuckPerms** (Should be, luckperms for 26.1 isnt out yet)
✔ Falls back to OP system if LuckPerms is not installed

---

## 📂 Logging

All actions are automatically logged:

```
sayas/log/sayas.log
```

### Example:

```
[2025-11-21T13:10:59.505297300] PlayerA -> PlayerB: Hello
JSON [2025-11-21T13:11:10] PlayerA -> PlayerB: {"text":"Hi"}
```

---

## ⚙️ Features Overview

* Persistent alias system (UUID-based)
* Real-time chat interception (mimic mode)
* Fallback system if mimic fails
* JSON chat support for advanced formatting
* Lightweight and thread-safe implementation
* No noticeable performance impact

---

## 🧩 Requirements

* Fabric Loader 0.18.1+
* Fabric API
* Minecraft 1.21.10+

---

## 🔧 Target Use Cases

* Server administration
* Role-play events
* Moderation tools
* Chat testing and scripting
