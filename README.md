# DiamondCraft 1.0 RC4 — Color Fidelity

Основа: проверенный RC3 Project Management.

Новые изменения RC4:
- отдельная настройка цветопередачи: Естественный / Яркий / Насыщенный;
- Яркий выбран по умолчанию для алмазной мозаики;
- усилены насыщенность и локальный контраст без изменения геометрии/кадрирования;
- добавлен vibrance-проход, чтобы цветные области не выглядели как через матовое стекло;
- профили Авто / Портрет / Предмет / Пейзаж сохранены и работают независимо от цветопередачи;
- сохранены Новый проект, импорт/экспорт .diamondcraft, проекты, прогресс, PNG/PDF/CSV и список покупок;
- versionCode 122, versionName 1.0-rc4; устанавливается поверх RC3 тем же dev-ключом.


## RC6 commercial layer
- clean production-style title (RC number is no longer in the app bar);
- Pro entry point and feature explanation;
- billing UI intentionally does not fake a purchase;
- Google Play Billing must be connected after the Pro product ID is created in Play Console;
- supplier SKUs remain disabled until a verified catalog is connected.


## RC7 branding
- adaptive Android launcher icon with faceted diamond mark;
- round launcher icon and legacy fallback;
- consistent DiamondCraft violet Material 3 palette;
- status/navigation/window background aligned with the brand;
- app label and launcher resources prepared for release packaging.


## RC9 Free / Pro
- commercial Free/Pro limits are centralized;
- Free target: 100 drills / 60 colors;
- Pro target: 200 drills / 120 colors + export/import features;
- RC9 intentionally unlocks Pro for complete device testing;
- no fake purchase state is stored;
- production entitlement will come from Google Play Billing.
