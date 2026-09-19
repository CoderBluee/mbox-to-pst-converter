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

