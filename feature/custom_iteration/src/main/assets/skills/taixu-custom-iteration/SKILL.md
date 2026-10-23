---
name: taixu-custom-iteration
description: TaiXu custom iteration and TaiXuDev build guidelines, guiding agents in safe development, submitting PRs, and building dual-flavor APKs.
---

# TaiXu Custom Iteration Development Guidelines

This skill guides AI Agents in the TaiXu rootless Linux sandbox to assist users in self-iterating development, testing, cloud CI builds, and open-source community PR delivery for the TaiXu app itself.

---

## 1. Core Security & Isolation Principles

1. **Workspace Isolation**: All modifications to TaiXu source code must strictly take place within the isolated workspace `~/custom_taixu`. Direct modifications in host directories or system configurations are forbidden.
2. **Credential Security**:
   - Guide users to authenticate with `gh auth login` (device code flow) or SSH Key;
   - Never print GitHub tokens, private keys, or sensitive credentials in terminal output, session messages, or commits.
3. **Dual-Flavor Coexistence**:
   - APKs built from custom iterations must be named `TaiXuDev`;
   - The package name must be renamed to `top.wkbin.taixu.dev`;
   - Default storage and configuration directories are isolated from the release version, ensuring the test version coexists cleanly with the installed production TaiXu app without conflicts.

---

## 2. Iteration Workflow

1. **Environment Diagnostics**:
   - Check `git` and `gh` installation status;
   - Confirm current working directory is `~/custom_taixu`. The directory must be empty prior to `git clone`;
   - Skill auto-discovery paths are `/attachments/skills` and `/workspace/skills`. `~/custom_taixu/.agents/skills` is not a scan root; only copy from `/attachments/skills/taixu-custom-iteration` after clone when preparing to commit a project-level skill into the Fork;
   - Check current Git branch status and ensure a clean working tree.
2. **Architecture Constraints**:
   - **UI Layer**: Strictly use Jetpack Compose + Material3, following `top.wkbin.taixu.ui` naming conventions;
   - **State Management**: Use Koin to inject ViewModels, exposing state via `StateFlow` + `collectAsStateWithLifecycle`;
   - **Linux Sandbox**: PRoot system calls and interactions follow the standard `runtime` module contract.
3. **Local Smoke Testing**:
   - Run `./gradlew testDebugUnitTest` for basic unit tests before commit;
   - Create a feature branch `feature/xxx` to commit after verification passes.

---

## 3. Building TaiXuDev APK (GitHub Actions Preferred)

Due to differences between mobile ARM64 architecture and standard Android build toolchains (x86_64), cloud CI build via GitHub Actions is preferred:

1. Ensure the user's Fork repository contains `.github/workflows/taixudev-build.yml`. The template is cached at `/attachments/templates/workflows/taixudev-build.yml` (outside clone destination). The official main branch includes this file; if missing in Fork, copy to `~/custom_taixu/.github/workflows/` after clone and commit;
2. Trigger the workflow in the cloned Fork directory and monitor in real time. Do not run `workflow_dispatch` against `wkbin/taixu` (contributors get 403). Use the owner/repo parsed from `git remote get-url origin`:
   ```bash
   gh workflow run taixudev-build.yml --ref <feature-branch> -R <fork-owner/name>
   gh run watch <run-id> --exit-status -R <fork-owner/name>
   ```
3. Download generated APK artifact upon build success:
   ```bash
   gh run download <run-id> -n taixudev-apk -D /storage/emulated/0/Download/
   ```
4. Verify package name (`top.wkbin.taixu.dev`) and signing before notifying user to install.

---

## 4. Open-Source PR Contribution Guidelines

After verifying on real device:
- **Target Repository**: `wkbin/taixu:main`
- **Source Branch**: `user-fork:feature/xxx`
- **PR Template Includes**: Summary, Changes, Testing, Screenshots, Risks.
