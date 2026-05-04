# Metrics-Studio

Desktop app to analyze Java project metrics (LOC/CLOC/NCLOC/etc.).

## Run

- `mvn javafx:run`

## Build JAR

- `mvn -q clean package -DskipTests`
- Output JAR: `target/metrics-studio-0.1.0.jar`
- Dependencies: `target/libs/`
- Run (Windows PowerShell):
  - `java --module-path target/libs --add-modules javafx.controls,javafx.graphics -cp "target/metrics-studio-0.1.0.jar;target/libs/*" com.metricsstudio.MainApp`

## Current behavior (MVP)

- Select a project folder
- Scans for `.java` files
- Skips tests by default (`src/test`, `test`, `tests`, `*Test.java`)
- Skips common build folders (`target`, `build`, `out`, `.git`)
