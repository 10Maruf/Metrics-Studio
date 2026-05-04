package com.metricsstudio;

import com.metricsstudio.analysis.AnalysisResult;
import com.metricsstudio.analysis.CocomoBasic;
import com.metricsstudio.analysis.ProjectAnalyzer;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.nio.file.Path;
import java.util.Locale;

public class MainApp extends Application {

    private static final class ResultTabs {
        final TabPane tabs = new TabPane();

        final TextArea summaryText = new TextArea();

        final GridPane codeSizeGrid = new GridPane();
        final GridPane designSizeGrid = new GridPane();
        final GridPane halsteadGrid = new GridPane();
        final GridPane cocomoGrid = new GridPane();

        final ComboBox<CocomoBasic.Mode> cocomoMode = new ComboBox<>();
        final TextField cocomoKloc = new TextField();

        private long lastNonBlankLoc = 0;
        private boolean suppressCocomoUpdates = false;

        ResultTabs() {
            summaryText.setEditable(false);
            summaryText.setWrapText(false);

            configureGrid(codeSizeGrid);
            configureGrid(designSizeGrid);
            configureGrid(halsteadGrid);
            configureGrid(cocomoGrid);

            cocomoMode.getItems().addAll(CocomoBasic.Mode.values());
            cocomoMode.getSelectionModel().select(CocomoBasic.Mode.ORGANIC);
            cocomoMode.setMaxWidth(Double.MAX_VALUE);

            cocomoKloc.setPromptText("KLOC (e.g. 12.345)");

            Tab summary = new Tab("Summary", summaryText);
            Tab codeSize = new Tab("Code Size", wrapGrid(codeSizeGrid));
            Tab designSize = new Tab("Design Size", wrapGrid(designSizeGrid));
            Tab halstead = new Tab("Halstead", wrapGrid(halsteadGrid));
            Tab cocomo = new Tab("COCOMO", buildCocomoPane());
            summary.setClosable(false);
            codeSize.setClosable(false);
            designSize.setClosable(false);
            halstead.setClosable(false);
            cocomo.setClosable(false);

            tabs.getTabs().addAll(summary, codeSize, designSize, halstead, cocomo);

            cocomoMode.valueProperty().addListener((obs, oldV, newV) -> {
                if (!suppressCocomoUpdates) {
                    updateCocomoGrid();
                }
            });
            cocomoKloc.textProperty().addListener((obs, oldV, newV) -> {
                if (!suppressCocomoUpdates) {
                    updateCocomoGrid();
                }
            });
        }

        void clear() {
            suppressCocomoUpdates = true;
            summaryText.clear();
            codeSizeGrid.getChildren().clear();
            designSizeGrid.getChildren().clear();
            halsteadGrid.getChildren().clear();
            cocomoGrid.getChildren().clear();
            cocomoKloc.clear();
            lastNonBlankLoc = 0;
            suppressCocomoUpdates = false;
        }

        void showError(String message) {
            clear();
            summaryText.setText("Error: " + message);
        }

        void setResult(AnalysisResult r) {
            clear();
            summaryText.setText(r.toPrettyText());

            long nonBlankLoc = Math.max(0, r.loc - r.blankLoc);
            lastNonBlankLoc = nonBlankLoc;
            addRow(codeSizeGrid, 0, "LOC", String.valueOf(r.loc));
            addRow(codeSizeGrid, 1, "Blank LOC", String.valueOf(r.blankLoc));
            addRow(codeSizeGrid, 2, "CLOC", String.valueOf(r.cloc));
            addRow(codeSizeGrid, 3, "NCLOC", String.valueOf(r.ncloc));
            addRow(codeSizeGrid, 4, "Non-blank LOC", String.valueOf(nonBlankLoc));
            addRow(codeSizeGrid, 5, "Executable LOC", String.valueOf(r.ast.executableLoc));
            addRow(codeSizeGrid, 6, "Comment density", String.format(Locale.ROOT, "%.4f", r.commentDensity));

            addRow(designSizeGrid, 0, "Sub-packages", String.valueOf(r.ast.subPackageCount));
            addRow(designSizeGrid, 1, "Classes", String.valueOf(r.ast.classCount));
            addRow(designSizeGrid, 2, "Interfaces", String.valueOf(r.ast.interfaceCount));
            addRow(designSizeGrid, 3, "Design patterns (heuristic)", String.valueOf(r.ast.designPatternCount));
            addRow(designSizeGrid, 4, "Methods", String.valueOf(r.ast.methodCount));
            addRow(designSizeGrid, 5, "Avg methods per class",
                    String.format(Locale.ROOT, "%.2f", r.ast.averageMethodsPerClass));

            long n1 = r.ast.halsteadDistinctOperator;
            long n2 = r.ast.halsteadDistinctOperands;
            long N1 = r.ast.halsteadTotalOperators;
            long N2 = r.ast.halsteadTotalOperands;
            long N = N1 + N2;
            long mu = n1 + n2;
            double V = (mu > 1) ? (N * (Math.log(mu) / Math.log(2.0))) : 0.0;
            double estLen = (n1 > 1 ? n1 * (Math.log(n1) / Math.log(2.0)) : 0.0)
                    + (n2 > 1 ? n2 * (Math.log(n2) / Math.log(2.0)) : 0.0);
            int n2Star = r.ast.halsteadUniqueIoParams;
            double VStar = (2.0 + n2Star) > 1.0 ? ((2.0 + n2Star) * (Math.log(2.0 + n2Star) / Math.log(2.0))) : 0.0;
            double L = (V > 0.0) ? (VStar / V) : 0.0;
            double D = (L > 0.0) ? (1.0 / L) : 0.0;
            double Lp = (n1 > 0 && N2 > 0) ? ((2.0 / (double) n1) * ((double) n2 / (double) N2)) : 0.0;
            double Dp = (Lp > 0.0) ? (1.0 / Lp) : 0.0;
            double E = (Lp > 0.0) ? (V / Lp) : 0.0;

            addRow(halsteadGrid, 0, "μ1 (unique operators)", String.valueOf(n1));
            addRow(halsteadGrid, 1, "μ2 (unique operands)", String.valueOf(n2));
            addRow(halsteadGrid, 2, "N1 (total operators)", String.valueOf(N1));
            addRow(halsteadGrid, 3, "N2 (total operands)", String.valueOf(N2));
            addRow(halsteadGrid, 4, "Length N = N1 + N2", String.valueOf(N));
            addRow(halsteadGrid, 5, "Vocabulary μ = μ1 + μ2", String.valueOf(mu));
            addRow(halsteadGrid, 6, "Volume V = N×log2(μ)", String.format(Locale.ROOT, "%.4f", V));
            addRow(halsteadGrid, 7, "Estimated length", String.format(Locale.ROOT, "%.4f", estLen));
            addRow(halsteadGrid, 8, "n2* (approx param names)", String.valueOf(n2Star));
            addRow(halsteadGrid, 9, "Potential volume V*", String.format(Locale.ROOT, "%.4f", VStar));
            addRow(halsteadGrid, 10, "Level L = V*/V", String.format(Locale.ROOT, "%.4f", L));
            addRow(halsteadGrid, 11, "Difficulty D = 1/L", String.format(Locale.ROOT, "%.4f", D));
            addRow(halsteadGrid, 12, "Estimated level L'", String.format(Locale.ROOT, "%.6f", Lp));
            addRow(halsteadGrid, 13, "Estimated difficulty D'", String.format(Locale.ROOT, "%.4f", Dp));
            addRow(halsteadGrid, 14, "Effort E = V/L'", String.format(Locale.ROOT, "%.4f", E));

            suppressCocomoUpdates = true;
            cocomoKloc.setText(String.format(Locale.ROOT, "%.3f", nonBlankLoc / 1000.0));
            suppressCocomoUpdates = false;
            updateCocomoGrid();
        }

        private Pane buildCocomoPane() {
            Label modeLabel = new Label("Mode:");
            Label klocLabel = new Label("KLOC:");

            HBox controls = new HBox(10, modeLabel, cocomoMode, klocLabel, cocomoKloc);
            controls.setPadding(new Insets(12, 12, 0, 12));
            HBox.setHgrow(cocomoMode, Priority.ALWAYS);

            VBox box = new VBox(8, controls, wrapGrid(cocomoGrid));
            VBox.setVgrow(box.getChildren().get(1), Priority.ALWAYS);
            return box;
        }

        private void updateCocomoGrid() {
            cocomoGrid.getChildren().clear();

            CocomoBasic.Mode mode = cocomoMode.getValue();
            if (mode == null) {
                mode = CocomoBasic.Mode.ORGANIC;
            }

            String raw = cocomoKloc.getText() == null ? "" : cocomoKloc.getText().trim();
            double kloc;
            try {
                kloc = raw.isEmpty() ? 0.0 : Double.parseDouble(raw);
            } catch (NumberFormatException nfe) {
                addRow(cocomoGrid, 0, "KLOC", "Invalid number");
                addRow(cocomoGrid, 1, "Tip", "Use a dot (.) for decimals, e.g. 12.345");
                return;
            }

            if (kloc < 0.0 || Double.isNaN(kloc) || Double.isInfinite(kloc)) {
                addRow(cocomoGrid, 0, "KLOC", "Must be a finite number >= 0");
                return;
            }

            CocomoBasic.Estimate est = CocomoBasic.estimate(mode, kloc);
            int row = 0;
            if (lastNonBlankLoc > 0) {
                addRow(cocomoGrid, row++, "Derived KLOC (non-blank LOC/1000)",
                        String.format(Locale.ROOT, "%d / 1000 = %.3f", lastNonBlankLoc, lastNonBlankLoc / 1000.0));
            }
            addRow(cocomoGrid, row++, "Mode", mode.displayName);
            addRow(cocomoGrid, row++, "KLOC (input)", String.format(Locale.ROOT, "%.3f", kloc));
            addRow(cocomoGrid, row++, "Effort E (person-months)",
                    String.format(Locale.ROOT, "%.4f", est.effortPersonMonths()));
            addRow(cocomoGrid, row++, "Tdev (months)", String.format(Locale.ROOT, "%.4f", est.tdevMonths()));
        }

        private static void configureGrid(GridPane g) {
            g.setHgap(12);
            g.setVgap(8);
            g.setPadding(new Insets(12));
            ColumnConstraints c1 = new ColumnConstraints();
            c1.setHgrow(Priority.NEVER);
            ColumnConstraints c2 = new ColumnConstraints();
            c2.setHgrow(Priority.ALWAYS);
            g.getColumnConstraints().addAll(c1, c2);
        }

        private static ScrollPane wrapGrid(GridPane grid) {
            ScrollPane sp = new ScrollPane(grid);
            sp.setFitToWidth(true);
            return sp;
        }

        private static void addRow(GridPane g, int row, String key, String value) {
            Label k = new Label(key);
            k.setStyle("-fx-font-weight: bold;");
            Label v = new Label(value);
            v.setWrapText(true);
            g.add(k, 0, row);
            g.add(v, 1, row);
        }
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Metrics Studio");

        Label selectedFolderLabel = new Label("No folder selected");
        selectedFolderLabel.setWrapText(true);

        Button chooseFolderButton = new Button("Select Project Folder");
        Button analyzeButton = new Button("Analyze");
        analyzeButton.setDisable(true);

        ProgressIndicator progress = new ProgressIndicator();
        progress.setVisible(false);
        progress.setPrefSize(18, 18);

        ResultTabs resultTabs = new ResultTabs();

        HBox topRow = new HBox(10, chooseFolderButton, analyzeButton, progress);
        topRow.setPadding(new Insets(10));

        VBox root = new VBox(8,
                topRow,
                new Separator(),
                new VBox(6, new Label("Selected folder:"), selectedFolderLabel),
                new Separator(),
                resultTabs.tabs);
        root.setPadding(new Insets(10));
        VBox.setVgrow(resultTabs.tabs, Priority.ALWAYS);

        final Path[] selectedPath = new Path[1];

        chooseFolderButton.setOnAction(evt -> {
            DirectoryChooser chooser = new DirectoryChooser();
            chooser.setTitle("Select Java Project Folder");
            File folder = chooser.showDialog(primaryStage);
            if (folder == null)
                return;

            selectedPath[0] = folder.toPath();
            selectedFolderLabel.setText(folder.getAbsolutePath());
            analyzeButton.setDisable(false);
            resultTabs.clear();
        });

        analyzeButton.setOnAction(evt -> {
            Path rootPath = selectedPath[0];
            if (rootPath == null)
                return;

            analyzeButton.setDisable(true);
            chooseFolderButton.setDisable(true);
            progress.setVisible(true);
            resultTabs.clear();
            resultTabs.summaryText.setText("Analyzing...\n");

            Thread worker = new Thread(() -> {
                try {
                    ProjectAnalyzer analyzer = new ProjectAnalyzer();
                    AnalysisResult result = analyzer.analyze(rootPath);

                    Platform.runLater(() -> {
                        resultTabs.setResult(result);
                        progress.setVisible(false);
                        analyzeButton.setDisable(false);
                        chooseFolderButton.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        resultTabs.showError(ex.getMessage());
                        progress.setVisible(false);
                        analyzeButton.setDisable(false);
                        chooseFolderButton.setDisable(false);
                    });
                }
            }, "analysis-worker");
            worker.setDaemon(true);
            worker.start();
        });

        Scene scene = new Scene(root, 900, 600);
        primaryStage.setScene(scene);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
