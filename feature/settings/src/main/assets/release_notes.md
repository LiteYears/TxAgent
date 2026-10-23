# TaiXu v0.17.0 Release Notes

> **Current Version**: v0.17.0  
> **Runtime Environment**: Android 10+ · arm64-v8a (PRoot Linux Sandbox + AI Pair Programming)

---

### ✨ What's New

- **On-Device Offline Translation**: Deep integration with Google ML Kit offline translation, supporting automatic language identification and offline model download progress display, enabling accurate bidirectional translation without API keys.
- **Material You Dynamic Color**: Full support for Android system wallpaper dynamic color extraction and real-time refresh, preserving both "Liquid Glass" frosted glass and "Material You" native themes with seamless switching.
- **Agent Harness Architecture Evolution**:
  - Adopted RikkaHub / Pi best practices, providing fault-tolerant editing, Prompt Caching, direct vision pass-through, and clean retry mechanisms.
  - Added `.agents/skills` discovery mechanism, stop-retained content, and Prompt Cache toggle with custom TTL.
  - Implemented Split-Turn role bridging and deterministic summary file footprint, completing optional engineering capabilities.
  - Eliminated 512MB out-of-memory (OOM) errors caused by snapshot escaping and full-branch loading.
  - Strictly normalized `content` field output and intelligently filtered UI-only skill recommendations.
  - Simplified `history_read` tool parameter schema and aligned precisely with history retrieval.

### 🐛 Bug Fixes

- **Terminal & Networking**: Switched mDNS to on-demand discovery, completely preventing frequent `NSD_PICKER` exception dialog popups on Android 17+.
- **Chat Experience**: Message bubbles now support long-press text selection and one-tap copy; improved model usage progress bar corner radius and padding alignment.
- **UI Layout Stability**: Resolved multiple list item height overflow and text wrapping clipping issues, ensuring consistent layout across compact screens and foldables.
- **Model Configuration**: Removed thinking language constraints, letting models adapt autonomously based on instruction context.

### ⚡ Build & Engineering

- Debug builds automatically reuse Release signing parameters when a keystore is configured, preventing signing mismatches.
- Architecture layering: Decoupled Markdown rendering engine to common components, enabling full rich text preview in update dialogs and about logs.
