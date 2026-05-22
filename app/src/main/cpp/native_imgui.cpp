#include <jni.h>
#include <android/log.h>
#include <GLES3/gl3.h>
#include <algorithm>
#include <mutex>
#include <string>
#include <vector>

#include "imgui.h"
#include "imgui_impl_opengl3.h"

#define LOG_TAG "ImGuiTemplate"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

enum TemplateScreen {
    SCREEN_LOGIN,
    SCREEN_GAMES,
    SCREEN_MAIN
};

static const ImVec4 COL_BG = ImVec4(0x09 / 255.0f, 0x0B / 255.0f, 0x14 / 255.0f, 0.96f);
static const ImVec4 COL_PANEL = ImVec4(0x10 / 255.0f, 0x13 / 255.0f, 0x1D / 255.0f, 0.88f);
static const ImVec4 COL_PANEL_HOVER = ImVec4(0x17 / 255.0f, 0x1B / 255.0f, 0x29 / 255.0f, 0.92f);
static const ImVec4 COL_ACCENT = ImVec4(0x7C / 255.0f, 0x4D / 255.0f, 1.0f, 1.0f);
static const ImVec4 COL_ACCENT_DARK = ImVec4(0x4E / 255.0f, 0x2D / 255.0f, 0xC9 / 255.0f, 1.0f);
static const ImVec4 COL_TEXT = ImVec4(0xF5 / 255.0f, 0xF7 / 255.0f, 0xFA / 255.0f, 1.0f);
static const ImVec4 COL_SUBTEXT = ImVec4(0x9A / 255.0f, 0xA4 / 255.0f, 0xB2 / 255.0f, 1.0f);
static const ImVec4 COL_SUCCESS = ImVec4(0.0f, 0xE6 / 255.0f, 0x76 / 255.0f, 1.0f);
static const ImVec4 COL_OFFLINE = ImVec4(0x7B / 255.0f, 0x81 / 255.0f, 0x90 / 255.0f, 1.0f);
static const ImVec4 COL_BORDER = ImVec4(1.0f, 1.0f, 1.0f, 0.06f);

struct TouchInputEvent { int action; float x; float y; };
struct KeyInputEvent { int unicodeChar; int keyCode; bool isDown; };

static std::string g_BodyFontPath;
static std::string g_HeadingFontPath;
static ImFont* g_BodyFont = nullptr;
static ImFont* g_HeadingFont = nullptr;
static bool g_Initialized = false;
static bool g_CloseRequested = false;
static int g_DisplayW = 1080;
static int g_DisplayH = 2400;
static int g_ScreenW = 1080;
static int g_ScreenH = 2400;
static TemplateScreen g_Screen = SCREEN_LOGIN;
static std::vector<TouchInputEvent> g_Touches;
static std::vector<KeyInputEvent> g_Keys;
static std::mutex g_InputMutex;

static char g_LicenseKey[96] = "";
static char g_SearchText[96] = "";
static char g_TestInput[96] = "";
static bool g_TestSwitch = true;
static float g_TestSlider = 50.0f;
static int g_TestDropdown = 0;

static ImVec4 WithAlpha(ImVec4 color, float alpha) {
    color.w = alpha;
    return color;
}

static ImVec4 LerpColor(const ImVec4& from, const ImVec4& to, float t) {
    t = std::max(0.0f, std::min(1.0f, t));
    return ImVec4(from.x + (to.x - from.x) * t,
                  from.y + (to.y - from.y) * t,
                  from.z + (to.z - from.z) * t,
                  from.w + (to.w - from.w) * t);
}

static float ClampFloat(float value, float minValue, float maxValue) {
    return std::max(minValue, std::min(value, maxValue));
}

static float ResponsiveWindowWidth(float percent, float minValue, float maxValue) {
    float available = std::max(320.0f, (float)g_ScreenW - 32.0f);
    float desired = ClampFloat((float)g_ScreenW * percent, minValue, maxValue);
    return std::min(desired, available);
}

static float ResponsiveWindowHeight(float percent, float minValue, float maxValue) {
    float available = std::max(420.0f, (float)g_ScreenH - 48.0f);
    float desired = ClampFloat((float)g_ScreenH * percent, minValue, maxValue);
    return std::min(desired, available);
}

static ImVec2 ScreenSizeFor(TemplateScreen screen) {
    if (screen == SCREEN_LOGIN) {
        return ImVec2(ResponsiveWindowWidth(0.86f, 430.0f, 590.0f),
                      ResponsiveWindowHeight(0.44f, 560.0f, 720.0f));
    }
    if (screen == SCREEN_GAMES) {
        return ImVec2(ResponsiveWindowWidth(0.90f, 500.0f, 700.0f),
                      ResponsiveWindowHeight(0.48f, 610.0f, 800.0f));
    }
    return ImVec2(ResponsiveWindowWidth(0.96f, 760.0f, 1080.0f),
                  ResponsiveWindowHeight(0.50f, 720.0f, 900.0f));
}

static void CenterNextWindow(ImVec2 size) {
    ImGui::SetNextWindowPos(ImVec2(0.0f, 0.0f), ImGuiCond_Always);
    ImGui::SetNextWindowSize(size, ImGuiCond_Always);
}

static void DrawGradientH(ImDrawList* dl, ImVec2 a, ImVec2 b, ImU32 left, ImU32 right) {
    dl->AddRectFilledMultiColor(a, b, left, right, right, left);
}

static void DrawGlowBorder(ImDrawList* dl, ImVec2 min, ImVec2 max, float rounding) {
    for (int i = 4; i >= 1; --i) {
        float spread = i * 3.0f;
        dl->AddRect(ImVec2(min.x - spread, min.y - spread),
                    ImVec2(max.x + spread, max.y + spread),
                    ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.035f * i)),
                    rounding + spread, 0, 1.0f);
    }
    dl->AddRect(min, max, ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.36f)), rounding, 0, 1.0f);
    dl->AddRect(ImVec2(min.x + 1, min.y + 1), ImVec2(max.x - 1, max.y - 1),
                ImGui::GetColorU32(COL_BORDER), rounding - 1.0f, 0, 1.0f);
}

static void DrawDragHandle(ImDrawList* dl, ImVec2 min, ImVec2 max) {
    float cx = (min.x + max.x) * 0.5f;
    dl->AddRectFilled(ImVec2(cx - 18.0f, min.y + 15.0f), ImVec2(cx + 18.0f, min.y + 19.0f),
                      ImGui::GetColorU32(WithAlpha(COL_SUBTEXT, 0.55f)), 3.0f);
}

static bool DrawCloseButton(ImDrawList* dl, ImVec2 max) {
    ImVec2 pos(max.x - 42.0f, max.y - (max.y - ImGui::GetWindowPos().y) + 16.0f);
    ImGui::SetCursorScreenPos(pos);
    bool clicked = ImGui::InvisibleButton("##close", ImVec2(30.0f, 30.0f));
    bool hovered = ImGui::IsItemHovered();
    ImU32 color = ImGui::GetColorU32(hovered ? COL_TEXT : COL_SUBTEXT);
    dl->AddLine(ImVec2(pos.x + 9, pos.y + 9), ImVec2(pos.x + 21, pos.y + 21), color, 1.6f);
    dl->AddLine(ImVec2(pos.x + 21, pos.y + 9), ImVec2(pos.x + 9, pos.y + 21), color, 1.6f);
    return clicked;
}

static bool BeginFloatingShell(const char* id, TemplateScreen screen, const char* title = nullptr) {
    ImVec2 size = ScreenSizeFor(screen);
    CenterNextWindow(size);
    ImGui::Begin(id, nullptr,
                 ImGuiWindowFlags_NoDecoration |
                 ImGuiWindowFlags_NoMove |
                 ImGuiWindowFlags_NoResize |
                 ImGuiWindowFlags_NoSavedSettings |
                 ImGuiWindowFlags_NoScrollbar |
                 ImGuiWindowFlags_NoScrollWithMouse);

    ImDrawList* dl = ImGui::GetWindowDrawList();
    ImVec2 min = ImGui::GetWindowPos();
    ImVec2 max(min.x + ImGui::GetWindowWidth(), min.y + ImGui::GetWindowHeight());
    dl->AddRectFilled(min, max, ImGui::GetColorU32(COL_BG), 18.0f);
    dl->AddRectFilled(ImVec2(min.x + 1, min.y + 1), ImVec2(max.x - 1, max.y - 1),
                      ImGui::GetColorU32(COL_PANEL), 18.0f);
    DrawGradientH(dl, min, ImVec2(max.x, min.y + 72.0f),
                  ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.16f)),
                  ImGui::GetColorU32(WithAlpha(COL_PANEL, 0.0f)));
    DrawGlowBorder(dl, min, max, 18.0f);
    DrawDragHandle(dl, min, max);
    if (DrawCloseButton(dl, max)) g_CloseRequested = true;

    if (title && title[0]) {
        ImGui::SetCursorPos(ImVec2(56.0f, 34.0f));
        if (g_HeadingFont) ImGui::PushFont(g_HeadingFont);
        ImGui::TextUnformatted(title);
        if (g_HeadingFont) ImGui::PopFont();
    }
    return true;
}

static void EndFloatingShell() {
    ImGui::End();
}

static void DrawIconPlaceholder(ImDrawList* dl, ImVec2 min, ImVec2 size, bool gamepad) {
    ImVec2 max(min.x + size.x, min.y + size.y);
    dl->AddRectFilled(min, max, ImGui::GetColorU32(WithAlpha(COL_TEXT, 0.06f)), 8.0f);
    dl->AddRect(min, max, ImGui::GetColorU32(COL_BORDER), 8.0f, 0, 1.0f);
    if (gamepad) {
        ImVec2 c((min.x + max.x) * 0.5f, (min.y + max.y) * 0.5f);
        dl->AddCircle(ImVec2(c.x - 10, c.y), 7.0f, ImGui::GetColorU32(WithAlpha(COL_SUBTEXT, 0.70f)), 16, 2.0f);
        dl->AddCircle(ImVec2(c.x + 10, c.y), 7.0f, ImGui::GetColorU32(WithAlpha(COL_SUBTEXT, 0.70f)), 16, 2.0f);
    } else {
        dl->AddLine(min, max, ImGui::GetColorU32(WithAlpha(COL_SUBTEXT, 0.55f)), 1.0f);
        dl->AddLine(ImVec2(max.x, min.y), ImVec2(min.x, max.y), ImGui::GetColorU32(WithAlpha(COL_SUBTEXT, 0.55f)), 1.0f);
    }
}

static void DrawStatusDot(ImDrawList* dl, ImVec2 center, bool online) {
    ImVec4 color = online ? COL_SUCCESS : COL_OFFLINE;
    dl->AddCircleFilled(center, 4.5f, ImGui::GetColorU32(color), 16);
    dl->AddCircle(center, 8.0f, ImGui::GetColorU32(WithAlpha(color, 0.18f)), 16, 1.0f);
}

static bool DrawTemplateButton(const char* label, ImVec2 size) {
    ImVec2 pos = ImGui::GetCursorScreenPos();
    ImGui::GetWindowDrawList()->AddRectFilled(ImVec2(pos.x - 3, pos.y - 3), ImVec2(pos.x + size.x + 3, pos.y + size.y + 3),
                                              ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.16f)), 10.0f);
    ImGui::PushStyleColor(ImGuiCol_Button, COL_ACCENT_DARK);
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, LerpColor(COL_ACCENT_DARK, COL_ACCENT, 0.45f));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, COL_ACCENT);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);
    bool clicked = ImGui::Button(label, size);
    ImGui::PopStyleVar();
    ImGui::PopStyleColor(3);
    return clicked;
}

static bool DrawTemplateInput(const char* id, char* buffer, size_t bufferSize, const char* hint, ImVec2 size) {
    ImGui::PushStyleColor(ImGuiCol_FrameBg, WithAlpha(COL_BG, 0.44f));
    ImGui::PushStyleColor(ImGuiCol_Border, COL_BORDER);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameRounding, 8.0f);
    ImGui::PushStyleVar(ImGuiStyleVar_FrameBorderSize, 1.0f);
    ImGui::SetNextItemWidth(size.x);
    bool edited = ImGui::InputTextWithHint(id, hint, buffer, bufferSize);
    ImGui::PopStyleVar(2);
    ImGui::PopStyleColor(2);
    return edited;
}

static bool DrawTemplateSwitch(const char* id, bool* value) {
    ImVec2 pos = ImGui::GetCursorScreenPos();
    ImVec2 size(48.0f, 26.0f);
    bool clicked = ImGui::InvisibleButton(id, size);
    if (clicked) *value = !*value;
    ImDrawList* dl = ImGui::GetWindowDrawList();
    ImVec4 bg = *value ? COL_ACCENT_DARK : WithAlpha(COL_SUBTEXT, 0.22f);
    dl->AddRectFilled(pos, ImVec2(pos.x + size.x, pos.y + size.y), ImGui::GetColorU32(bg), 13.0f);
    float knobX = *value ? pos.x + size.x - 19.0f : pos.x + 7.0f;
    dl->AddCircleFilled(ImVec2(knobX + 6.0f, pos.y + 13.0f), 8.0f, ImGui::GetColorU32(COL_TEXT), 20);
    return clicked;
}

static void DrawFieldLabel(const char* label, const char* desc = nullptr) {
    ImGui::TextUnformatted(label);
    if (desc) {
        ImGui::PushStyleColor(ImGuiCol_Text, COL_SUBTEXT);
        ImGui::TextUnformatted(desc);
        ImGui::PopStyleColor();
    }
}

static ImVec2 DrawComponentRow(const char* label, const char* desc, float height) {
    ImVec2 p = ImGui::GetCursorScreenPos();
    float w = ImGui::GetContentRegionAvail().x;
    ImDrawList* dl = ImGui::GetWindowDrawList();
    dl->AddRectFilled(p, ImVec2(p.x + w, p.y + height), ImGui::GetColorU32(WithAlpha(COL_PANEL_HOVER, 0.58f)), 10.0f);
    dl->AddRect(p, ImVec2(p.x + w, p.y + height), ImGui::GetColorU32(COL_BORDER), 10.0f, 0, 1.0f);
    ImGui::SetCursorScreenPos(ImVec2(p.x + 18.0f, p.y + 12.0f));
    DrawFieldLabel(label, desc);
    return p;
}

static void ApplyTemplateStyle() {
    ImGui::StyleColorsDark();
    ImGuiStyle& style = ImGui::GetStyle();
    style.WindowRounding = 18.0f;
    style.ChildRounding = 12.0f;
    style.FrameRounding = 8.0f;
    style.PopupRounding = 10.0f;
    style.GrabRounding = 8.0f;
    style.WindowBorderSize = 0.0f;
    style.FrameBorderSize = 0.0f;
    style.WindowPadding = ImVec2(0.0f, 0.0f);
    style.FramePadding = ImVec2(14.0f, 9.0f);
    style.ItemSpacing = ImVec2(12.0f, 10.0f);
    style.ItemInnerSpacing = ImVec2(10.0f, 7.0f);
    style.ScrollbarSize = 5.0f;
    style.Colors[ImGuiCol_WindowBg] = ImVec4(0, 0, 0, 0);
    style.Colors[ImGuiCol_ChildBg] = COL_PANEL;
    style.Colors[ImGuiCol_PopupBg] = COL_PANEL;
    style.Colors[ImGuiCol_Text] = COL_TEXT;
    style.Colors[ImGuiCol_TextDisabled] = COL_SUBTEXT;
    style.Colors[ImGuiCol_Border] = COL_BORDER;
    style.Colors[ImGuiCol_FrameBg] = WithAlpha(COL_BG, 0.44f);
    style.Colors[ImGuiCol_FrameBgHovered] = WithAlpha(COL_ACCENT, 0.18f);
    style.Colors[ImGuiCol_FrameBgActive] = WithAlpha(COL_ACCENT, 0.26f);
    style.Colors[ImGuiCol_CheckMark] = COL_ACCENT;
    style.Colors[ImGuiCol_Header] = WithAlpha(COL_ACCENT, 0.16f);
    style.Colors[ImGuiCol_HeaderHovered] = WithAlpha(COL_ACCENT, 0.24f);
    style.Colors[ImGuiCol_HeaderActive] = WithAlpha(COL_ACCENT, 0.32f);
    style.Colors[ImGuiCol_SliderGrab] = COL_ACCENT;
    style.Colors[ImGuiCol_SliderGrabActive] = COL_ACCENT;
}

static void LoadFonts() {
    ImGuiIO& io = ImGui::GetIO();
    const float scale = 2.6f;
    if (!g_BodyFontPath.empty()) g_BodyFont = io.Fonts->AddFontFromFileTTF(g_BodyFontPath.c_str(), 13.0f * scale);
    if (!g_HeadingFontPath.empty()) g_HeadingFont = io.Fonts->AddFontFromFileTTF(g_HeadingFontPath.c_str(), 16.0f * scale);
    if (!g_BodyFont) g_BodyFont = io.Fonts->AddFontFromFileTTF("/system/fonts/Roboto-Regular.ttf", 13.0f * scale);
    if (!g_HeadingFont) g_HeadingFont = io.Fonts->AddFontFromFileTTF("/system/fonts/Roboto-Bold.ttf", 16.0f * scale);
    if (!g_BodyFont) g_BodyFont = io.Fonts->AddFontDefault();
    if (!g_HeadingFont) g_HeadingFont = g_BodyFont;
    io.FontDefault = g_BodyFont;
}

static void GoTo(TemplateScreen screen) {
    g_Screen = screen;
}

static void DrawLoginScreen() {
    BeginFloatingShell("##login_window", SCREEN_LOGIN);
    ImDrawList* dl = ImGui::GetWindowDrawList();
    float w = ImGui::GetWindowWidth();
    float h = ImGui::GetWindowHeight();
    ImVec2 logoMin(w * 0.5f - 44.0f, 72.0f);
    DrawIconPlaceholder(dl, ImVec2(ImGui::GetWindowPos().x + logoMin.x, ImGui::GetWindowPos().y + logoMin.y), ImVec2(88, 88), false);
    dl->AddTriangleFilled(ImVec2(ImGui::GetWindowPos().x + w * 0.5f, ImGui::GetWindowPos().y + logoMin.y + 18.0f),
                          ImVec2(ImGui::GetWindowPos().x + logoMin.x + 18.0f, ImGui::GetWindowPos().y + logoMin.y + 70.0f),
                          ImVec2(ImGui::GetWindowPos().x + logoMin.x + 70.0f, ImGui::GetWindowPos().y + logoMin.y + 70.0f),
                          ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.78f)));
    ImGui::SetCursorPos(ImVec2(0, 176));
    if (g_HeadingFont) ImGui::PushFont(g_HeadingFont);
    ImGui::SetCursorPosX((w - ImGui::CalcTextSize("AURORA").x) * 0.5f);
    ImGui::TextUnformatted("AURORA");
    if (g_HeadingFont) ImGui::PopFont();
    ImGui::PushStyleColor(ImGuiCol_Text, COL_ACCENT);
    ImGui::SetCursorPosX((w - ImGui::CalcTextSize("EXECUTIVE").x) * 0.5f);
    ImGui::TextUnformatted("EXECUTIVE");
    ImGui::PopStyleColor();
    ImGui::SetCursorPos(ImVec2(42, 270));
    DrawFieldLabel("Enter License Key", "Your key is required to continue");
    ImGui::SetCursorPos(ImVec2(42, 342));
    DrawTemplateInput("##license", g_LicenseKey, sizeof(g_LicenseKey), "License Key", ImVec2(w - 84, 44));
    ImGui::SetCursorPos(ImVec2(42, 412));
    if (DrawTemplateButton("AUTHENTICATE", ImVec2(w - 84, 52))) GoTo(SCREEN_GAMES);
    float footerY = h - 64;
    dl->AddLine(ImVec2(ImGui::GetWindowPos().x + 42, ImGui::GetWindowPos().y + footerY - 18),
                ImVec2(ImGui::GetWindowPos().x + w - 42, ImGui::GetWindowPos().y + footerY - 18),
                ImGui::GetColorU32(COL_BORDER), 1.0f);
    DrawStatusDot(dl, ImVec2(ImGui::GetWindowPos().x + 52, ImGui::GetWindowPos().y + footerY + 6), true);
    ImGui::SetCursorPos(ImVec2(68, footerY - 4));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_SUBTEXT);
    ImGui::TextUnformatted("Secure Connection");
    ImGui::SetCursorPos(ImVec2(w - 98, footerY - 4));
    ImGui::TextUnformatted("v1.0.0");
    ImGui::PopStyleColor();
    EndFloatingShell();
}

static void DrawGamesScreen() {
    BeginFloatingShell("##games_window", SCREEN_GAMES, "GAMES");
    ImDrawList* dl = ImGui::GetWindowDrawList();
    ImVec2 wp = ImGui::GetWindowPos();
    float w = ImGui::GetWindowWidth();
    dl->AddCircleFilled(ImVec2(wp.x + 30, wp.y + 34), 10, ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.80f)), 20);
    ImGui::SetCursorPos(ImVec2(w - 86, 24));
    ImGui::PushStyleColor(ImGuiCol_Button, ImVec4(0, 0, 0, 0));
    ImGui::PushStyleColor(ImGuiCol_ButtonHovered, WithAlpha(COL_ACCENT, 0.16f));
    ImGui::PushStyleColor(ImGuiCol_ButtonActive, WithAlpha(COL_ACCENT, 0.24f));
    ImGui::Button("R", ImVec2(34, 34));
    ImGui::PopStyleColor(3);
    ImGui::SetCursorPos(ImVec2(28, 84));
    DrawTemplateInput("##search", g_SearchText, sizeof(g_SearchText), "Search games...", ImVec2(w - 56, 42));
    const char* names[] = {"Test Game 01", "Test Game 02", "Test Game 03", "Test Game 04", "Test Game 05"};
    bool online[] = {true, true, true, false, false};
    float y = 154;
    for (int i = 0; i < 5; ++i) {
        ImVec2 rowMin(wp.x + 28, wp.y + y);
        ImVec2 rowMax(wp.x + w - 28, wp.y + y + 64);
        bool hovered = ImGui::IsMouseHoveringRect(rowMin, rowMax);
        dl->AddRectFilled(rowMin, rowMax, ImGui::GetColorU32(hovered ? COL_PANEL_HOVER : WithAlpha(COL_PANEL_HOVER, 0.48f)), 10);
        dl->AddRect(rowMin, rowMax, ImGui::GetColorU32(COL_BORDER), 10, 0, 1);
        DrawIconPlaceholder(dl, ImVec2(rowMin.x + 12, rowMin.y + 10), ImVec2(44, 44), true);
        dl->AddText(ImVec2(rowMin.x + 72, rowMin.y + 20), ImGui::GetColorU32(COL_TEXT), names[i]);
        DrawStatusDot(dl, ImVec2(rowMax.x - 150, rowMin.y + 32), online[i]);
        dl->AddText(ImVec2(rowMax.x - 134, rowMin.y + 20), ImGui::GetColorU32(online[i] ? COL_SUCCESS : COL_OFFLINE), online[i] ? "ONLINE" : "OFFLINE");
        ImGui::SetCursorScreenPos(ImVec2(rowMax.x - 58, rowMin.y + 14));
        if (DrawTemplateButton(">", ImVec2(42, 36))) GoTo(SCREEN_MAIN);
        y += 76;
    }
    ImGui::SetCursorPos(ImVec2(28, ImGui::GetWindowHeight() - 54));
    DrawTemplateButton("*", ImVec2(38, 34));
    EndFloatingShell();
}

static void DrawMainScreen() {
    BeginFloatingShell("##main_window", SCREEN_MAIN);
    ImDrawList* dl = ImGui::GetWindowDrawList();
    ImVec2 wp = ImGui::GetWindowPos();
    float w = ImGui::GetWindowWidth();
    float h = ImGui::GetWindowHeight();
    float outerPad = ClampFloat(w * 0.035f, 24.0f, 36.0f);
    float topY = 72.0f;
    float sidebarW = ClampFloat(w * 0.22f, 186.0f, 230.0f);
    float sidebarGap = ClampFloat(w * 0.035f, 24.0f, 38.0f);
    float contentX = outerPad + sidebarW + sidebarGap;
    float contentW = w - contentX - outerPad;
    float contentTop = topY + 6.0f;
    float titleH = 58.0f;
    float panelTop = contentTop + titleH;
    float panelH = h - panelTop - outerPad;
    float rowGap = 12.0f;
    float rowH = ClampFloat((panelH - rowGap * 6.0f - 28.0f) / 7.0f, 66.0f, 82.0f);
    float controlW = ClampFloat(contentW * 0.36f, 170.0f, 260.0f);

    ImVec2 sidebarMin(wp.x + outerPad, wp.y + topY);
    ImVec2 sidebarMax(sidebarMin.x + sidebarW, wp.y + h - outerPad);
    dl->AddRectFilled(sidebarMin, sidebarMax, ImGui::GetColorU32(WithAlpha(COL_BG, 0.28f)), 12);
    dl->AddLine(ImVec2(sidebarMax.x + sidebarGap * 0.5f, sidebarMin.y), ImVec2(sidebarMax.x + sidebarGap * 0.5f, sidebarMax.y), ImGui::GetColorU32(COL_BORDER), 1);
    const char* tabs[] = {"Main", "Visuals", "Players", "World", "Misc", "Configs", "Settings"};
    float tabY = sidebarMin.y + 18;
    float tabH = 42.0f;
    float tabGap = 12.0f;
    for (int i = 0; i < 7; ++i) {
        bool active = i == 0;
        ImVec2 min(sidebarMin.x + 14, tabY);
        ImVec2 max(sidebarMax.x - 14, tabY + tabH);
        if (active) {
            dl->AddRectFilled(min, max, ImGui::GetColorU32(WithAlpha(COL_ACCENT, 0.24f)), 8);
            dl->AddRectFilled(min, ImVec2(min.x + 4, max.y), ImGui::GetColorU32(COL_ACCENT), 8);
        }
        DrawIconPlaceholder(dl, ImVec2(min.x + 12, min.y + 11), ImVec2(20, 20), false);
        dl->AddText(ImVec2(min.x + 44, min.y + 12), ImGui::GetColorU32(active ? COL_TEXT : COL_SUBTEXT), tabs[i]);
        tabY += tabH + tabGap;
    }
    ImVec2 attachedMin(sidebarMin.x + 12, sidebarMax.y - 54);
    dl->AddRect(attachedMin, ImVec2(sidebarMax.x - 12, sidebarMax.y - 10), ImGui::GetColorU32(COL_BORDER), 8, 0, 1);
    DrawStatusDot(dl, ImVec2(attachedMin.x + 22, attachedMin.y + 22), true);
    dl->AddText(ImVec2(attachedMin.x + 42, attachedMin.y + 13), ImGui::GetColorU32(COL_SUBTEXT), "Attached");
    ImGui::SetCursorPos(ImVec2(contentX, contentTop));
    ImGui::PushStyleColor(ImGuiCol_Text, COL_ACCENT);
    if (g_HeadingFont) ImGui::PushFont(g_HeadingFont);
    ImGui::TextUnformatted("MAIN");
    if (g_HeadingFont) ImGui::PopFont();
    ImGui::PopStyleColor();
    ImGui::PushStyleColor(ImGuiCol_Text, COL_SUBTEXT);
    ImGui::TextUnformatted("UI Components Test");
    ImGui::PopStyleColor();

    ImGui::SetCursorPos(ImVec2(contentX, panelTop));
    ImGui::PushStyleVar(ImGuiStyleVar_WindowPadding, ImVec2(14.0f, 14.0f));
    ImGui::BeginChild("##component_panel", ImVec2(contentW, panelH), true, ImGuiWindowFlags_NoScrollbar);
    float rowInnerW = ImGui::GetContentRegionAvail().x;
    float controlX = ImGui::GetCursorScreenPos().x + rowInnerW - controlW - 18.0f;
    float rowTextY = 0.0f;
    ImVec2 row;

    row = DrawComponentRow("Test Switch", "This is a test switch", rowH);
    rowTextY = row.y + (rowH - 26.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX + controlW - 48.0f, rowTextY));
    DrawTemplateSwitch("##switch", &g_TestSwitch);
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Slider", "This is a test slider", rowH);
    rowTextY = row.y + (rowH - 34.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX, rowTextY));
    ImGui::SetNextItemWidth(controlW);
    ImGui::SliderFloat("##slider", &g_TestSlider, 0, 100, "%.0f%%");
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Dropdown", "This is a test dropdown", rowH);
    rowTextY = row.y + (rowH - 38.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX, rowTextY));
    const char* options[] = {"Option 1", "Option 2", "Option 3"};
    ImGui::SetNextItemWidth(controlW);
    ImGui::Combo("##dropdown", &g_TestDropdown, options, IM_ARRAYSIZE(options));
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Button", "This is a test button", rowH);
    rowTextY = row.y + (rowH - 40.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX, rowTextY));
    DrawTemplateButton("Test Button", ImVec2(controlW, 40));
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Keybind", "This is a test keybind", rowH);
    rowTextY = row.y + (rowH - 38.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX + controlW - 124.0f, rowTextY));
    DrawTemplateButton("F1", ImVec2(124, 38));
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Input", "This is a test input", rowH);
    rowTextY = row.y + (rowH - 40.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX, rowTextY));
    DrawTemplateInput("##testinput", g_TestInput, sizeof(g_TestInput), "Type something...", ImVec2(controlW, 40));
    ImGui::SetCursorScreenPos(ImVec2(row.x, row.y + rowH + rowGap));

    row = DrawComponentRow("Test Badge", "This is a test status badge", rowH);
    rowTextY = row.y + (rowH - 32.0f) * 0.5f;
    ImGui::SetCursorScreenPos(ImVec2(controlX + controlW - 84.0f, rowTextY));
    DrawTemplateButton("TEST", ImVec2(84, 32));
    ImGui::EndChild();
    ImGui::PopStyleVar();
    EndFloatingShell();
}

extern "C" {

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_setFontPaths(JNIEnv* env, jobject, jstring bodyPath, jstring headingPath) {
    const char* body = bodyPath ? env->GetStringUTFChars(bodyPath, nullptr) : nullptr;
    const char* heading = headingPath ? env->GetStringUTFChars(headingPath, nullptr) : nullptr;
    g_BodyFontPath = body ? body : "";
    g_HeadingFontPath = heading ? heading : "";
    if (body) env->ReleaseStringUTFChars(bodyPath, body);
    if (heading) env->ReleaseStringUTFChars(headingPath, heading);
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_setScreenSize(JNIEnv*, jobject, jint width, jint height) {
    g_ScreenW = std::max(1, (int)width);
    g_ScreenH = std::max(1, (int)height);
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_initImGui(JNIEnv*, jobject) {
    if (g_Initialized) {
        ImGui_ImplOpenGL3_Shutdown();
        ImGui::DestroyContext();
        g_Initialized = false;
    }
    IMGUI_CHECKVERSION();
    ImGui::CreateContext();
    ImGuiIO& io = ImGui::GetIO();
    io.IniFilename = nullptr;
    io.LogFilename = nullptr;
    ApplyTemplateStyle();
    LoadFonts();
    ImGui_ImplOpenGL3_Init("#version 300 es");
    g_Screen = SCREEN_LOGIN;
    g_CloseRequested = false;
    g_Initialized = true;
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_resizeImGui(JNIEnv*, jobject, jint width, jint height) {
    g_DisplayW = (int)width;
    g_DisplayH = (int)height;
    ImGui::GetIO().DisplaySize = ImVec2((float)width, (float)height);
    glViewport(0, 0, width, height);
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_stepAndRenderImGui(JNIEnv*, jobject) {
    if (!g_Initialized) return;
    {
        std::lock_guard<std::mutex> lock(g_InputMutex);
        ImGuiIO& io = ImGui::GetIO();
        for (const auto& ev : g_Touches) {
            io.AddMousePosEvent(ev.x, ev.y);
            if (ev.action == 0) io.AddMouseButtonEvent(0, true);
            if (ev.action == 1) io.AddMouseButtonEvent(0, false);
        }
        g_Touches.clear();
        for (const auto& ev : g_Keys) {
            if (ev.unicodeChar > 0 && ev.isDown) io.AddInputCharacter((unsigned int)ev.unicodeChar);
            if (ev.keyCode == 67) io.AddKeyEvent(ImGuiKey_Backspace, ev.isDown);
            if (ev.keyCode == 66) io.AddKeyEvent(ImGuiKey_Enter, ev.isDown);
        }
        g_Keys.clear();
    }
    ImGui_ImplOpenGL3_NewFrame();
    ImGui::NewFrame();
    if (g_Screen == SCREEN_LOGIN) DrawLoginScreen();
    else if (g_Screen == SCREEN_GAMES) DrawGamesScreen();
    else DrawMainScreen();
    ImGui::Render();
    glClearColor(0, 0, 0, 0);
    glClear(GL_COLOR_BUFFER_BIT);
    ImGui_ImplOpenGL3_RenderDrawData(ImGui::GetDrawData());
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_onTouchEvent(JNIEnv*, jobject, jint action, jfloat x, jfloat y) {
    std::lock_guard<std::mutex> lock(g_InputMutex);
    g_Touches.push_back({(int)action, (float)x, (float)y});
}

JNIEXPORT void JNICALL
Java_com_lc_hello_service_JniBridge_onKeyEvent(JNIEnv*, jobject, jint unicodeChar, jint keyCode, jboolean isDown) {
    std::lock_guard<std::mutex> lock(g_InputMutex);
    g_Keys.push_back({(int)unicodeChar, (int)keyCode, isDown == JNI_TRUE});
}

JNIEXPORT jboolean JNICALL
Java_com_lc_hello_service_JniBridge_wantTextInput(JNIEnv*, jobject) {
    if (!g_Initialized) return JNI_FALSE;
    return ImGui::GetIO().WantTextInput ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_lc_hello_service_JniBridge_checkCloseRequested(JNIEnv*, jobject) {
    bool requested = g_CloseRequested;
    g_CloseRequested = false;
    return requested ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jfloatArray JNICALL
Java_com_lc_hello_service_JniBridge_getDesiredWindowSize(JNIEnv* env, jobject) {
    ImVec2 size = ScreenSizeFor(g_Screen);
    jfloatArray result = env->NewFloatArray(2);
    jfloat data[2] = { size.x, size.y };
    env->SetFloatArrayRegion(result, 0, 2, data);
    return result;
}

}
