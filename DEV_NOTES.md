# Developer Notes & Architecture (Pocket-Hollow)

This folder contains the modified Android Java UI, Gradle configuration, and native C/C++ files used to build Pocket-Hollow.

## Key Components
- android/java/LauncherActivity.java: Responsive multi-aspect-ratio Android launcher (20:9, 16:9, 4:3, portrait, display cutout safe insets), custom Vulkan driver (.zip) installer, disc importer, and background mod extractor.
- android/java/FoxhollowActivity.java: SDLActivity subclass passing launcher intent extras (FOXHOLLOW_USER_DIR, FOXHOLLOW_HD_TEXTURES, FOXHOLLOW_REFLECTIONS, FOXHOLLOW_HOLD_A, CUSTOM_VK_ICD, etc.) into native environment variables.
- android/AndroidManifest.xml & android/gradle/build.gradle: Package ID (dev.foxhollow.pocket), MANAGE_EXTERNAL_STORAGE permission, and uncompressed asset bundling (noCompress 'db', 'zip').
- native/: Modified C/C++ files for context-gated Hold-A auto-mash, audio stability, and stage fixes.

## Bundled Assets (Excluded from Git due to 100 MB file limit)
Place these in platforms/android/app/src/main/assets/ before building:
1. bundled_hd_textures.zip (Uncompressed zip of com.marcelus.hd-textures)
2. bundled_reflections.zip (Uncompressed zip of dev.foxhollow.reflections with lib/linux-arm64/mod.so)
3. initial_pipeline_cache.db (Pre-compiled Aurora/Dawn shader database)