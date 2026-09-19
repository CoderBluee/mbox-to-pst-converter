# MBOX to PST Converter — Developer Guide

<!-- SSOT_RULES_HEADER_START -->
> **🚨 MANDATORY SINGLE SOURCE OF TRUTH (SSOT) FOR ALL RULES & PACKAGING:**
> All engineering rules, pre-packaging checklists, packaging pipelines, UI standards, and content guidelines are centralized in the master repository. Do NOT duplicate rule files inside this project. Always consult and reference the master SSOT directory:
> 
> 📁 **Master Rulebook Hub:** [`docs/rules/`](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/README.md)
> 
> ### 🛑 Mandatory Pre-Packaging Gates (Before Building JAR / EXE / DMG):
> 1. **💡 Universal Default Light Theme:** ALL desktop tools MUST initialize with **FlatLaf Light** (`FlatLightLaf.setup()` / `FlatMacLightLaf`) or native Light theme by default on first launch. Dark mode may only exist as an optional user toggle.
> 2. **⌨️ 100% Keyboard Operability:** The entire conversion flow must be executable mouse-free using `Tab`/`Shift+Tab` focus cycles, `Enter`/`Space`, and arrow keys (Section 508 standard).
> 3. **📦 Shaded Fat JAR Size Gate:** Fat JAR size must be `< 75 MB` (Target: 50MB–65MB). If `> 100MB`, stop and inspect dependency tree (`mvn dependency:tree`).
> 4. **🪟 Windows Setup EXE:** Output standard `Prism-[Tool-Name]-Setup.exe`, embedded 64-bit JRE 17/21, total installer `< 135 MB`.
> 5. **🍎 macOS DMG Package:** Install4j project preserved in Obsidian vault (`Obsidian-Work/Package Maker/Install4j/`), output to `~/Downloads/`.
> 6. **🔍 Full Master Checklist:** [Pre-Packaging Checklist](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/packaging/pre-packaging-checklist.md) & [Developer KT Handover Rulebook](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/engineering/dev-kt-handover-rulebook.md).
<!-- SSOT_RULES_HEADER_END -->


