# LibreGo

> **LibreGo** is a pure, modern, and completely free Android mock location and route simulation tool built on Android system APIs.
> 
> Forked and rewritten from the original `GoGoGo` before it went proprietary, LibreGo is dedicated to true Free and Open Source Software (FOSS), strictly licensed under **GPL-3.0**. **No accounts, no cloud auth, no subscriptions, no ads. 100% offline and forever free.**

---

## 🌟 Key Features (v2.0+)

### 1. Modern Android & Anti-Rubberbanding Optimizations
* **Triple Provider Synchronization**: Registers `gps`, `network`, and **`fused`** (Fused Location Provider) mock providers simultaneously, effectively eliminating the rubberbanding / location-jumping issue on modern Android apps.
* **Strict Monotonic Timestamps**: High-precision monotonic nanosecond timing prevents Android location cache from dropping updates due to jitter.
* **Realistic Physical Signal Simulation**:
  * Dynamic GPS accuracy jitter based on speed and noise.
  * Full Android 8.0+ accuracy metrics: vertical, bearing, and speed accuracy.
  * Satellite count simulation in location extras.
* **Android 13 / 14 / 15 Ready**: Modern foreground service types and notification permissions.

### 2. Movement Controls & Routes
* **Floating Joystick**: Circular joystick and direction buttons with customizable size and speed modes (walk, run, bike).
* **Multi-point Route Simulation**: Cruise along custom paths with single or looping modes.
* **GPX Import / Export**: Easy trajectory sharing.

### 3. Privacy & Freedom
* 🔒 **Local Only**: All data stored locally in SQLite. Zero cloud tracking.
* 🚫 **No Paywalls**: 100% open-source without feature gates.

---

## 📜 License

Licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.
