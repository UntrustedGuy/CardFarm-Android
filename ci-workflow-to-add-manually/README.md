# CI workflow (add manually)

GitHub blocked pushing `.github/workflows/build.yml` because the personal
access token used lacks the `workflow` scope.

To enable automatic APK builds, either:

1. Copy `build.yml` from this folder to `.github/workflows/build.yml` using
   the GitHub web UI (Add file -> Create new file), **or**
2. Regenerate the token with the `workflow` scope added and re-push.

Once present, every push to `main` builds a debug APK and uploads it under
Actions -> latest run -> `cardfarm-debug-apk`.
