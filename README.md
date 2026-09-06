# SPARC Workbench Android v1.0

This is the standalone Android application branch of SPARC Workbench. It removes the Pydroid/local-server dependency entirely.

## User experience

1. Install APK.
2. Open **SPARC Workbench**.
3. Tap **Load SPARC data** on first run.
4. Browse galaxies, inspect rotation curves, use scientific cohorts, and export model-ready files.
5. Exports are written to `Downloads/SPARC_Workbench/`.

The two official SPARC source tables are cached in private app storage after the first load. Clearing the cache from Data Health forces a fresh download.

## Included features

- Official SPARC metadata + mass-model ingestion
- 175-galaxy catalog browser when the official source profile is unchanged
- Search and quality/min-point filtering
- Rotation-curve plot with gas/disk/bulge components
- Curve diagnostics and outer-shape classification
- Named cohorts: quality_1, btfr_ready, bulgeless, extended_rotation_curve, inclination_safe, uig_benchmark_v1
- Built-in export styles: UIG, canonical CSV, minimal CSV, whitespace
- User-defined JSON export style with field rename, scale and offset
- Android-native download caching and export saving
- No Python, Pydroid, local HTTP server, or third-party runtime required on the phone

## Build requirements

- Android Studio / Android SDK 35
- JDK 17+
- Gradle / Android Gradle Plugin 8.7.3

Open the project root in Android Studio and build `app` as an APK for direct testing or an Android App Bundle (AAB) for Google Play.

## Important status

This repository is an Android-native port of the workbench interface and core dataset operations. The original Python v0.9.1 project remains the reference implementation for deeper validation and MCP/server deployment. Before a public scientific release, the Android port should be cross-validated against the Python regression suite on the full SPARC tables.
