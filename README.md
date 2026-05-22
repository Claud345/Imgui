# Android ImGui Floating Overlay Template

## Overview

This project is a reusable Android Dear ImGui floating overlay UI template. It provides a local, placeholder-only framework for floating windows, screen flow, theming, module-driven menus, manifest-rendered components, configs, profiles, debug tools, and recovery behavior.

It is UI/framework only. It does not include memory editing, injection, networking, downloading, authentication, or game modification logic.

## Features

- Login/Games/Menu screen flow
- Android `WindowManager` floating overlay
- Touch pass-through outside the active overlay bounds
- Safe positioning and orientation-aware clamping
- Centralized theme system with presets
- Reusable ImGui component wrappers
- Game profile list and local module mapping
- Module-driven dynamic menu renderer
- Manifest tabs, sections, dependencies, and layout variants
- Centralized placeholder icon system
- Toast notification system
- Per-module config saving
- Per-module preset profiles
- Manifest validation and clean error UI
- Developer debug panel
- Crash-safe recovery checks
- Runtime overlay state manager
- Build/runtime mode flags

## Project Structure

- `app/src/main/java/com/lc/hello/service/FloatingWindowService.kt`  
  Android foreground overlay service, `WindowManager` setup, touch routing, keyboard focus, orientation handling, and runtime state updates.

- `app/src/main/java/com/lc/hello/service/OverlayBoundsManager.kt`  
  Android-side bounds helper for responsive sizing, drag clamping, orientation repair, and safe overlay positioning.

- `app/src/main/java/com/lc/hello/service/JniBridge.kt`  
  JNI bridge between Android/Kotlin and native ImGui.

- `app/src/main/cpp/native_imgui.cpp`  
  Native JNI/render glue: font setup, input forwarding, screen dispatch, and Android entry points.

- `app/src/main/cpp/core/TemplateModels.h`  
  Shared enums and data models for screens, icons, modules, manifests, themes, runtime state, and UI state.

- `app/src/main/cpp/core/AppState.h/.cpp`  
  Central shared app state and active `ModuleManager` instance.

- `app/src/main/cpp/ui/Theme.h/.cpp`  
  Central theme colors, spacing, sizing, style application, and theme presets.

- `app/src/main/cpp/ui/UIComponents.h/.cpp`  
  Reusable ImGui component wrappers, icons, cards, game rows, error panels, buttons, inputs, badges, and sidebar rows.

- `app/src/main/cpp/runtime/OverlayWindowManager.h/.cpp`  
  Native floating window sizing, shell drawing, active screen, close behavior, and responsive bounds.

- `app/src/main/cpp/modules/Modules.h/.cpp`  
  Game profile data, local demo modules, module registration, module loading, and manifest validation.

- `app/src/main/cpp/ui/DynamicMenuRenderer.h/.cpp`  
  Manifest-driven menu renderer for tabs, sections, dependencies, layout variants, settings, configs, and profiles.

- `app/src/main/cpp/screens/Screens.h/.cpp`  
  Login, Games, and Menu screen layout code.

- `app/src/main/cpp/screens/ScreenManager.h/.cpp`  
  Current-screen dispatch for the native render loop.

- `app/src/main/cpp/runtime/ImGuiRuntime.h/.cpp`  
  Native ImGui runtime helpers for font loading and queued input forwarding.

- `app/src/main/cpp/config/ConfigStorage.h/.cpp`  
  Lightweight per-module config and profile save/load/reset storage.

- `app/src/main/cpp/modules/FeatureFactory.h/.cpp`  
  Helpers for building manifest components, sections, dependencies, layouts, and default module state.

- `app/src/main/cpp/runtime/RuntimeState.h/.cpp`  
  Build mode policy and Android overlay/runtime readiness state.

- `app/src/main/cpp/runtime/RecoveryManager.h/.cpp`  
  Startup/frame recovery checks for window, theme, config, and module state.

- `app/src/main/cpp/ui/ToastManager.h/.cpp`  
  Toast notification queue and rendering.

- `app/src/main/cpp/ui/DebugPanel.h/.cpp`  
  Hidden developer diagnostics panel and safe local module/config actions.

- `app/src/main/cpp/CMakeLists.txt`  
  Native build setup for Dear ImGui and OpenGL ES.

- `app/src/main/assets/fonts/`  
  Font files copied into app storage for ImGui.

## Screen Flow

The template flow is:

`Login -> Games -> Module-loaded Menu`

Login is placeholder-only. Selecting an online placeholder game loads its local demo module and renders the menu from that module manifest.

## Adding a New Game

Add a `GameProfile` entry in `TemplateData::Games()` inside `app/src/main/cpp/modules/Modules.cpp`.

Each game needs:

- `gameId`
- `displayName`
- `online`
- `moduleId`

The game row UI stays generic: icon, name, status, and launch/select button.

## Adding a New Module

Create a class implementing `IModule` in `app/src/main/cpp/modules/Modules.cpp` or a new module source file.

Implement:

- `GetModuleId()`
- `GetGameId()`
- `GetDisplayName()`
- `GetVersion()`
- `GetManifest()`

Expose a factory such as `CreateMyModule()`, register it in `JniBridge_initImGui()` through `ModuleManager::RegisterModule(...)`, then map a game profile to that module id.

## Adding New Components

Add components to a module `FeatureManifest`.

Supported component types:

- `Switch`
- `Slider`
- `Dropdown`
- `Button`
- `Input`
- `Keybind`
- `Badge`

Components can be placed directly on a tab for backward compatibility, or inside `FeatureSection` groups.

## Adding a New Component Type

To add a new component type:

1. Add it to `ComponentType`.
2. Add default state handling in `InitializeModuleState()`.
3. Add render behavior in `DynamicMenuRenderer::RenderComponentControl()`.
4. Add config/profile save-load support if it stores values.
5. Add validation rules in `ModuleManager::ValidateComponent()` if needed.

## Sections and Dependencies

Tabs support grouped sections via `FeatureSection`.

Sections include:

- `id`
- `title`
- `description`
- `defaultOpen`
- `components`
- optional dependency
- optional layout variant

Components support optional dependency rules:

- `ShowWhen`
- `HideWhen`
- `EnableWhen`
- `DisableWhen`

Bool dependencies are the primary supported path. Int and string dependencies are handled when matching state exists.

## Layout Variants

Component layouts:

- `NormalRow`
- `CompactRow`
- `Card`
- `Inline`
- `FullWidth`
- `TwoColumn`

Section layouts:

- `Vertical`
- `Compact`
- `Card`
- `TwoColumn`

Defaults preserve the existing UI. Two-column sections fall back to single-column behavior on narrow screens.

## Adding a New Theme

Add a value to `ThemePreset`, then update `Theme::SetPreset(...)`.

Keep components reading colors from `Theme::Colors` instead of hardcoding colors inside screens or modules.

## Configs and Profiles

Config files are saved in the app private files directory under `configs`.

The template uses a lightweight key/value text format to avoid adding dependencies. It stores:

- active tab
- component values
- section open states
- selected theme preset

Profiles are per-module and include:

- `Default`
- `Testing`
- `Custom 1`
- `Custom 2`

The Settings tab includes minimal controls for saving, loading, resetting, creating, and deleting local profile data.

## Debug Panel

The debug panel is enabled by default in `Debug` and `Development` modes for realtime template inspection.
`Demo` and `Release` modes hide it automatically through `RuntimeBuildConfig::allowDebugPanel`.

The panel shows:

- overlay/runtime state
- renderer and ImGui readiness
- screen and orientation info
- current screen flow
- module manifest info
- config/profile state
- recovery state
- build mode flags
- safe lifecycle actions

## Build Modes

Runtime build modes are defined by `BuildMode` and `RuntimeBuildConfig`.

- `Debug`: debug panel, reload, reset, verbose logs, and demo modules enabled.
- `Development`: debug panel and development tools enabled with quieter logs.
- `Demo`: demo modules enabled, debug panel disabled by default.
- `Release`: debug tools hidden, verbose logs disabled, strict validation enabled.

The current default is `Development`.

## Android Overlay Notes

The overlay is intentionally sized to the active ImGui window, not fullscreen. This lets Android pass touches outside the overlay rectangle through to the app underneath.

Runtime state tracks:

- draw-over-apps permission
- surface active state
- renderer readiness
- ImGui readiness
- screen size
- orientation
- touch pass-through status

Orientation changes recalculate window size and clamp the overlay back inside screen bounds.

## Recovery System

Recovery checks repair safe local state:

- invalid active tab
- missing section state
- invalid slider/dropdown values
- invalid theme preset
- invalid screen/window state
- missing profile id

Recovery is lightweight and avoids repeated toast spam.

## Safety / Template Scope

This template is only a local UI/framework foundation. It does not include memory editing, injection, network loading, authentication, downloading, decryption, or game modification behavior.

## Troubleshooting

- Overlay not showing: confirm Android draw-over-apps permission and foreground service state.
- Touches blocked: confirm the overlay view is not fullscreen and `FLAG_NOT_TOUCH_MODAL` is still set.
- Module not loading: check `gameId`, `moduleId`, module registration, and manifest validation error in the debug panel.
- Invalid manifest: verify manifest version, tab ids, section ids, component ids, labels, slider bounds, and dropdown options.
- Config corrupted: reset config/profile from Settings or let recovery fall back to defaults.
- Window off-screen: rotate the device or restart the overlay; orientation handling clamps bounds.
- Debug panel unavailable: confirm `g_ShowDebugPanel` is true and build mode allows debug panels.

## Quality Checklist

- Login -> Games -> Menu flow works.
- Online game loads its mapped local module.
- Offline game does not load a module.
- Sections expand and collapse.
- Component dependencies show, hide, enable, and disable safely.
- Config save/load/reset works.
- Profile save/load/reset works.
- Theme switching updates immediately.
- Orientation changes keep the overlay in bounds.
- Touch pass-through still works outside the overlay.
- Recovery repairs invalid local state without crashing.
- Debug panel remains hidden unless explicitly enabled.
