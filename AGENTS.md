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
> 6. **🔍 Full Master Rulebook:** [Desktop Packaging Master Rulebook (SSOT)](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/packaging/desktop-packaging-master-rulebook.md) & [Developer KT Handover Rulebook](file:///Users/akashsahu.blue/Documents/Akas/software-selling-platform/docs/rules/engineering/dev-kt-handover-rulebook.md).
<!-- SSOT_RULES_HEADER_END -->

You must read `GEMINI.md` in the project root before performing any task. It contains project overview, build commands, architecture, file structure, known issues, and other critical context for working on this codebase.

## 📋 Mandatory Web Platform & KT Synchronization Rule (Single Source of Truth)
Whenever working on any desktop converter tool, all Knowledge Transfer (KT) documents, panel-by-panel feature breakdowns, and architecture updates **MUST ALWAYS be written directly into the product's `transferred_from_tool_dev` folder**:
- **Target Products Directory**: `/Users/akashsahu.blue/Documents/Akas/software-selling-platform/products/`
- **Product-Specific Tool Dev Subfolder**: `/Users/akashsahu.blue/Documents/Akas/software-selling-platform/products/<product-slug>/transferred_from_tool_dev/`
- **Target KT File Path**: `/Users/akashsahu.blue/Documents/Akas/software-selling-platform/products/<product-slug>/transferred_from_tool_dev/KT_<TOOL_NAME>.md`
- **Actions Required**:
  1. Write all technical specs and panel-wise feature lists directly into `<product-slug>/transferred_from_tool_dev/KT_<TOOL_NAME>.md`.
  2. The web developer will read from `transferred_from_tool_dev/` to build/update `content.md` and marketing pages.
  3. Keep `metadata.json` (pricing, screenshots, system specs, reviews) updated without placeholder links or null fields.
  4. Maintain `MASTER_PRODUCTS_KT_AND_WEB_SPEC.md` at `/software-selling-platform/products/transferred_from_tool_dev/MASTER_PRODUCTS_KT_AND_WEB_SPEC.md`.
  5. Never create duplicate local markdown KT files in the desktop code directory.

