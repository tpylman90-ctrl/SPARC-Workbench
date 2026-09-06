# Upload to GitHub

Repository target: `sparc-workbench-android`

## Important
Upload the **contents of this folder**, not a parent folder. The repository root should contain:

- `.github/workflows/android.yml`
- `app/`
- `build.gradle`
- `settings.gradle`
- `README.md`

## From GitHub on your phone
1. Open the empty `sparc-workbench-android` repository.
2. Choose **Add file** -> **Upload files**.
3. Upload all files and folders from this package.
4. Commit directly to the default branch.
5. Open the **Actions** tab.
6. The Android build workflow should start automatically after the commit.
7. When it finishes, open the workflow run and download the APK artifact.

If GitHub's mobile uploader does not accept folders cleanly, use the GitHub website in desktop mode or upload from a computer. The `.github` folder is essential because it contains the build workflow.
