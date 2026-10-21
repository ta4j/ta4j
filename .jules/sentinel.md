## 2026-10-21 - Path Traversal via Cache File Notes Parameter
**Vulnerability:** Unsanitized `notes` parameter in `AbstractHttpBarSeriesDataSource` cache file path generation allowed path traversal sequences (`..`, `/`, `\`) to write or read cache files outside the configured `responseCacheDir`.
**Learning:** String parameters appended to cache filenames (like optional notes or labels) can act as path traversal vectors if concatenated without sanitization or path boundary validation.
**Prevention:** Sanitize user-supplied filename components with strict allowlists (`[^a-zA-Z0-9-]`) and perform path boundary checks using `resolvedPath.normalize().startsWith(baseDir.normalize())`.
