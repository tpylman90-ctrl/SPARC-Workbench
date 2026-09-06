# Build SPARC Workbench as an APK/AAB

## Android Studio

1. Open this folder in Android Studio.
2. Allow Gradle sync to complete.
3. For a test APK: **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
4. For Google Play: **Build > Generate Signed Bundle / APK > Android App Bundle**.
5. Create and securely retain your release keystore. Google Play uses the AAB for distribution.

The package/application id is `org.sparcworkbench.app` and the current version is `1.0.0` / versionCode `10`.

## No Pydroid

The installed APK contains its own WebView interface and Java Android bridge. Source downloads use Android networking and exports use Android MediaStore. There is no local server and no Python dependency.
