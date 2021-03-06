package com.jtulayan.ui.javafx;

import com.jtulayan.main.Pathplanner;
import com.jtulayan.ui.javafx.factory.AlertFactory;
import com.jtulayan.ui.javafx.factory.DialogFactory;
import com.jtulayan.ui.javafx.factory.SeriesFactory;
import com.jtulayan.util.Mathf;
import jaci.pathfinder.Pathfinder;
import jaci.pathfinder.Trajectory;
import jaci.pathfinder.Waypoint;
import javafx.beans.value.ObservableValue;
import javafx.beans.value.ObservableValueBase;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Point2D;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TextField;
import javafx.scene.control.cell.TextFieldTableCell;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Pane;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.util.Callback;
import javafx.util.converter.DoubleStringConverter;

import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class MPGenController {
    private Pathplanner backend;

    @FXML
    private Pane root;

    @FXML
    private TextField
        txtTimeStep,
        txtVelocity,
        txtAcceleration,
        txtJerk,
        txtWheelBaseW,
        txtWheelBaseD;

    @FXML
    private Label
        lblWheelBaseD;

    @FXML
    private TableView<Waypoint> tblWaypoints;

    @FXML
    private LineChart<Double, Double>
        chtPosition,
        chtVelocity;

    @FXML
    private NumberAxis
        axisPosX,
        axisPosY,
        axisTime,
        axisVel;

    @FXML
    private TableColumn<Waypoint, Double>
            colWaypointX,
            colWaypointY,
            colWaypointAngle;

    @FXML
    private MenuItem
            mnuOpen,
            mnuFileNew,
            mnuFileSave,
            mnuFileSaveAs,
            mnuFileExport,
            mnuFileExit,
            mnuHelpAbout;

    @FXML
    private ChoiceBox
            choDriveBase,
            choFitMethod,
            choUnits;

    @FXML
    private Button
            btnAddPoint,
            btnClearPoints,
            btnDelete;

    private ObservableList<Waypoint> waypointsList;

    private Properties properties;

    private File workingDirectory;

    private boolean currentTrajValid = false;

    @FXML
    public void initialize() {
        backend = new Pathplanner();
        properties = PropWrapper.getProperties();

        workingDirectory = new File(properties.getProperty("file.workingDir", System.getProperty("user.dir")));

        btnDelete.setDisable(true);

        choDriveBase.setItems(FXCollections.observableArrayList("Tank", "Swerve"));
        choDriveBase.setValue(choDriveBase.getItems().get(0));
        choDriveBase.getSelectionModel().selectedItemProperty().addListener(this::updateDriveBase);

        choFitMethod.setItems(FXCollections.observableArrayList("Cubic", "Quintic"));
        choFitMethod.setValue(choFitMethod.getItems().get(0));
        choFitMethod.getSelectionModel().selectedItemProperty().addListener(this::updateFitMethod);

        choUnits.setItems(FXCollections.observableArrayList("Imperial", "Metric"));
        choUnits.setValue(choUnits.getItems().get(0));
        choUnits.getSelectionModel().selectedItemProperty().addListener(this::updateUnits);

        Callback<TableColumn<Waypoint, Double>, TableCell<Waypoint, Double>> doubleCallback =
            (TableColumn<Waypoint, Double> param) -> {
                TextFieldTableCell<Waypoint, Double> cell = new TextFieldTableCell<>();

                cell.setConverter(new DoubleStringConverter());

                return cell;
        };

        EventHandler<TableColumn.CellEditEvent<Waypoint, Double>> editHandler =
            (TableColumn.CellEditEvent<Waypoint, Double> t) -> {
                Waypoint
                    curWaypoint = t.getRowValue(),
                    history = new Waypoint(curWaypoint.x, curWaypoint.y, curWaypoint.angle);

                if (t.getTableColumn() == colWaypointAngle)
                    curWaypoint.angle = Pathfinder.d2r(t.getNewValue());
                else if (t.getTableColumn() == colWaypointY)
                    curWaypoint.y = t.getNewValue();
                else
                    curWaypoint.x = t.getNewValue();

                // If the point is invalid
                if (!generateTrajectories()) {
                    curWaypoint.x = history.x;
                    curWaypoint.y = history.y;
                    curWaypoint.angle = history.angle;
                }
        };

        txtTimeStep.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtVelocity.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtAcceleration.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtJerk.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtWheelBaseW.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));
        txtWheelBaseD.setTextFormatter(new TextFormatter<>(new DoubleStringConverter()));

        txtTimeStep.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtTimeStep.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "0.02";
                    txtTimeStep.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtTimeStep.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtVelocity.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtVelocity.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "4.0";
                    txtVelocity.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtVelocity.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtAcceleration.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtAcceleration.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "3.0";
                    txtAcceleration.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtAcceleration.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtJerk.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtJerk.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "60.0";
                    txtJerk.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtJerk.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtWheelBaseW.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtWheelBaseW.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "1.464";
                    txtWheelBaseW.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtWheelBaseW.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        txtWheelBaseD.focusedProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue) { // On unfocus
                String val = txtWheelBaseD.getText().trim();
                double d = 0;

                if (val.isEmpty()) {
                    val = "1.464";
                    txtWheelBaseD.setText(val);
                } else {
                    d = Double.parseDouble(val);
                    if (d != 0) {
                        txtWheelBaseD.setText("" + Math.abs(d));
                        generateTrajectories();
                    }
                }
            }
        });

        colWaypointX.setCellFactory(doubleCallback);
        colWaypointY.setCellFactory(doubleCallback);
        colWaypointAngle.setCellFactory(doubleCallback);

        colWaypointX.setOnEditCommit(editHandler);
        colWaypointY.setOnEditCommit(editHandler);
        colWaypointAngle.setOnEditCommit(editHandler);

        colWaypointX.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
            new ObservableValueBase<Double>() {
                @Override
                public Double getValue() {
                    return d.getValue().x;
                }
            }
        );

        colWaypointY.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
                new ObservableValueBase<Double>() {
                    @Override
                    public Double getValue() {
                        return d.getValue().y;
                    }
                }
        );

        colWaypointAngle.setCellValueFactory((TableColumn.CellDataFeatures<Waypoint, Double> d) ->
            new ObservableValueBase<Double>() {
                @Override
                public Double getValue() {
                    return Mathf.round(Pathfinder.r2d(d.getValue().angle), 2);
                }
            }
        );

        waypointsList = FXCollections.observableList(backend.getWaypointsList());
        waypointsList.addListener((ListChangeListener<Waypoint>) c -> {
            btnClearPoints.setDisable(waypointsList.size() == 0);

            currentTrajValid = generateTrajectories();

            tblWaypoints.getSelectionModel().clearSelection();
        });

        tblWaypoints.setItems(waypointsList);
        tblWaypoints.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        tblWaypoints.getSelectionModel().selectedIndexProperty().addListener((observable, oldValue, newValue) -> {
            List<Integer> selectedIndicies = tblWaypoints.getSelectionModel().getSelectedIndices();
            String srcDisplayStr = properties.getProperty("ui.sourceDisplay", "2");
            int sourceDisplay = Integer.parseInt(srcDisplayStr);

            btnDelete.setDisable(tblWaypoints.getSelectionModel().getSelectedIndices().get(0) == -1);

            // Highlight selected waypoints if available
            if (sourceDisplay > 0 && !waypointsList.isEmpty())
                highlightPoints(selectedIndicies);
        });

        tblWaypoints.setOnKeyPressed(event -> {
            KeyCode code = event.getCode();
            if (code.equals(KeyCode.INSERT)) {
                showAddPointDialog();
            } else if (code.equals(KeyCode.DELETE)) {
                deletePoints();
            }
        });

        updateOverlayImg();
        updateFrontend();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            properties.setProperty("file.workingDir", workingDirectory.getAbsolutePath());
            try {
                PropWrapper.storeProperties();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }));
    }

    @FXML
    private void showSettingsDialog() {
        Dialog<Properties> settingsDialog = DialogFactory.createSettingsDialog();
        Optional<Properties> result = null;

        // Wait for the result
        result = settingsDialog.showAndWait();

        result.ifPresent((Properties p) -> {
            try {
                properties.clear();
                properties.putAll(p);

                updateOverlayImg();
                repopulatePosChart();

                PropWrapper.storeProperties();
            } catch (IOException e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
            }
        });
    }

    @FXML
    private void deletePoints() {
        List<Integer> selectedIndicies = tblWaypoints.getSelectionModel().getSelectedIndices();

        int firstIndex = selectedIndicies.get(0);
        int lastIndex = selectedIndicies.get(selectedIndicies.size() - 1);

        waypointsList.remove(firstIndex, lastIndex + 1);
    }

    @FXML
    private void showSaveAsDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(workingDirectory);
        fileChooser.setTitle("Save As");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Extensive Markup Language", "*.xml")
        );

        File result = fileChooser.showSaveDialog(root.getScene().getWindow());

        if (result != null)
            try {
                workingDirectory = result.getParentFile();

                backend.saveProjectAs(result);

                mnuFileSave.setDisable(false);
            } catch (Exception e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
        }
    }

    @FXML
    private void showOpenDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(workingDirectory);
        fileChooser.setTitle("Open Project");
        fileChooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("Extensive Markup Language", "*.xml")
        );

        File result = fileChooser.showOpenDialog(root.getScene().getWindow());

        if (result != null) {
            try {
                workingDirectory = result.getParentFile();
                backend.loadProject(result);

                updateFrontend();
                updateChartAxes();

                generateTrajectories();

                mnuFileSave.setDisable(false);
            } catch (Exception e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
            }
        }
    }

    @FXML
    private void save() {
        updateBackend();

        try {
            backend.saveWorkingProject();
        } catch (Exception e) {
            Alert alert = AlertFactory.createExceptionAlert(e);

            alert.showAndWait();
        }
    }

    @FXML
    private void showExportDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(new File(System.getProperty("user.dir")));
        fileChooser.setTitle("Export");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Comma Separated Values", "*.csv"),
                new FileChooser.ExtensionFilter("Binary Trajectory File", "*.traj")
        );

        File result = fileChooser.showSaveDialog(root.getScene().getWindow());

        if (result != null && generateTrajectories()) {
            String parentPath = result.getAbsolutePath(), ext = parentPath.substring(parentPath.lastIndexOf("."));
            parentPath = parentPath.substring(0, parentPath.lastIndexOf(ext));

            try {
                backend.exportTrajectories(new File(parentPath), ext);
            } catch (Pathfinder.GenerationException e) {
                Alert alert = AlertFactory.createExceptionAlert(e, "Invalid Trajectory!");

                alert.showAndWait();
            }
        }
    }

    @FXML
    private void showImportDialog() {
        FileChooser fileChooser = new FileChooser();

        fileChooser.setInitialDirectory(workingDirectory);
        fileChooser.setTitle("Import");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("Vannaka Properties File", "*.bot")
        );

        File result = fileChooser.showOpenDialog(root.getScene().getWindow());

        if (result != null) {
            Dialog<Pathplanner.Units> unitsSelector = new Dialog<>();
            Optional<Pathplanner.Units> unitsResult = null;
            GridPane grid = new GridPane();
            ToggleGroup radGroup = new ToggleGroup();
            RadioButton
                radImperial = new RadioButton("Imperial (ft)"),
                radMetric = new RadioButton("Metric (m)");

            // Reset working directory
            workingDirectory = result.getParentFile();

            // Some header stuff
            unitsSelector.setTitle("Select Units");
            unitsSelector.setHeaderText("Select the distance units being used");

            // Some other UI stuff
            grid.setVgap(10);
            grid.setPadding(new Insets(20, 150, 10, 10));

            grid.add(radImperial, 0, 0);
            grid.add(radMetric, 0, 1);

            radImperial.setToggleGroup(radGroup);
            radImperial.selectedProperty().set(true);
            radMetric.setToggleGroup(radGroup);

            unitsSelector.getDialogPane().setContent(grid);

            // Add all buttons
            unitsSelector.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
            unitsSelector.setResultConverter(buttonType -> {
                if (buttonType.getButtonData() == ButtonBar.ButtonData.OK_DONE) {
                    if (radMetric.selectedProperty().getValue())
                        return Pathplanner.Units.METRIC;
                    else
                        return Pathplanner.Units.IMPERIAL;
                }

                return null;
            });

            unitsResult = unitsSelector.showAndWait();

            unitsResult.ifPresent(u -> {
                backend.clearPoints();
                try {
                    backend.importBotFile(result, u);

                    updateFrontend();
                    generateTrajectories();

                    mnuFileSave.setDisable(!backend.hasWorkingProject());
                } catch (Exception e) {
                    Alert alert = AlertFactory.createExceptionAlert(e);

                    alert.showAndWait();
                }
            });
        }
    }

    @FXML
    private void showAddPointDialog() {
        Dialog<Waypoint> waypointDialog = DialogFactory.createWaypointDialog();
        Optional<Waypoint> result = null;

        // Wait for the result
        result = waypointDialog.showAndWait();

        result.ifPresent((Waypoint w) -> {
            waypointsList.add(w);
            if (!currentTrajValid)
                waypointsList.remove(w);
        });
    }

    @FXML
    private void addPointOnClick(MouseEvent event) {
        if (event.getButton() == MouseButton.PRIMARY) {
            boolean addWaypointOnClick = Boolean.parseBoolean(
                    properties.getProperty("ui.addWaypointOnClick", "false")
            );

            if (addWaypointOnClick) {
                Point2D mouseSceneCoords = new Point2D(event.getSceneX(), event.getSceneY());
                double xLocal = axisPosX.sceneToLocal(mouseSceneCoords).getX();
                double yLocal = axisPosY.sceneToLocal(mouseSceneCoords).getY();

                double x = Mathf.round(axisPosX.getValueForDisplay(xLocal).doubleValue(), 2);
                double y = Mathf.round(axisPosY.getValueForDisplay(yLocal).doubleValue(), 2);
                double angle = 0;

                if (!waypointsList.isEmpty()) {
                    Waypoint prev = waypointsList.get(waypointsList.size() - 1);
                    angle = Pathfinder.r2d(Math.atan2(y - prev.y, x - prev.x));
                    angle = Pathfinder.d2r(Mathf.round(angle, 45.0));
                }

                if (x >= axisPosX.getLowerBound() && x <= axisPosX.getUpperBound() &&
                        y >= axisPosY.getLowerBound() && y <= axisPosY.getUpperBound()) {
                    waypointsList.add(new Waypoint(x, y, angle));
                    if (!currentTrajValid)
                        waypointsList.remove(waypointsList.size() - 1);
                }

            } else {
                event.consume();
            }
        } else {
            event.consume();
        }
    }

    @FXML
    private void showClearPointsDialog() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle("Clear Points");
        alert.setHeaderText("Clear All Points?");
        alert.setContentText("Are you sure you want to clear all points?");

        Optional<ButtonType> result = alert.showAndWait();

        result.ifPresent((ButtonType t) -> {
            if (t.getButtonData() == ButtonBar.ButtonData.OK_DONE)
                waypointsList.clear();
        });
    }

    @FXML
    private void resetData() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);

        alert.setTitle("Create New Project?");
        alert.setHeaderText("Confirm Reset");
        alert.setContentText("Are you sure you want to reset all data? Have you saved?");

        Optional<ButtonType> result = alert.showAndWait();

        result.ifPresent((ButtonType t) -> {
            if (t == ButtonType.OK) {
                backend.clearWorkingFiles();
                backend.resetValues();

                updateFrontend();
                waypointsList.clear();
                updateChartAxes();

                mnuFileSave.setDisable(true);
            }
        });
    }

    @FXML
    private void validateFieldEdit(ActionEvent event) {
        String val = ((TextField) event.getSource()).getText().trim();
        double d = 0;
        boolean validInput = true;

        try {
            d = Double.parseDouble(val);

            validInput = d > 0;
        } catch (NumberFormatException e) {
            validInput = false;
        } finally {
            if (validInput)
                generateTrajectories();
            else
                Toolkit.getDefaultToolkit().beep();
        }
    }

    @FXML
    private void updateBackend() {
        backend.setTimeStep(Double.parseDouble(txtTimeStep.getText().trim()));
        backend.setVelocity(Double.parseDouble(txtVelocity.getText().trim()));
        backend.setAcceleration(Double.parseDouble(txtAcceleration.getText().trim()));
        backend.setJerk(Double.parseDouble(txtJerk.getText().trim()));
        backend.setWheelBaseW(Double.parseDouble(txtWheelBaseW.getText().trim()));
        backend.setWheelBaseD(Double.parseDouble(txtWheelBaseD.getText().trim()));
    }

    /**
     * Updates all fields and views in the UI.
     */
    private void updateFrontend() {
        txtTimeStep.setText("" + backend.getTimeStep());
        txtVelocity.setText("" + backend.getVelocity());
        txtAcceleration.setText("" + backend.getAcceleration());
        txtJerk.setText("" + backend.getJerk());
        txtWheelBaseW.setText("" + backend.getWheelBaseW());
        txtWheelBaseD.setText("" + backend.getWheelBaseD());

        choDriveBase.setValue(choDriveBase.getItems().get(backend.getDriveBase().ordinal()));
        choFitMethod.setValue(choFitMethod.getItems().get(backend.getFitMethod().ordinal()));
        choUnits.setValue(choUnits.getItems().get(backend.getUnits().ordinal()));

        refreshWaypointTable();
    }

    @FXML
    private void openAboutDialog() {
        Dialog<Boolean> aboutDialog = DialogFactory.createAboutDialog();

        aboutDialog.showAndWait();
    }

    @FXML
    private void exit() {
        System.exit(0);
    }

    private boolean generateTrajectories() {
        updateBackend();

        if (waypointsList.size() > 1) {
            try {
                backend.updateTrajectories();
            } catch (Pathfinder.GenerationException e) {
                Toolkit.getDefaultToolkit().beep();

                Alert alert = new Alert(Alert.AlertType.WARNING);

                alert.setTitle("Invalid Trajectory");
                alert.setHeaderText("Invalid trajectory point!");
                alert.setContentText("The trajectory point is invalid because one of the waypoints is invalid! " +
                        "Please check the waypoints and try again.");
                alert.showAndWait();

                return false;
            }
        }

        repopulatePosChart();
        repopulateVelChart();

        return true;
    }

    private void updateDriveBase(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        Pathplanner.DriveBase db = Pathplanner.DriveBase.valueOf(choice);

        backend.setDriveBase(db);

        txtWheelBaseD.setDisable(db == Pathplanner.DriveBase.TANK);
        lblWheelBaseD.setDisable(db == Pathplanner.DriveBase.TANK);

        generateTrajectories();
    }

    private void updateFitMethod(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        Trajectory.FitMethod fm = Trajectory.FitMethod.valueOf("HERMITE_" + choice);

        backend.setFitMethod(fm);

        generateTrajectories();
    }

    private void updateUnits(ObservableValue<String> observable, Object oldValue, Object newValue) {
        String choice = ((String) newValue).toUpperCase();
        Pathplanner.Units
            u = Pathplanner.Units.valueOf(choice),
            oldUnits = backend.getUnits();

        backend.setUnits(u);

        if (u != oldUnits) {
            updateFrontend();
            refreshWaypointTable();
            repopulatePosChart();
            repopulateVelChart();
        }

        updateChartAxes();
    }

    private void repopulatePosChart() {
        ObservableList<XYChart.Series<Double, Double>> posData = chtPosition.getData();
        String srcDisplayStr = properties.getProperty("ui.sourceDisplay", "2");
        int sourceDisplay = Integer.parseInt(srcDisplayStr);

        String colorSourceTraj = properties.getProperty("ui.colorSourceTrajectory", "orange");
        String colorTankTraj = properties.getProperty("ui.colorTankTrajectory", "magenta");

        // Clear data from position graph
        posData.clear();

        // Get theme stuff
        if (colorSourceTraj.indexOf("0x") == 0)
            colorSourceTraj = Mathf.toWeb(colorSourceTraj);

        if (colorTankTraj.indexOf("0x") == 0)
            colorTankTraj = Mathf.toWeb(colorTankTraj);

        if (!waypointsList.isEmpty()) {
            switch (sourceDisplay) {
                case 3: // Robot base
                    XYChart.Series<Double, Double> robotBoxSeries =
                            SeriesFactory.buildWaypointsSeries(waypointsList.toArray(new Waypoint[1]));

                    double xMin = axisPosX.getDisplayPosition(axisPosX.getLowerBound());
                    double xMax = axisPosX.getDisplayPosition(axisPosX.getUpperBound());
                    double yMin = axisPosY.getDisplayPosition(axisPosY.getLowerBound());
                    double yMax = axisPosY.getDisplayPosition(axisPosY.getUpperBound());
                    double widthScale = (xMax - xMin) / axisPosX.getUpperBound();
                    double heightScale = (yMax - yMin) / axisPosY.getUpperBound();
                    double width = widthScale * 1.41;
                    double height = heightScale * 1.41;

                    posData.add(robotBoxSeries);
                    robotBoxSeries.getNode().setStyle("-fx-stroke: transparent");

                    for (int i = 0; i < waypointsList.size(); i++) {
                        Waypoint curPoint = waypointsList.get(i);
                        XYChart.Data<Double, Double> data = robotBoxSeries.getData().get(i);
                        Rectangle robotBox = new Rectangle();

                        robotBox.setWidth(width);
                        robotBox.setHeight(height);
                        robotBox.setArcWidth(0.5 * widthScale);
                        robotBox.setArcHeight(0.5 * heightScale);
                        robotBox.setStyle("-fx-color: white");
                        robotBox.setRotate(Pathfinder.r2d(curPoint.angle));
                        data.setNode(robotBox);
                    }
                case 2: // Source Trajectory
                    if (waypointsList.size() > 1) {
                        XYChart.Series<Double, Double> sourceSeries =
                                SeriesFactory.buildPositionSeries(backend.getSourceTrajectory());

                        posData.add(sourceSeries);
                        sourceSeries.getNode().setStyle("-fx-stroke: " + colorSourceTraj);

                        for (XYChart.Data<Double, Double> data : sourceSeries.getData())
                            data.getNode().setVisible(false);
                    }
                case 1: // Waypoints
                    XYChart.Series<Double, Double> waypointSeries =
                            SeriesFactory.buildWaypointsSeries(waypointsList.toArray(new Waypoint[1]));
                    int dataSize = posData.size();

                    posData.add(0, waypointSeries);
                    waypointSeries.getNode().setStyle("-fx-stroke: transparent");

                    highlightPoints(waypointSeries, tblWaypoints.getSelectionModel().getSelectedIndices());
                default: // Drive train trajectories
                    if (waypointsList.size() > 1) {
                        XYChart.Series<Double, Double>
                                flSeries = SeriesFactory.buildPositionSeries(backend.getFrontLeftTrajectory()),
                                frSeries = SeriesFactory.buildPositionSeries(backend.getFrontRightTrajectory());

                        if (backend.getDriveBase() == Pathplanner.DriveBase.SWERVE) {
                            XYChart.Series<Double, Double>
                                    blSeries = SeriesFactory.buildPositionSeries(backend.getBackLeftTrajectory()),
                                    brSeries = SeriesFactory.buildPositionSeries(backend.getBackRightTrajectory());

                            posData.add(0, blSeries);
                            posData.add(0, brSeries);
                            posData.add(0, flSeries);
                            posData.add(0, frSeries);

                            flSeries.getNode().setStyle("-fx-stroke: red");
                            frSeries.getNode().setStyle("-fx-stroke: red");
                            blSeries.getNode().setStyle("-fx-stroke: blue");
                            brSeries.getNode().setStyle("-fx-stroke: blue");

                            for (XYChart.Data<Double, Double> data : blSeries.getData())
                                data.getNode().setVisible(false);

                            for (XYChart.Data<Double, Double> data : brSeries.getData())
                                data.getNode().setVisible(false);
                        } else {
                            posData.add(0, flSeries);
                            posData.add(0, frSeries);

                            flSeries.getNode().setStyle("-fx-stroke: " + colorTankTraj);
                            frSeries.getNode().setStyle("-fx-stroke: " + colorTankTraj);
                        }

                        for (XYChart.Data<Double, Double> data : flSeries.getData())
                            data.getNode().setVisible(false);

                        for (XYChart.Data<Double, Double> data : frSeries.getData())
                            data.getNode().setVisible(false);
                    }
            }
        }
    }

    private void repopulateVelChart() {
        // Clear data from velocity graph
        chtVelocity.getData().clear();

        if (waypointsList.size() > 1) {
            XYChart.Series<Double, Double>
                    flSeries = SeriesFactory.buildVelocitySeries(backend.getFrontLeftTrajectory()),
                    frSeries = SeriesFactory.buildVelocitySeries(backend.getFrontRightTrajectory());

            chtVelocity.getData().addAll(flSeries, frSeries);

            if (backend.getDriveBase() == Pathplanner.DriveBase.SWERVE) {
                XYChart.Series<Double, Double>
                        blSeries = SeriesFactory.buildVelocitySeries(backend.getBackLeftTrajectory()),
                        brSeries = SeriesFactory.buildVelocitySeries(backend.getBackRightTrajectory());

                chtVelocity.getData().addAll(blSeries, brSeries);

                flSeries.setName("Front Left Trajectory");
                frSeries.setName("Front Right Trajectory");
                blSeries.setName("Back Left Trajectory");
                brSeries.setName("Back Right Trajectory");
            } else {
                flSeries.setName("Left Trajectory");
                frSeries.setName("Right Trajectory");
            }
        }
    }

    /**
     * Highlights position waypoints given a list of selected indices
     *
     * @param selectedIndicies the list of selected indices
     */
    private void highlightPoints(List<Integer> selectedIndicies) {
        int dataSize = chtPosition.getData().size();
        XYChart.Series<Double, Double> points = chtPosition.getData().get(dataSize - 1);

        highlightPoints(points, selectedIndicies);
    }

    /**
     * Highlights points given a list of selected indices
     *
     * @param points the series of points to highlight
     * @param selectedIndicies the list of selected indices
     */
    private void highlightPoints(
        XYChart.Series<Double, Double> points,
        List<Integer> selectedIndicies) {

        List<XYChart.Data<Double, Double>> pointsList = points.getData();

        String
            colorSourceTraj = properties.getProperty(
                "ui.colorSourceTrajectory",
                "orange"),
            colorHighlight = properties.getProperty(
                "ui.colorWaypointHighlight",
                "green");

        if (colorSourceTraj.indexOf("0x") == 0)
            colorSourceTraj = Mathf.toWeb(colorSourceTraj);

        if (colorHighlight.indexOf("0x") == 0)
            colorHighlight = Mathf.toWeb(colorHighlight);

        for (int i = 0; i < pointsList.size(); i++) {
            boolean selected = false;
            XYChart.Data<Double, Double> data = pointsList.get(i);
            // Figure out the current point is one of the selected elements in the table
            for (int ind : selectedIndicies) {
                if (i == ind) {
                    selected = true;
                    break;
                }
            }

            // Highlight or don't highlight the node
            data.getNode().setStyle(String.format("-fx-background-color: %s, white",
                    selected ? colorHighlight : colorSourceTraj
            ));
        }
    }

    private void updateOverlayImg() {
        String dir = properties.getProperty("ui.overlayDir", "");

        if (!dir.isEmpty()) {
            try {
                File img = new File(dir);
                chtPosition.lookup(".chart-plot-background").setStyle(
                    "-fx-background-image: url(" + img.toURI().toString() + ");" +
                    "-fx-background-size: stretch;" +
                    "-fx-background-position: top right;" +
                    "-fx-background-repeat: no-repeat;"
                );
            } catch (Exception e) {
                Alert alert = AlertFactory.createExceptionAlert(e);

                alert.showAndWait();
            }
        }
    }

    private void updateChartAxes() {
        switch (backend.getUnits()) {
            case IMPERIAL:
                axisPosX.setUpperBound(32);
                axisPosX.setTickUnit(1);
                axisPosX.setLabel("X-Position (ft)");
                axisPosY.setUpperBound(27);
                axisPosY.setTickUnit(1);
                axisPosY.setLabel("Y-Position (ft)");

                axisVel.setLabel("Velocity (ft/s)");

                break;
            case METRIC:
                axisPosX.setUpperBound(10);
                axisPosX.setTickUnit(0.5);
                axisPosX.setLabel("X-Position (m)");
                axisPosY.setUpperBound(8.23);
                axisPosY.setTickUnit(0.5);
                axisPosY.setLabel("Y-Position (m)");

                axisVel.setLabel("Velocity (m/s)");

                break;
            default:
                backend.setUnits(Pathplanner.Units.IMPERIAL);
                updateChartAxes();
        }
    }

    /**
     * Refreshes the waypoints table by clearing the waypoint list and repopulating it.
     */
    private void refreshWaypointTable() {
        // Bad way to update the waypoint list...
        // However, TableView.refresh() is apparently borked?
        List<Waypoint> tmp = new ArrayList<>(backend.getWaypointsList());
        waypointsList.clear();
        waypointsList.addAll(tmp);
    }
}
