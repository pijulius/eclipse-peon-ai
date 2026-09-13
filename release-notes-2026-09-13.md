# Peon AI — UI & Config Polish

**Model & Agent configuration**
- **Model dropdown keeps your input:** typed model names are no longer added to the dropdown list — the value stays in the field, and server-provided entries remain the only list items. Case-insensitive matching against the server list (canonical = server ID).
- **Native combo widgets:** model selection (Basic + Advanced pages) and the Think dropdown now use native, editable SWT combos — consistent keyboard behavior across platforms.
- **Layout fix:** removed the stray empty space below the extra-body examples on the Advanced settings page.

**Internal**
- Selection-context state hardened (`UserContext`): text selection is cleared when switching files; status line only refreshes on actual changes — covered by 12 new tests.
- Test suite extended: dead code removed, debug log gate re-enabled, 201 tests green.
