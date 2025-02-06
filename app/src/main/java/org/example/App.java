package org.example;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import javafx.scene.chart.LineChart;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.XYChart;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import org.json.JSONObject;

/**
 * A JavaFX application that:
 * 1. Queries Alpha Vantage for the DIA ETF price every 5 seconds (15-minute delayed),
 * 2. Parses the received JSON,
 */
public class App extends Application {

    // -- Use the 15-minute delayed Alpha Vantage API key
    private static final String API_KEY = "AAZWB58517RCWR74";
    private static final String SYMBOL = "DIA"; // Using DIA (ETF) as a Dow Jones proxy

    // JavaFX chart Series to hold price data
    private final XYChart.Series<Number, Number> series = new XYChart.Series<>();
    // Simple counter to represent each data fetch on the x-axis
    private int dataPointIndex = 0;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        // Create Axes
        NumberAxis xAxis = new NumberAxis();
        xAxis.setLabel("Fetch #");

        // Here, let's pick a 0.1 step:
        NumberAxis yAxis = new NumberAxis(425.5, 427.5, 0.1);
        yAxis.setLabel("Price (USD)");
        yAxis.setAutoRanging(false);

        // Create the LineChart
        LineChart<Number, Number> lineChart = new LineChart<>(xAxis, yAxis);
        lineChart.setTitle("DIA Price (Dow Jones Industrial Average Proxy)");
        series.setName("DIA");
        lineChart.getData().add(series);

        // Set up the Scene and Stage
        Scene scene = new Scene(lineChart, 800, 600);
        primaryStage.setTitle("Alpha Vantage: DIA Stock Price Chart");
        primaryStage.setScene(scene);
        primaryStage.show();

        // Start a Background Thread to Fetch Data
        Thread fetchThread = new Thread(() -> {
            while (true) {
                try {
                    String stockData = fetchStockData(SYMBOL, API_KEY);
                    if (stockData != null) {
                        // Parse the "Price" from the returned string
                        double price = parsePriceFromString(stockData);

                        if (!Double.isNaN(price)) {
                            // Increment data point index to place on x-axis
                            dataPointIndex++;

                            // Update chart on the JavaFX Application Thread
                            Platform.runLater(() -> {
                                series.getData().add(
                                        new XYChart.Data<>(dataPointIndex, price)
                                );
                            });
                        } else {
                            System.out.println("Invalid price or parse error.");
                        }
                    } else {
                        System.out.println("Failed to fetch stock data.");
                    }

                    // Sleep 5 seconds before the next fetch
                    Thread.sleep(5000);

                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });

        // Mark thread as daemon so it won't prevent the app from closing
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    /**
     * Fetches the latest quote from Alpha Vantage's GLOBAL_QUOTE endpoint (15-minute delayed).
     *
     * @param stockSymbol The stock/ETF symbol (e.g., "DIA").
     * @param apiKey      Your Alpha Vantage (15-min delayed) API key.
     * @return A string containing "Price: x.xxxx, Timestamp: <millis>", or null on error.
     */
    public static String fetchStockData(String stockSymbol, String apiKey) {
        try {
            // Construct the Alpha Vantage URL
            String apiUrl = "https://www.alphavantage.co/query?function=GLOBAL_QUOTE"
                    + "&symbol=" + stockSymbol
                    + "&entitlement=delayed"
                    + "&apikey=" + apiKey;

            URL url = new URL(apiUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");

            int responseCode = conn.getResponseCode();
            if (responseCode == 200) {
                // Read response
                BufferedReader in = new BufferedReader(
                        new InputStreamReader(conn.getInputStream())
                );
                StringBuilder response = new StringBuilder();
                String inputLine;
                while ((inputLine = in.readLine()) != null) {
                    response.append(inputLine);
                }
                in.close();

                // Print the full JSON response for debugging (optional)
                System.out.println("Full JSON Response: " + response.toString());

                // The JSON object may contain "Global Quote - DATA DELAYED BY 15 MINUTES"
                // Instead of just "Global Quote".
                // So let's handle both cases:
                JSONObject jsonResponse = new JSONObject(response.toString());
                String delayedKey = "Global Quote - DATA DELAYED BY 15 MINUTES";
                if (jsonResponse.has("Global Quote")) {
                    // normal key
                    JSONObject globalQuote = jsonResponse.getJSONObject("Global Quote");
                    double price = globalQuote.optDouble("05. price", Double.NaN);
                    return "Price: " + price + ", Timestamp: " + System.currentTimeMillis();
                } else if (jsonResponse.has(delayedKey)) {
                    // delayed key
                    JSONObject globalQuote = jsonResponse.getJSONObject(delayedKey);
                    double price = globalQuote.optDouble("05. price", Double.NaN);
                    return "Price: " + price + ", Timestamp: " + System.currentTimeMillis();
                } else {
                    System.out.println("Error: 'Global Quote' data not found in JSON.");
                }
            } else {
                System.out.println("HTTP Error code: " + responseCode);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null; // Return null if the request fails or if no data is found
    }

    /**
     * Parses a string in the format "Price: 123.45, Timestamp: 123456789" to extract the price.
     *
     * @param stockData The string with price and timestamp.
     * @return The parsed price as a double (NaN if parse fails).
     */
    private static double parsePriceFromString(String stockData) {
        if (stockData == null || !stockData.contains("Price:")) {
            return Double.NaN;
        }
        try {
            // Example: "Price: 345.67, Timestamp: 1691109069277"
            String[] parts = stockData.split(",");
            // parts[0] => "Price: 345.67"
            String priceStr = parts[0].substring(parts[0].indexOf(":") + 1).trim();
            return Double.parseDouble(priceStr);
        } catch (Exception e) {
            e.printStackTrace();
            return Double.NaN;
        }
    }
}


