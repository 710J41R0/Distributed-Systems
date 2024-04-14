/*
 * Proyecto Final
 * Jairo Soto Yañez
 * 7CM3
 */

package com.mycompany.app;


import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.Executors;

import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.XYPlot;
import org.jfree.data.xy.XYSeries;
import org.jfree.data.xy.XYSeriesCollection;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategy;


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


/* 
 * #####################################################################################
 * ######################################### SERVER ####################################
 * #####################################################################################
 */


public class ProcessingServer extends JFrame{
    private static HashMap<Character, Character> DICTIONARY = new HashMap<>();
    private static HashMap<String, HashMap<String, Long>> MAPPED_BOOKS = new HashMap<>();
    private static HashMap<String, Long> BOOKS_WORDS_COUNT = new HashMap<>();
    private static final String TASK_ENDPOINT = "/task";
    private static final String INIT_ENDPOINT = "/init";
    private static final String STATUS_ENDPOINT = "/status";
    private static final String MONITORING_ENDPOINT = "/monitoring";
    private static final String MONITORING_CPU_ENDPOINT = "/monitoring_cpu";
    private static final String MONITORING_RAM_ENDPOINT = "/monitoring_ram";
    private static final String DB_BOOKS_PATH = "src/main/java/com/mycompany/app/LIBROS_TXT/";
    
    private final int port;
    private HttpServer server;
    private static ObjectMapper objectMapper;
    private static XYSeries cpuSeries;
    private static XYSeries ramSeries;
    private static JLabel costosLabel;



    public static void main(String[] args) {
        int serverPort = 8083;
        if (args.length == 1) {
            serverPort = Integer.parseInt(args[0]);
        }

        ProcessingServer webServer = new ProcessingServer(serverPort);
        objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        
        cpuSeries = new XYSeries("CPU Usage");
        ramSeries = new XYSeries("RAM Usage");
        costosLabel = new JLabel("Costos: 0");
        
        webServer.startServer();
        loadDictionary();       
        // SwingUtilities.invokeLater(() -> new TestingCpuMem("Consumo de recursos del servidor")); 
        System.out.println("Servidor escuchando en el puerto " + serverPort);
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

    // Constructor
    public ProcessingServer(int port) {
        this.port = port;
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
    

    // Init server
    public void startServer() {
        try {
            this.server = HttpServer.create(new InetSocketAddress(port), 0);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }

        HttpContext initContext = server.createContext(INIT_ENDPOINT);
        HttpContext statusContext = server.createContext(STATUS_ENDPOINT);
        HttpContext taskContext = server.createContext(TASK_ENDPOINT);
        HttpContext monitoringCPUContext = server.createContext(MONITORING_CPU_ENDPOINT);
        HttpContext monitoringContext = server.createContext(MONITORING_ENDPOINT);
        HttpContext monitoringRAMContext = server.createContext(MONITORING_RAM_ENDPOINT);

        initContext.setHandler(this::handleInitRequest);
        statusContext.setHandler(this::handleStatusCheckRequest);
        taskContext.setHandler(this::handleTaskRequest);
        monitoringContext.setHandler(this::handleMonitoringRequest);
        monitoringCPUContext.setHandler(this::handleMonitoringCPURequest);
        monitoringRAMContext.setHandler(this::handleMonitoringRAMRequest);
        
        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();
    }

    // Whe starts the main server
    private void handleInitRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.close();
            return;
        }
        
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        String reqBook = new String(requestBytes);
        System.out.println("Solicita mapeo de: \n" + reqBook);
        
        // Load necessary data
        MAPPED_BOOKS.put(reqBook, getWordsFromBook(reqBook));
        Long acu = (long)0;
        HashMap<String, Long> currBook = MAPPED_BOOKS.get(reqBook);
        
        for (String tmpWord : currBook.keySet())
            acu += currBook.get(tmpWord);
        
        BOOKS_WORDS_COUNT.put(reqBook, acu);

        System.out.println("Done...");
        sendResponse("Servidor actualizado y listo para recibir consultas".getBytes(), exchange);
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


    private void handleMonitoringCPURequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        byte[] responseBytes = objectMapper.writeValueAsBytes(cpuSeries);
        sendResponse(responseBytes, exchange);
    }

    private void handleMonitoringRAMRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }
        byte[] responseBytes = objectMapper.writeValueAsBytes(ramSeries);
        sendResponse(responseBytes, exchange);
    }

    // Solve a client's query
    private void handleTaskRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("post")) {
            exchange.close();
            return;
        } 
        
        byte[] requestBytes = exchange.getRequestBody().readAllBytes();
        String requested = new String(requestBytes);
        System.out.println("Se recibe (busqueda): " + requested);
        String[] data = requested.split("<---->"); // Format -> BookName <----> FilteredRequestedSearch
        String bookName = data[0];
        String requestedFiltered = data[1];
        
        // Just make a 'constant' query. Format -> bookName / word_i:cont_i word_i+1:cont_i+1
        String searchResults = makeSearch(requestedFiltered, MAPPED_BOOKS.get(bookName), bookName);
        System.out.println(searchResults);
        sendResponse(searchResults.getBytes(), exchange);
        // sendResponse("Done".getBytes(), exchange);
    }
    
    private static String makeSearch(String wordToSearch, HashMap<String, Long> words, String bookName){
        String result = bookName + "/";
        for (String s: wordToSearch.split(" ")) {
            // if(words.containsKey(s)) result += s + ":" + words.get(s) + " ";
            // if(words.containsKey(s)) result += s + ":" + String.format("%.12f", (float)words.get(s)/BOOKS_WORDS_COUNT.get(bookName)) + " ";
            if(words.containsKey(s)) result += s + ":" + (double)words.get(s)/BOOKS_WORDS_COUNT.get(bookName) + " ";
            else result += s + ":0 ";
        }

        return result;
    }

    // To get all words from a txt file (book)
    private static HashMap<String, Long> getWordsFromBook(String fileName) {
        HashMap<String, Long> words = new HashMap<>();
        try {
            BufferedReader tmp = new BufferedReader(new FileReader(new File(DB_BOOKS_PATH + fileName)));
            String line, aux = "";
            while ((line = tmp.readLine()) != null) { // Get data
                for (String s : line.split(" ")) {
                    aux = filterWord(s);
                    if (words.containsKey(aux))
                        words.replace(aux, words.get(aux) + 1);
                    else
                        words.put(aux, Long.valueOf(1));
                }
            }
            tmp.close();
        } catch (Exception e) {
            words = null;
            System.out.println("Error al leer palabras del libro " + fileName);
            System.exit(0);
        }

        return words;
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
        equivalents.put('c', Arrays.asList('Ç','ç'));
        equivalents.put('a', Arrays.asList('á','â','ä','à','å','Å','Ä','Æ','Á','Â','À','ã','Ã','æ','ª'));
        equivalents.put('e', Arrays.asList('ê','ë','è','É','Ê','Ë','È','é'));
        equivalents.put('i', Arrays.asList('ï','î','ì','í','Í','Î','Ï','Ì'));
        equivalents.put('o', Arrays.asList('ô','ö','ò','Ö','º','ó','ð','Ó','Ô','Ò','õ','Õ'));
        equivalents.put('u', Arrays.asList('ü','û','ù','Ü','ú','µ','Ú','Û','Ù'));
        equivalents.put('y', Arrays.asList('ÿ','ý','Ý'));
        equivalents.put('x', Arrays.asList('×'));
        equivalents.put('f', Arrays.asList('ƒ'));
        equivalents.put('n', Arrays.asList('ñ','Ñ'));
        equivalents.put('d', Arrays.asList('Ð'));
        equivalents.put('b', Arrays.asList('ß'));
        equivalents.put('p', Arrays.asList('þ','Þ'));
        for (Character c : equivalents.keySet())
            for (Character item : equivalents.get(c))
                DICTIONARY.put(item, c);
    }

    private void handleStatusCheckRequest(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equalsIgnoreCase("get")) {
            exchange.close();
            return;
        }

        String responseMessage = "El servidor está vivo\n";
        sendResponse(responseMessage.getBytes(), exchange);
    }

    // To send response
    private void sendResponse(byte[] responseBytes, HttpExchange exchange) throws IOException {
        exchange.sendResponseHeaders(200, responseBytes.length);
        OutputStream outputStream = exchange.getResponseBody();
        outputStream.write(responseBytes);
        outputStream.flush();
        outputStream.close();
        exchange.close();
    }
}
