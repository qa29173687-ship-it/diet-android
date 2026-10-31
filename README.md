# 飲食帳本（DietBook）

台灣在地飲食紀錄 App（Kotlin／Traditional Chinese）。離線內建衛生福利部食品藥物管理署「食品營養成分資料庫」熱量／蛋白質，可記錄每日攝取、自訂超商食品（相機＋離線中文 OCR 讀營養標示）、串 Health Connect，並維護「不週清」的累計熱量帳戶。

## 功能摘要

1. **離線 TFND**：bundled `tfnd.csv`（kcal／蛋白／100g），模糊搜尋名稱與俗名；歸因 OGDL-Taiwan-1.0。
2. **每日紀錄**：以公克換算；顯示今日熱量／蛋白質與目標進度。
3. **自訂食物＋營養標示 OCR**：CameraX + ML Kit 中文辨識；處理每份／每100g、本包裝含 N 份；確認後存成可重用食物。
4. **首次設定**：直接輸入 BMR，或以 Mifflin–St Jeor（身高／體重／年齡／性別）估算；活動量作後備 TDEE；皆可於設定修改。
5. **Health Connect**：讀取當日活動熱量、步數、體重。淨值＝攝取 − BMR − 活動消耗；否則＝攝取 − TDEE。
6. **累計熱量帳戶**：每日淨值持續疊加、不週清；顯示體脂等價（7700 kcal ≈ 1 kg）與盈餘還款計畫；可選提醒。
7. **Edge-to-edge**：避開狀態列與底部導航列。

## 建置

```bash
export JAVA_HOME=/home/box/jdk17
export ANDROID_HOME=/home/box/android-sdk
./gradlew assembleRelease
# APK: app/build/outputs/apk/release/app-release.apk
```

金鑰位於 `keystore/`（未納入版控）。

## 授權與資料

食品營養成分資料來源：衛生福利部食品藥物管理署／政府資料開放授權條款－第1版（OGDL-Taiwan-1.0）。

熱量帳戶與體脂換算僅供個人覺察估算，非醫療建議。
