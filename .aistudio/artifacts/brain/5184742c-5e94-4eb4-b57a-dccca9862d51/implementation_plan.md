# AI Auto Clipper Studio - OpenRouter & Multi-Preset Upgrade

Upgrades the Auto Video Clipper application with multi-provider AI vision analysis (OpenRouter / 9Router / Gemini), an in-app settings management screen with persistent storage, tailored content presets (Gaming, Podcast, Vlog), AI-generated viral titles and hooks, and adaptive aspect ratio export (9:16 Shorts/Reels & 16:9 original).

### User Review & Critical Decisions

> [!IMPORTANT]
> The following decisions were confirmed based on your requirements and will be integrated into the architecture:

- **Confirmed Provider & Key**: Pre-configured with your OpenRouter / 9Router API key (`sk-f3a0eacae7b8c282-rm4sr9-7c00c4e6`), targeting vision models (e.g. `google/gemini-2.0-flash-001` or `openai/gpt-4o-mini`) via standard OpenAI-compatible Chat Completions format with base64 image frames.
- **Confirmed Key Management**: In-app Settings screen with persistent `SharedPreferences`, enabling seamless key, endpoint URL, and model switching without recompiling the app.
- **Confirmed Enhancements**:
  1. Content preset templates (Gaming, Podcast, Vlog, General Viral).
  2. Viral clip metadata (AI-generated title, 3-second hook quote, category tag, score, rationale).
  3. Aspect ratio export selector (Vertical 9:16 for Reels/TikTok/Shorts vs. Original).
  4. Smarter multi-frame visual highlight detection with frame timestamp mapping.

---

### 1. Overview & Core Concept

- **What It Does**: Allows creators to pick any local video, choose a content style preset (Gaming, Podcast, Vlog, or General), and run AI vision analysis over extracted video frames. The AI ranks the most engaging segments, suggests viral titles and hooks, previews the clips in a built-in player, and exports individual or all clips directly to the Android Gallery in 9:16 vertical or original format.
- **Target Audience / Persona**: Content creators, streamers, podcast editors, and social media managers looking to repurpose long-form video into viral short-form clips.
- **Key Value**: Eliminates manual scrubbing through hours of footage by automating highlight detection, titling, and export with full customization of API keys and models.

---

### 2. User Experience & Visual Design

- **Key User Flows**:
  1. **Video Selection**: Tap the hero media picker card to import any video from device storage with instant thumbnail generation and duration display.
  2. **Configuration Bar**: Choose content preset chip (🎮 Gaming, 🎙️ Podcast, 📹 Vlog, ⚡ General), target clip count (1-10), and desired export aspect ratio (Original vs. 9:16 Vertical).
  3. **Settings Access**: Tap the top-bar gear icon to open the Settings modal to view/edit the API Key, Base URL (defaulting to OpenRouter), and selected model.
  4. **Analysis & Live Feedback**: Tap "Analyze & Clip Highlights" to view detailed stage indicators: frame extraction progress, AI vision processing, and result parsing.
  5. **Review & Playback**: Browse highlight cards ranked by viral score, showing category badges, viral titles, opening hook suggestions, and time intervals. Tap any card to preview in an ExoPlayer dialog.
  6. **Export**: Export individual clips or batch-export all highlights to the device Gallery with real-time progress indicators.

- **Visual Identity & Theme**:
  - *Aesthetic Direction*: Cyber-studio dark aesthetic with modern neon accents (deep obsidian surface `#0F1117`, electric cyan primary `#00D2FF`, vibrant purple accent `#9D4EDD`, and card surface `#1A1D26`).
  - *Typography*: Clean sans-serif hierarchy with bold score indicators and prominent clip titles.
  - *Component Styling*: Elevated Material 3 cards with rounded corners (16.dp), subtle border strokes, categorized emoji chips, and smooth ripple interactions.
  - *Interactive Motion*: Fluid state transitions, animated loading spinners, and snackbar/toast completion alerts.

---

### 3. Key Product Decisions & Trade-Offs

- **Decision 1: Universal OpenAI-Compatible Vision API vs. Proprietary SDKs**
  - *Chosen Approach*: Implement an HTTP client targeting the standard OpenAI Chat Completions schema (`/chat/completions` with image `image_url` parts).
  - *Why*: Enables seamless support for OpenRouter, 9Router, DeepSeek, Groq, and direct OpenAI endpoints using the user's API key, while still retaining fallback support for Google Gemini.
  - *Alternatives Considered*: Hardcoding a single SDK would lock out other router providers and prevent custom endpoints.

- **Decision 2: In-App Persistent Settings vs. Build-Time Only**
  - *Chosen Approach*: Store API key, base URL, and selected model in Android `SharedPreferences` with sensible defaults pre-seeded.
  - *Why*: Allows users to swap keys or test different models (e.g., Flash vs. Pro vision models) directly on-device without needing to edit source code.

- **Decision 3: Media3 Transformer for Video Clipping & Cropping**
  - *Chosen Approach*: Use AndroidX Media3 Transformer with `ClippingConfiguration` and `Presentation` effects for trimming and 9:16 aspect ratio formatting.
  - *Why*: Hardware-accelerated on-device encoding that writes directly to `MediaStore.Video`, saving clips directly into the user's Gallery.

---

### 4. Technical Architecture & Data Strategy

```
┌─────────────────────────────────────────────────────────────┐
│                       Jetpack Compose UI                    │
│   ┌───────────────────┐  ┌──────────────────────────────┐   │
│   │  VideoPickerCard  │  │ Preset & Settings Selectors  │   │
│   └─────────┬─────────┘  └──────────────┬───────────────┘   │
│             │                           │                   │
│   ┌─────────▼───────────────────────────▼───────────────┐   │
│   │               AutoClipperApp State                  │   │
│   │   (selectedUri, frames, activePreset, settings)    │   │
│   └─────────┬───────────────────────────┬───────────────┘   │
│             │                           │                   │
│   ┌─────────▼─────────┐       ┌─────────▼───────────────┐   │
│   │  ExoPlayer Dialog │       │  Clip Highlight List    │   │
│   └───────────────────┘       └─────────┬───────────────┘   │
└─────────────────────────────────────────┼───────────────────┘
                                          │
                        ┌─────────────────▼──────────────────┐
                        │       Processing Pipeline          │
                        │ ┌────────────────────────────────┐ │
                        │ │ 1. MediaMetadataRetriever      │ │
                        │ │    (Sampled frames + timestamps│ │
                        │ ├────────────────────────────────┤ │
                        │ │ 2. OpenRouter Vision Client    │ │
                        │ │    (POST /chat/completions)    │ │
                        │ ├────────────────────────────────┤ │
                        │ │ 3. Media3 Transformer Export   │ │
                        │ │    (Trim + Aspect Ratio -> MP4)│ │
                        │ └────────────────────────────────┘ │
                        └─────────────────┬──────────────────┘
                                          │
                                ┌─────────▼─────────┐
                                │ Android MediaStore│
                                │ (Movies/Gallery)  │
                                └───────────────────┘
```

#### Data Model & State

- `ClipPreset`: Enum/Data class defining presets (`GAMING`, `PODCAST`, `VLOG`, `GENERAL`) with specialized prompt instructions.
- `VideoClip`:
  - `startStr`: String (e.g. `"01:24"`)
  - `endStr`: String (e.g. `"01:45"`)
  - `score`: Double (0.0 to 1.0)
  - `title`: String (e.g. `"Epic Clutch Moment in 1v3"`)
  - `hook`: String (e.g. `"You won't believe what happened next!"`)
  - `reason`: String (detailed explanation)
  - `category`: String (e.g. `"🎮 GAMING"`, `"🔥 EPIC"`, `"😂 FUNNY"`)
- `ClipperSettings`:
  - `apiKey`: String (default: `sk-f3a0eacae7b8c282-rm4sr9-7c00c4e6`)
  - `baseUrl`: String (default: `https://openrouter.ai/api/v1`)
  - `modelName`: String (default: `google/gemini-2.0-flash-001`)
  - `targetAspect`: String (`"ORIGINAL"` or `"9:16"`)

#### Implementation Steps
1. Create `SettingsManager` with persistent storage for API key, URL, and model.
2. Build Settings Dialog UI accessible via TopAppBar.
3. Update `MainActivity.kt` with Preset selector bar (Gaming, Podcast, Vlog, General).
4. Implement `analyzeWithOpenRouter` supporting OpenAI-compatible Chat Completions format with multiple base64 image parts and structured JSON parsing.
5. Enhance Highlight Cards with Title, Hook, Score Badge, and category indicator.
6. Enhance `trimAndExportClip` with aspect ratio options (Original vs 9:16) and direct MediaStore export.
7. Verify build compilation via `compile_applet`.
