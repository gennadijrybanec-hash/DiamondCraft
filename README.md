# DiamondCraft 1.0 Beta 1

Commercial-beta foundation for Android diamond-painting pattern creation.

## Included in this build

- photo to drill-grid conversion with adaptive photo palette;
- 30–200 drills wide and 24–120 adaptive colors;
- zoom/pan and completed-drill progress tracking;
- local save/restore of multiple projects;
- square / round drill material estimates;
- physical picture and adhesive-canvas size;
- reserve percentage, per-color quantities and 200-piece bag estimates;
- CSV materials export;
- PDF materials export;
- provider-neutral shopping list model ready for store/API adapters;
- stable development APK signing for in-place updates;
- version displayed from Android BuildConfig to avoid stale hard-coded labels.

## Test update path

This build uses the same `app/diamondcraft-dev.keystore` introduced in v0.6 and raises `versionCode` to 100. Install the new APK directly over v0.6. Android should offer an update without uninstalling the application. Project data stored by v0.6 will remain if the app is updated in place.

## Google Play

The bundled development keystore is only for internal APK testing. Production Google Play signing / Play App Signing must use a separate release key and release build configuration.
