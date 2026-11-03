# LibreGo

> **LibreGo** is a modern Android mock location and route simulation tool built directly on Android system APIs.
> 
> Maintained and refactored from `GoGoGo`, LibreGo is dedicated to **GPL-3.0**. The codebase remains completely open and transparent—free of proprietary backdoors, forced registration, or cloud activation paywalls. Unless the project is archived in the future, it will remain actively maintained under open-source terms.

---

## 🌟 Key Features (v2.0+)

### 1. Modern Android & Anti-Rubberbanding Optimizations
* **Triple Provider Synchronization**: Registers `gps`, `network`, and **`fused`** mock providers simultaneously to help mitigate rubberbanding / location hopping on modern Android apps.
* **Strict Monotonic Timestamps**: High-precision monotonic nanosecond timing prevents Android location cache from discarding updates.
* **Realistic Signal Simulation**:
  * Dynamic GPS accuracy with speed jitter.
  * Full Android 8.0+ accuracy metrics: vertical, bearing, and speed accuracy.
  * Satellite count simulation in location extras.
* **Android 13 / 14 / 15 Ready**: Modern foreground service types and notification permissions.

### 2. Movement Controls & Routes
* **Floating Joystick**: Circular joystick and direction buttons with customizable size and speed modes (walk, run, bike).
* **Multi-point Route Simulation**: Cruise along custom paths with single or looping modes.
* **GPX Import / Export**: Easy trajectory sharing.

### 3. Privacy & Security
* 🔒 **Local Only**: All data stored locally in SQLite without remote telemetry.
* 🚫 **No Forced Cloud Gates**: Fully offline operations.

---

## 📜 License

Licensed under the **[GNU General Public License v3.0 (GPL-3.0)](LICENSE)**.
