Phase 1 runtime payloads

Linux ARM64 rootfs is downloaded online by default and not bundled in the APK.
Android 10+ forbids target apps from executing ELFs in writable private directories,
so the APK only carries a minimal ARM64 PRoot binary, external loader, and dynamic
dependencies; Linux systems are installed during initial boot using the `proot-distro 5.10.0`
OCI pipeline.

The installer selects `linux/arm64` manifests from Docker/OCI registries, caches digest-verified
layers to app private storage, and merges them using OCI whiteout rules before atomically enabling RootFS.
This Kotlin-native installer aligns with upstream v5.10.0 without bundling the Python proot-distro CLI.
Supports Docker Hub names and `name[:tag]@sha256:<64-char-digest>`; pinned digests take precedence over tags.
Manifests and compressed layers verify raw bytes; fully cached layers allow offline reinstallation,
and pinned images never fall back to LXC.
Downloads strictly forbid HTTPS-to-HTTP downgrade redirects and time out after 2 minutes of inactivity.
TaiXu does not implement upstream build/push/sync/copy/login commands, nor does it generate mock `/proc` sysdata.
The PRoot binary and loader must originate from the same official Termux package; run
`tools/prepare-proot-runtime.ps1` before build to verify package SHA-256 and prepare both.
