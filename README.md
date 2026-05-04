# Metrics-Studio

Desktop app to analyze Java project metrics (LOC/CLOC/NCLOC/etc.).

## Run

- `mvn javafx:run`

## Current behavior (MVP)

- Select a project folder
- Scans for `.java` files
- Skips tests by default (`src/test`, `test`, `tests`, `*Test.java`)
- Skips common build folders (`target`, `build`, `out`, `.git`)
