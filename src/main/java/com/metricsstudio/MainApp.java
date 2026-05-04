package com.metricsstudio;

import com.metricsstudio.analysis.AnalysisResult;
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

public class MainApp extends Application {

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

        TextArea output = new TextArea();
        output.setEditable(false);
        output.setWrapText(false);

        HBox topRow = new HBox(10, chooseFolderButton, analyzeButton, progress);
        topRow.setPadding(new Insets(10));

        VBox root = new VBox(8,
                topRow,
                new Separator(),
                new VBox(6, new Label("Selected folder:"), selectedFolderLabel),
                new Separator(),
                new Label("Results:"),
                output);
        root.setPadding(new Insets(10));
        VBox.setVgrow(output, Priority.ALWAYS);

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
            output.clear();
        });

        analyzeButton.setOnAction(evt -> {
            Path rootPath = selectedPath[0];
            if (rootPath == null)
                return;

            analyzeButton.setDisable(true);
            chooseFolderButton.setDisable(true);
            progress.setVisible(true);
            output.setText("Analyzing...\n");

            Thread worker = new Thread(() -> {
                try {
                    ProjectAnalyzer analyzer = new ProjectAnalyzer();
                    AnalysisResult result = analyzer.analyze(rootPath);

                    Platform.runLater(() -> {
                        output.setText(result.toPrettyText());
                        progress.setVisible(false);
                        analyzeButton.setDisable(false);
                        chooseFolderButton.setDisable(false);
                    });
                } catch (Exception ex) {
                    Platform.runLater(() -> {
                        output.setText("Error: " + ex.getMessage());
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
