# Third-party notices

These notices apply to the third-party component identified below. DC Language's own license remains in `LICENSE` (or `META-INF/LICENSE` inside a JAR).

## Gson 2.10.1

- Project: Google Gson
- Dependency: `com.google.code.gson:gson:2.10.1`
- License: Apache License, Version 2.0; the unmodified text is included in [licenses/gson/Apache-2.0.txt](licenses/gson/Apache-2.0.txt).
- Upstream copyright and source attribution: [licenses/gson/COPYRIGHT.txt](licenses/gson/COPYRIGHT.txt).
- Upstream source: https://github.com/google/gson/tree/gson-parent-2.10.1

DC Language uses Gson as a separate runtime dependency. The DC Language Server and MPS plugin distributions include Gson. These distributions do not relocate Gson's package names. The language server repackages Gson's classes in its combined JAR; the MPS distribution retains the original Gson JAR.

This file is a project-authored dependency notice. The accompanying copyright statements are retained from the Gson 2.10.1 sources.
