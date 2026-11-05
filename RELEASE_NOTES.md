# LibreGo v2.0.0

LibreGo 是 100% 開源、自由、無廣告、無會員限制、去商業化的全新模擬定位應用。

### 🌟 核心變更與現代化重構
- **架構純淨**：全面移除原版 VirtGO 的分身生成、付費會員、帳號登入、人機驗證與所有雲端閉源後門。
- **現代技術棧**：全核心採用 **Kotlin 2.0**、**Java 21 / JVM 21** 重構，適配 **Android SDK 37 (Android 15+)**。
- **Shizuku 免 Root 特權支援**：
  - 整合 Shizuku 特權服務，一秒免手動進入「開發者選項」自動授權 `OP_MOCK_LOCATION`。
  - 一鍵自動授權懸浮窗（`SYSTEM_ALERT_WINDOW`）與系統通知。
  - 自動加入電池不最佳化白名單（`dumpsys deviceidle whitelist`），徹底告別 OEM 殺後台。
- **反模擬偵測防禦 (Anti-Mock Route A)**：
  - 注入真實物理微擾動（電離層高斯噪聲、微小隨機經緯飄移）。
  - 單調納秒時鐘（Monotonic Nanosecond Clock）計算，防止時序跳變偵測。
  - 動態模擬高精衛星 C/N0 訊號與動態 Accuracy 水平。
- **解耦懸浮搖桿**：
  - 支援純淨單點固定模擬，徹底解耦懸浮窗與搖桿。
- **全新 Shizuku 風格控制卡片**：
  - Material 3 圓角狀態面板，即時呈現模擬狀態、坐標與 Shizuku 連線徽章。
- **極致體積瘦身**：
  - 原生動態庫 Deflate 壓縮 + 剔除冗餘室內商場地圖資產 + R8 進階優化，APK 由 28.4MB 狂降至 **12.6MB**（-57.7%）。

### 📥 下載
- **安裝包**：`LibreGo_2.0.0_arm64-v8a_release.apk`
- **架構**：arm64-v8a
- **大小**：12.0 MB
