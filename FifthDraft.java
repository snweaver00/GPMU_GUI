import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Pos;
import javafx.stage.Stage;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.control.Label;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.io.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import java.net.URL;
import java.net.HttpURLConnection;


/*
    Senior Design Draft 5
    v1.0.5
    Author: Samantha Weaver
    Date: 12/3/25
 */


public class FifthDraft extends Application {

    private Label currentLabel, voltageLabel, powerLabel;
    private ScheduledExecutorService executor;
    private Stage primaryStage;

    //Backend's IP address
    private static final String BACKEND_BASE_URL = "http://10.173.158.2:4020/api/v1";

    private LineChart<Number, Number> chart;
    private XYChart.Series<Number, Number> seriesCurrent;
    private XYChart.Series<Number, Number> seriesVoltage;
    private XYChart.Series<Number, Number> seriesPower;
    private long sampleIndex = 0;

    //Helper class to retrieve readings
    private static class PowerSample {
        final double current;
        final double voltage;
        final double power;

        PowerSample(double current, double voltage, double power) {
            this.current = current;
            this.voltage = voltage;
            this.power = power;
        }
    }

    @Override
    public void start(Stage stage) {

        this.primaryStage = stage;
        primaryStage.setTitle("CPEodesic");
        Scene scene = welcomeScene();
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());
        primaryStage.setScene(scene);
        primaryStage.setResizable(false);
        primaryStage.show();
    }

    //Welcome Scene
    private Scene welcomeScene(){

        Image image = new Image("file:/Users/samanthaweaver/Documents/cpeodesic.png");
        ImageView img = new ImageView(image);


        Label welcomeLabel = new Label("Welcome!");

        /*
           Start button that takes you to the next scene
           Clicking on the button will take you to the next window with a device list
           The button is set -50 on the y-axis
         */
        Button startButton = new Button("Start");
        startButton.setTranslateY(50);

        startButton.setOnAction(e -> primaryStage.setScene(deviceSelectionScene()));

        VBox box = new VBox(15, img, welcomeLabel, startButton);
        box.setAlignment(Pos.CENTER);

        return new Scene(box, 1200, 800);
    }

    //Device Selection Scene
    private Scene deviceSelectionScene() {

        Label selectLabel = new Label("Select your Device:");

        Button device1 = new Button("Device 1");

        Button device2 = new Button("Device 2");

        Button device3 = new Button("Device 3");

        device1.setOnAction(e -> primaryStage.setScene(latencyScene("Device 1")));
        device2.setOnAction(e -> primaryStage.setScene(latencyScene("Device 2")));
        device3.setOnAction(e -> primaryStage.setScene(latencyScene("Device 3")));

        VBox box = new VBox(15, selectLabel, device1, device2, device3);
        box.setAlignment(Pos.CENTER);

        Scene scene = new Scene(box, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        return scene;

    }

    //Choose Latency Scene
    private Scene latencyScene(String deviceName){

        Label label = new Label(deviceName + ": Choose latency mode");
        label.setFont(Font.font("Helvetica", 25));

        Button hlButton = new Button("High Latency");
        hlButton.setFont(Font.font("Helvetica"));

        Button llButton = new Button("Low Latency");
        llButton.setFont(Font.font("Helvetica"));

        Button back = new Button("Back");
        back.setFont(Font.font("Helvetica"));

        hlButton.setOnAction(e -> primaryStage.setScene(powerReading(deviceName, "High")));
        llButton.setOnAction(e -> primaryStage.setScene(powerReading(deviceName, "Low")));
        back.setOnAction(e -> primaryStage.setScene(deviceSelectionScene()));

        HBox hbox = new HBox(15, hlButton, llButton);
        hbox.setAlignment(Pos.CENTER);

        VBox vbox = new VBox(15, label, hbox, back);
        vbox.setAlignment(Pos.CENTER);

        Scene scene = new Scene(vbox, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        return scene;
    }

    //High or Low Latency Power Reading Scene
    private Scene powerReading(String deviceName, String latencyMode) {

        stopExecutor();

        Label label = new Label(deviceName + ": " + latencyMode + " Latency");
        label.setFont(Font.font("Helvetica", 25));

        currentLabel = new Label("Current: -- mA");
        voltageLabel = new Label("Voltage: -- mV");
        powerLabel = new Label("Power: -- mW");

        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Time");

        NumberAxis yAxis = new NumberAxis();
        yAxis.setLabel("Samples");

        chart = new LineChart<>(xAxis, yAxis);
        chart.setTitle("Live Telemetry");

        seriesCurrent = new XYChart.Series<>();
        seriesCurrent.setName("Current (mA)");

        seriesVoltage = new XYChart.Series<>();
        seriesVoltage.setName("Voltage (mV)");

        seriesPower = new XYChart.Series<>();
        seriesPower.setName("Power (mW)");

        chart.getData().addAll(seriesCurrent, seriesVoltage, seriesPower);
        chart.setCreateSymbols(false);
        chart.setMinHeight(300);


        Label powerLimitLabel = new Label("Limit Power: To enable power limiting select the desired power percentage" +
                " of 145 W ");
        Label sliderLabel = new Label();
        Slider powerSL = new Slider(80, 100, 20);
        powerSL.setShowTickMarks(true);
        powerSL.setShowTickLabels(true);
        powerSL.setMajorTickUnit(5);
        powerSL.setMinorTickCount(4);
        powerSL.setMaxWidth(500);

        powerSL.valueProperty().addListener((obs, oldVal, newVal) -> {
            int percent = newVal.intValue();
            double watts = (percent/100.0) * 145.0;
            sliderLabel.setText(String.format("Power: %d%% (%.1f W)", percent, watts));
        });

        powerSL.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
            if (!isChanging) {
                // User just released the slider
                int percent = (int) powerSL.getValue();

                // Run network call off the UI thread
                new Thread(() -> {
                    try {
                        sendPowerLimitPercent(percent);
                        Platform.runLater(() -> {
                            sliderLabel.setText(sliderLabel.getText() + "  (applied)");
                        });
                    } catch (Exception e) {
                        Platform.runLater(() -> {
                            sliderLabel.setText("Failed to apply limit");
                        });
                        System.err.println("Failed to set power limit: " + e.getMessage());
                    }
                }).start();
            }
        });

        VBox sliderBox = new VBox(powerLimitLabel, powerSL, sliderLabel);
        sliderBox.setAlignment(Pos.CENTER);

        Button back = new Button("Back");
        back.setFont(Font.font("Helvetica"));
        back.setOnAction(e -> {
            stopExecutor();
            primaryStage.setScene(latencyScene(deviceName));
        });

        VBox vbox = new VBox(15, label, currentLabel, voltageLabel, powerLabel,
                sliderBox, chart, back);
        vbox.setAlignment(Pos.CENTER);
        vbox.setStyle("-fx-padding: 30;");

        if(latencyMode.equals("High")) {
            updatePowerHL(deviceName, latencyMode);
        }
        else {
            updatePowerLL(deviceName, latencyMode);
        }

        Scene scene = new Scene(vbox, 1200, 800);
        scene.getStylesheets().add(getClass().getResource("/style.css").toExternalForm());

        return scene;
    }

    //High Latency records a power reading every 60 seconds
    private void updatePowerHL(String deviceName, String latencyMode){

        stopExecutor();
        executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() ->  {

            try {
                PowerSample sample = fetchLatestFromBackend();

                Platform.runLater(() -> {
                    currentLabel.setText(String.format("Current: %.2f A", sample.current));
                    voltageLabel.setText(String.format("Voltage: %.2f V", sample.voltage));
                    powerLabel.setText(String.format("Power: %.2f W", sample.power));

                    //Updating the graph
                    sampleIndex++;

                    seriesCurrent.getData().add(new XYChart.Data<>(sampleIndex, sample.current));
                    seriesVoltage.getData().add(new XYChart.Data<>(sampleIndex, sample.voltage));
                    seriesPower.getData().add(new XYChart.Data<>(sampleIndex, sample.power));

                    //Only keeps the last 300 points on the graph
                    int maxPoints = 300;
                    if (seriesCurrent.getData().size() > maxPoints) {
                        seriesCurrent.getData().remove(0);
                        seriesVoltage.getData().remove(0);
                        seriesPower.getData().remove(0);
                    }
                });

            } catch (Exception e) {
                //Current, voltage, and power are just blank if there's an error
                Platform.runLater(() -> {
                    currentLabel.setText("Current: -- mA");
                    voltageLabel.setText("Voltage: -- mV");
                    powerLabel.setText("Power: -- mW");
                });
                System.err.println("HL metrics failed: " + e.getMessage());
            }

        }, 0, 60, TimeUnit.SECONDS);
    }

    // Low Latency records a power reading every 3 seconds
    private void updatePowerLL(String deviceName, String latencyMode){

        stopExecutor();
        executor = Executors.newSingleThreadScheduledExecutor();

        executor.scheduleAtFixedRate(() ->  {

            try {
                PowerSample sample = fetchLatestFromBackend();

                Platform.runLater(() -> {
                    currentLabel.setText(String.format("Current: %.2f mA", sample.current));
                    voltageLabel.setText(String.format("Voltage: %.2f mV", sample.voltage));
                    powerLabel.setText(String.format("Power: %.2f mW", sample.power));

                    //Updates the graph
                    sampleIndex++;

                    seriesCurrent.getData().add(new XYChart.Data<>(sampleIndex, sample.current));
                    seriesVoltage.getData().add(new XYChart.Data<>(sampleIndex, sample.voltage));
                    seriesPower.getData().add(new XYChart.Data<>(sampleIndex, sample.power));

                    //Only keeps the last 300 points on the graph
                    int maxPoints = 300;
                    if (seriesCurrent.getData().size() > maxPoints) {
                        seriesCurrent.getData().remove(0);
                        seriesVoltage.getData().remove(0);
                        seriesPower.getData().remove(0);
                    }
                });

            } catch (Exception e) {
                //Current, voltage, and power are just blank if there's an error
                Platform.runLater(() -> {
                    currentLabel.setText("Current: -- mA");
                    voltageLabel.setText("Voltage: -- mV");
                    powerLabel.setText("Power: -- mW");
                });
                System.err.println("LL metrics failed: " + e.getMessage());
            }

        }, 0, 3, TimeUnit.SECONDS);
    }

    //Calls the backend to get the values of current, voltage, and power
    //Obtains the value through an IP address
    private PowerSample fetchLatestFromBackend() throws IOException {
        String urlString = BACKEND_BASE_URL + "/telemetry/latest";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }

        if (status != 200) {
            throw new IOException("Backend returned " + status + ": " + sb);
        }

        String body = sb.toString();
        double voltage = parseJsonDouble(body, "voltage_V");
        double current = parseJsonDouble(body, "current_A");
        double power   = parseJsonDouble(body, "power_W");

        return new PowerSample(current, voltage, power);
    }

    //Calls the backend when the user sets a power limit
    private void sendPowerLimitPercent(int percent) throws IOException {
        String urlString = BACKEND_BASE_URL + "/control/power_limit";
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(2000);
        conn.setReadTimeout(2000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8");

        String jsonBody = "{\"percent\":" + percent + "}";

        try (OutputStream os = conn.getOutputStream()) {
            os.write(jsonBody.getBytes("UTF-8"));
        }

        int status = conn.getResponseCode();
        InputStream is = (status >= 200 && status < 300)
                ? conn.getInputStream()
                : conn.getErrorStream();

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is))) {
            String line;
            while ((line = br.readLine()) != null) {
                sb.append(line);
            }
        } finally {
            conn.disconnect();
        }

        if (status != 200) {
            throw new IOException("Power limit POST failed with " + status + ": " + sb);
        }

        System.out.println("Power limit set response: " + sb);
    }

    //Helper that pulls a numeric field from a JSON object
    private double parseJsonDouble(String json, String fieldName) throws IOException {
        String key = "\"" + fieldName + "\"";
        int idx = json.indexOf(key);
        if (idx < 0) {
            throw new IOException("Field not found in JSON: " + fieldName);
        }
        int colon = json.indexOf(":", idx);
        if (colon < 0) {
            throw new IOException("Bad JSON near field: " + fieldName);
        }

        int start = colon + 1;
        int end = start;
        String validChars = "0123456789+-.eE";
        while (end < json.length() && validChars.indexOf(json.charAt(end)) >= 0) {
            end++;
        }

        String numStr = json.substring(start, end).trim();
        try {
            return Double.parseDouble(numStr);
        } catch (NumberFormatException ex) {
            throw new IOException("Invalid number for " + fieldName + ": " + numStr);
        }
    }

    private void stopExecutor() {
        if(executor != null && !executor.isShutdown()) {
            executor.shutdownNow();
        }
    }

    @Override
    public void stop() {
        stopExecutor();
    }

    public static void main(String[] args) {
        launch();
    }
}


