/*
 * Proyecto Final
 * Jairo Soto Yañez
 * 7CM3
 */

package com.mycompany.app;

import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import javax.sound.sampled.AudioFormat.Encoding;
import javax.swing.JFrame;
import javax.swing.JLabel;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;

import java.io.File;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;


import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;


import java.util.Collections;

import javax.swing.*;
import java.awt.*;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;


import java.io.BufferedReader;

import java.io.FileReader;

import com.sun.management.OperatingSystemMXBean;

import javax.swing.*;
import java.awt.*;
import java.lang.management.ManagementFactory;
//import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;

import java.util.Timer;
import java.util.TimerTask;

public class WebServer extends JFrame{
    private static final String STATUS_ENDPOINT = "/status";
    private static final String MONITORING_ENDPOINT = "/monitoring";
    private static final String HOME_PAGE_ENDPOINT = "/";
    private static final String HOME_PAGE_UI_ASSETS_BASE_DIR = "/ui_assets/";
    private static final String ENDPOINT_PROCESS = "/procesar_datos";
    private static final String TASK_ENDPOINT = "/task";
    private final int port;
    private HttpServer server;
    private final ObjectMapper objectMapper;
    private static final HttpClient httpClient = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();

    private static final String DB_BOOKS_PATH = "src/main/java/com/mycompany/app/LIBROS_TXT/";
    private static HashMap<Character, Character> DICTIONARY = new HashMap<>();
    private static final String[] WORKERS_ADDRESS = {
            "http://localhost:8081/task",
            "http://localhost:8082/task",
            "http://localhost:8083/task"
    };
    

    private static final String[] INIT_WORKERS_ADDRESS = {
            "http://localhost:8081/init",
            "http://localhost:8082/init",
            "http://localhost:8083/init"
    };

    private static XYSeries cpuSeries;
    private static XYSeries ramSeries;
    private static JLabel costosLabel;


    // Create Async tools
    private static File FILES;
    // private static Aggregator aggregator;
    private static String[] TASKS;

    public WebServer(int port) {
        this.port = port;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        HttpContext statusContext = server.createContext(STATUS_ENDPOINT);
        HttpContext processContext = server.createContext(ENDPOINT_PROCESS);
        HttpContext homePageContext = server.createContext(HOME_PAGE_ENDPOINT);
        HttpContext taskContext = server.createContext(TASK_ENDPOINT);
        HttpContext monitoringContext = server.createContext(MONITORING_ENDPOINT);

        statusContext.setHandler(this::handleStatusCheckRequest);
        processContext.setHandler(this::handleprocessRequest);
        homePageContext.setHandler(this::handleRequestForAsset);
        taskContext.setHandler(this::handleTaskRequest);
        monitoringContext.setHandler(this::handleMonitoringRequest);

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        // Load necessary data
        // aggregator = new Aggregator();
        FILES = new File(DB_BOOKS_PATH);
        TASKS = FILES.list();
        loadDictionary();

        // GUI
        cpuSeries = new XYSeries("CPU Usage");
        ramSeries = new XYSeries("RAM Usage");
        costosLabel = new JLabel("Costos: 0");

        System.out.println("Initializing aux servers...");
        distributeChargeIntoServers();
        System.out.println("Aux servers were initialized succesfully...");
        Timer timer = new Timer();
        // Define task to repeat each %d seconds
        TimerTask task = new TimerTask() {
            @Override
            public void run() {
                updateData();
            }
        };
        timer.schedule(task, 0, 1000);
    }

    private static void updateData() {
        double cpuUsage = getCPUUsage();
        double ramUsage = getRAMUsage();

        cpuSeries.addOrUpdate(System.currentTimeMillis(), cpuUsage);
        ramSeries.addOrUpdate(System.currentTimeMillis(), ramUsage);
        
        double cpuArea = calculateAreaUnderCurve(cpuSeries);
        double ramArea = calculateAreaUnderCurve(ramSeries);
        costosLabel.setText(String.format("Costos de CPU: "+ (cpuArea*2) +"     Costos de RAM: " + (ramArea*2) + "     Totales: " + (2*(cpuArea+ramArea))));
    }

    private static double getCPUUsage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        return osBean.getSystemCpuLoad() * 100.0;
    }

    private static double getRAMUsage() {
        OperatingSystemMXBean osBean = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long totalMemory = osBean.getTotalPhysicalMemorySize();
        long freeMemory = osBean.getFreePhysicalMemorySize();
        long usedMemory = totalMemory - freeMemory;
        return (double) usedMemory / totalMemory * 100.0;
    }

    private static double calculateAreaUnderCurve(XYSeries series) {
        double area = 0.0;
        int itemCount = series.getItemCount();
    
        for (int i = 1; i < itemCount; i++) {
            double x1 = series.getX(i - 1).doubleValue();
            double y1 = series.getY(i - 1).doubleValue();
            double x2 = series.getX(i).doubleValue();
            double y2 = series.getY(i).doubleValue();
            area += (x2 - x1) * (y1 + y2) / 2.0;
        }
    
        return area;
    }
    
    // MOnitoring -> cpu and memory
    private void handleMonitoringRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        
        XYSeriesCollection dataset = new XYSeriesCollection();
        dataset.addSeries(cpuSeries);
        dataset.addSeries(ramSeries);

        JFreeChart chart = ChartFactory.createXYLineChart(
                "Consumo de recursos",
                "Tiempo",
                "Consumo (%)",
                dataset
        );

        XYPlot plot = chart.getXYPlot();
        plot.setDomainPannable(true);
        plot.setRangePannable(true);
        
        setLayout(new BorderLayout());
        ChartPanel chartPanel = new ChartPanel(chart);
        chartPanel.setPreferredSize(new Dimension(800, 600));
        add(chartPanel, BorderLayout.CENTER);

        pack();
        // setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);
        setVisible(true);

        double cpuArea = calculateAreaUnderCurve(cpuSeries);
        double ramArea = calculateAreaUnderCurve(ramSeries);

        add(costosLabel, BorderLayout.NORTH);

        String response = String.format("Costos de CPU: %f\nCostos de RAM: %f\nTotales: %f", cpuArea*2, ramArea*2, 2*(cpuArea+ramArea));
        sendResponse(response.getBytes(), exchange);
    }

    private static void distributeChargeIntoServers() {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();;

        // Send async requests to other servers
        ArrayList<CompletableFuture<String>> futures = new ArrayList<>(INIT_WORKERS_ADDRESS.length);
        
        for (int i = 0; i < TASKS.length; i++) {
            // Get data
            String workerAddress = INIT_WORKERS_ADDRESS[i%INIT_WORKERS_ADDRESS.length]; // Switching tasks
            String task = TASKS[i];
            byte[] requestPayload = task.getBytes();
        
            // Send req
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestPayload))
                    .uri(URI.create(workerAddress)).header("X-Debug", "true").build();

            CompletableFuture<String> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(respuesta -> { return respuesta.body().toString();});
        
            futures.add(future);
        }

        // Get results
        List<String> results = new ArrayList<>();
        for (int i = 0; i < TASKS.length; i++) results.add(futures.get(i).join()); // Add results <future>
        
        System.out.println("Tareas distribuidas: " + results.size());
    }

    private static List<String> distributeSearchingIntoServers(ArrayList<String> tasks) {
        HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();;

        // Send async requests to other servers
        ArrayList<CompletableFuture<String>> futures = new ArrayList<>(WORKERS_ADDRESS.length);
        
        for (int i = 0; i < tasks.size(); i++) {
            // Get data
            String workerAddress = WORKERS_ADDRESS[i%WORKERS_ADDRESS.length]; // Switching tasks
            String task = tasks.get(i);
            byte[] requestPayload = task.getBytes();
        
            // Send req
            HttpRequest request = HttpRequest.newBuilder()
                    .POST(HttpRequest.BodyPublishers.ofByteArray(requestPayload))
                    .uri(URI.create(workerAddress)).header("X-Debug", "true").build();

            CompletableFuture<String> future = client.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenApply(respuesta -> { return respuesta.body().toString();});
        
            futures.add(future);
        }

        // Get results
        List<String> results = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) results.add(futures.get(i).join()); // Add results <future>
        
        System.out.println("Busquedas distribuidas: " + results.size());
        return results;
    }

    private void handleTaskRequest(HttpExchange exchange) throws IOException { // In case is not 'Post' request
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.close();
            return;
        }
        // Fill aux to get couters -> idf
        final int TITLE_POS = 0, WORDS_CONT = 1;
        HashMap<String, Long> occurencesCounter = new HashMap<>();
        ArrayList<ResultsObject> currContent = new ArrayList<>();
        HashMap<String, Long> repeatedWordsInSearch = new HashMap<>();
        
        // Get request's body
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        String requested = new String(requestBytes);
        System.out.println("Se recibe: " + requested);
        
        // Distribute charge
        // distributeChargeIntoServers();
        
        
        // Get filtered and processed data
        String filteredData = filterInput(requested);
        System.out.println("Busqueda filtrada" + filteredData);
        
        for (String tmpWord : filteredData.split(" ")){
            if(occurencesCounter.containsKey(tmpWord)){
                repeatedWordsInSearch.replace(tmpWord, repeatedWordsInSearch.get(tmpWord) + 1);
            }else{
                repeatedWordsInSearch.put(tmpWord, Long.valueOf(1));
                occurencesCounter.put(tmpWord, Long.valueOf(0));
            }
        }

        ArrayList<String> tasks = getProcessedDataToDistribute(TASKS, filteredData);
        
        // Distribute searching unsing synchonous client
        List<String> results = distributeSearchingIntoServers(tasks);
        
        // Update data according to results obtaine'
        System.out.println("\n\nBusqueda desordenada: \n");
        for (String result : results){
            System.out.println(result);
            String [] currData = result.split("/");
            currContent.add(new ResultsObject(currData[TITLE_POS], currData[WORDS_CONT]));
        }
        
        // Calculate tf-idf for each instance
        doCountOccurrences(currContent, occurencesCounter, repeatedWordsInSearch);
        for (ResultsObject curr : currContent) curr.calculateTfIdf(occurencesCounter, results.size());
        
        // Sort it ascending
        Collections.sort(currContent, new SortbyTfIdf());
        System.out.println("Resultados obtenidos: " + results.size());
        System.out.println("\n\nBusqueda ordenada: \n");
        
        // Send sorted results
        String responseMsg = "";
        for (ResultsObject a : currContent) {
            System.out.println(a);
            responseMsg += a.getFilename() + "\n";
        }
        
        System.out.println("Resultados obtenidos: " + results.size());

        try {
            // Send constructed response
            sendResponse(responseMsg.getBytes(), exchange);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static ArrayList<String> getProcessedDataToDistribute(String[] source, String filteredData) {
        ArrayList<String> tasks = new ArrayList<>();
        for (String s : source) {
            tasks.add(s + "<---->" + filteredData);
        }
        return tasks;
    }

    private static String filterInput(String input) {
        String requestedFiltered = "";
        for (String s : input.split(" "))
            requestedFiltered += filterWord(s) + " ";

        return requestedFiltered;
    }

    private static void doCountOccurrences(ArrayList<ResultsObject> currContent,
            HashMap<String, Long> occurencesCounter, HashMap<String, Long> repeatedWordsInSearch) {
        // Get counters
        for (ResultsObject currentRO : currContent) {
            for (PairStringDouble currPair : currentRO.getSearchingWordResults()) {
                // Case: book contains a word, add it to mapcount
                if (currPair.getValue() > 0)
                    occurencesCounter.replace(currPair.getWord(),
                            occurencesCounter.get(currPair.getWord()) + Long.valueOf(1));
            }
        }

        // REduce repeated words
        for (String currWord : repeatedWordsInSearch.keySet()) {
            occurencesCounter.replace(currWord, occurencesCounter.get(currWord) / repeatedWordsInSearch.get(currWord));
        }
    }

    private static String filterWord(String word) {
        String aux = "";
        char c;
        for (int i = 0; i < word.length(); i++) {
            c = word.charAt(i);
            if (DICTIONARY.containsKey(c))
                aux += DICTIONARY.get(c); // If is a special char, just replace it for its equivalent
            else if (isAlphaNumeric(c))
                aux += Character.toLowerCase(c); // Case: is not a special char, lets filter it to just letters and
                                                 // numbers in lowercase format
        }
        return aux;
    }

    private static boolean isAlphaNumeric(char c) {
        return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9');
    }

    private static void loadDictionary() {
        HashMap<Character, List<Character>> equivalents = new HashMap<>();
        equivalents.put('c', Arrays.asList('Ç', 'ç'));
        equivalents.put('a', Arrays.asList('á', 'â', 'ä', 'à', 'å', 'Å', 'Ä', 'Æ', 'Á', 'Â', 'À', 'ã', 'Ã', 'æ', 'ª'));
        equivalents.put('e', Arrays.asList('ê', 'ë', 'è', 'É', 'Ê', 'Ë', 'È', 'é'));
        equivalents.put('i', Arrays.asList('ï', 'î', 'ì', 'í', 'Í', 'Î', 'Ï', 'Ì'));
        equivalents.put('o', Arrays.asList('ô', 'ö', 'ò', 'Ö', 'º', 'ó', 'ð', 'Ó', 'Ô', 'Ò', 'õ', 'Õ'));
        equivalents.put('u', Arrays.asList('ü', 'û', 'ù', 'Ü', 'ú', 'µ', 'Ú', 'Û', 'Ù'));
        equivalents.put('y', Arrays.asList('ÿ', 'ý', 'Ý'));
        equivalents.put('x', Arrays.asList('×'));
        equivalents.put('f', Arrays.asList('ƒ'));
        equivalents.put('n', Arrays.asList('ñ', 'Ñ'));
        equivalents.put('d', Arrays.asList('Ð'));
        equivalents.put('b', Arrays.asList('ß'));
        equivalents.put('p', Arrays.asList('þ', 'Þ'));
        for (Character c : equivalents.keySet())
            for (Character item : equivalents.get(c))
                DICTIONARY.put(item, c);
    }

    private void handleRequestForAsset(HttpExchange exchange) throws IOException {
        // System.out.println(exchange.getRequestMethod());
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        byte[] response;
        String asset = exchange.getRequestURI().getPath();
        if (asset.equals(HOME_PAGE_ENDPOINT)) {
            response = readUiAsset(HOME_PAGE_UI_ASSETS_BASE_DIR + "index.html");
        } else {
            response = readUiAsset(asset);
        }
        // System.out.println(String.format("Archivo: %s, Tamaño: %d bytes", asset, response.length));
        addContentType(asset, exchange);
        sendResponse(response, exchange);
    }

    private byte[] readUiAsset(String asset) throws IOException {
        InputStream assetStream = getClass().getResourceAsStream(asset);
        if (assetStream == null) {
            return new byte[] {};
        }
        return assetStream.readAllBytes();
    }

    private static void addContentType(String asset, HttpExchange exchange) {
        String contentType = "text/html";
        if (asset.endsWith("js")) {
            contentType = "text/javascript";
        } else if (asset.endsWith("css")) {
            contentType = "text/css";
        }
        exchange.getResponseHeaders().add("Content-Type", contentType);
    }

    private void handleprocessRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.close();
            return;
        }
        try {
            FrontendSearchRequest frontendSearchRequest = objectMapper
            .readValue(exchange.getRequestBody().readAllBytes(), FrontendSearchRequest.class);
            String frase = frontendSearchRequest.getSearchQuery();
            // Send syncronous request to other server
            String server = "http://localhost:3000/task";
            HttpRequest request = HttpRequest.newBuilder().POST(HttpRequest.BodyPublishers.ofString(frase))
                    .uri(URI.create(server)).build();
            HttpClient client = HttpClient.newBuilder().version(HttpClient.Version.HTTP_1_1).build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            String responseString = response.body();
            FrontendSearchResponse frontendSearchResponse = new FrontendSearchResponse(frase, responseString);
            byte[] responseBytes = objectMapper.writeValueAsBytes(frontendSearchResponse);
            sendResponse(responseBytes, exchange);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void handleStatusCheckRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        String responseMessage = "El servidor está vivo\n";
        sendResponse(responseMessage.getBytes(), exchange);
    }

    private void sendResponse(byte[] responseBytes, HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(responseBytes);
        outputStream.flush();
        outputStream.close();
    }
}