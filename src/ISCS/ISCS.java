package src.ISCS;

import java.net.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.stream.Collectors;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import com.sun.net.httpserver.HttpContext;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.sql.Statement;
import java.util.Map;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Scanner;
import com.google.gson.Gson;

public class ISCS {

    private static String filename;

    private static HttpServer server;

    private static Boolean isStartingUp = true;

    // method to parse JSON data from str
    private static Map<String, String> JSONParser(String json) {
        Map<String, String> data = new HashMap<>();
        json = json.trim().replaceAll("[{}\"]", "");
        String[] pairs = json.split(",");
        for (String pair : pairs) {
            String[] keyValue = pair.trim().split(":");
            if (keyValue.length == 2) {
                data.put(keyValue[0].trim(), keyValue[1].trim());
            }
        }
        return data;
    }

    // extracts the configuration for ISCS
    private static String extractISCSConfig(String json, String ISCSName) {
        String ISCSPattern = "\"" + ISCSName + "\": {";
        int startIndex = json.indexOf(ISCSPattern);
        if (startIndex == -1) {
            return "{}"; // ISCS not found, return empty JSON object
        }
        startIndex += ISCSPattern.length() - 1; // move to the opening brace

        int bracesCount = 1;
        int endIndex = startIndex;
        while (endIndex < json.length() && bracesCount > 0) {
            endIndex++;
            char ch = json.charAt(endIndex);
            if (ch == '{') {
                bracesCount++;
            } else if (ch == '}') {
                bracesCount--;
            }
        }
        return json.substring(startIndex, endIndex + 1); // include closing brace
    }

    //method to read config file
    public static Map<String, String> readConfigFile( String type) throws IOException {
        String configContent = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
        String userISCSConfigContent = extractISCSConfig(configContent, type);
        return JSONParser(userISCSConfigContent);
    }

    public static void main(String[] args) throws IOException {
    
        // use "config.json" as the default config file name
        filename = "config.json";
        // if an argument is provided, use it as the configuration file name
        if (args.length > 0) {
            filename = args[0]; // get filename in same path
        }
    
        /// Retrieve port and IP from the config file
        Map<String, String> ISCSConfig = readConfigFile("InterServiceCommunication");
        int port = Integer.parseInt(ISCSConfig.get("port"));
        String ip = ISCSConfig.get("ip");
    
        // Start the server
        ISCS.server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        // endpoints
        HttpContext orderContext = ISCS.server.createContext("/order");
        HttpContext productContext = ISCS.server.createContext("/product");
        HttpContext userContext = ISCS.server.createContext("/user");
        HttpContext userPurchasedContext = ISCS.server.createContext("/user/purchased");
    
        // Set the same handler for all contexts
        orderContext.setHandler(ISCS::handleRequest);
        productContext.setHandler(ISCS::handleRequest); 
        userContext.setHandler(ISCS::handleRequest);
        userPurchasedContext.setHandler(ISCS::handleRequest);
    
        ISCS.server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");
    }

    // handler method for all HTTP requests
    private static void handleRequest(HttpExchange exchange) throws IOException {
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();
        String[] segments = path.split("/");
        String response = "";
        int responseCode = 200;
    
        try {

            
            // for placing order
            if ("POST".equals(exchange.getRequestMethod()) && path.startsWith("/ISCS")) {
                response = handleISCSRequest(exchange.getRequestBody());
            } 
            //  for retrieving user purchases
            else if ("GET".equals(exchange.getRequestMethod()) && path.startsWith("/user/purchased") && segments.length >= 4) {
                response = forwardRequestToService(3, path, exchange.getRequestBody(), exchange.getRequestMethod());
            } 
            
            else if (path.startsWith("/product") || path.startsWith("/user") || path.startsWith("/order")) {
                int type = -1;
                if (path.startsWith("/product")) {
                    type = 0;
                } else if (path.startsWith("/user")) {
                    type = 1;
                } else {
                    type = 2;
                }
                response = forwardRequestToService(type, path, exchange.getRequestBody(), exchange.getRequestMethod());
            }
  
            else {
                response = "Unsupported endpoint.";
                responseCode = 404;
            }
        } catch (IOException e) {
            response = "Internal server error.";
            responseCode = 500;
        }
    
        exchange.sendResponseHeaders(responseCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();

        ISCS.isStartingUp = false;
    }

    // POST request handler to place Order
    private static String handleISCSRequest(InputStream requestBody) {
        try (Scanner scanner = new Scanner(requestBody, StandardCharsets.UTF_8)) {
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);
            String command = data.get("command");
            if ("shutdown".equals(command)) {
                forwardMessageToService(1, "dummy/path/shutdown", body, "POST");
                forwardMessageToService(0, "dummy/path/shutdown", body, "POST");
                ISCS.server.stop(0);
                System.exit(0);
                return "Unreachable";
            } else if ("restart".equals(command)) {
                if (!ISCS.isStartingUp){
                    return "Restart must be the first command in a workload.";
                }
                
                System.out.println("Starting from scratch");
                forwardMessageToService(1, "dummy/path/restart", body, "POST");
                forwardMessageToService(0, "dummy/path/restart", body, "POST");

                ISCS.isStartingUp = false;
                return "Restarting...";

            } else {
                return "Invalid command.";
            }
        } catch (Exception e) {
            return "Invalid";
        }
    }


    // type 1 = User, type 0 = Product, type 2 = Order, type 3 = User/Purchased
    private static String forwardMessageToService(int type, String path, String body, String method) throws IOException {
        Map<String, String> serviceConfig;
        String endpoint = "";
        if (type == 0) {
            serviceConfig = readConfigFile("ProductService");
            endpoint = "/product";
        } else if (type == 1) {
            serviceConfig = readConfigFile("UserService");
            endpoint = "/user";
        } else if (type == 2) {
            serviceConfig = readConfigFile("OrderService");
            endpoint = "/order";
        } else {
            serviceConfig = readConfigFile("OrderService");
            endpoint = "/user/purchased";
        }
    
        int port = Integer.parseInt(serviceConfig.get("port"));
        String ip = serviceConfig.get("ip");
        String targetUrl = "http://" + ip + ":" + port + endpoint;

        // GET request forwarding
        if ("GET".equals(method)) {
            String getResponse;
            String[] paths = path.split("/");
            try {
                if (type == 3) {
                    getResponse = sendGetRequest(targetUrl + '/' + paths[3]);
                } else {
                    getResponse = sendGetRequest(targetUrl + '/' + paths[2]);
                }
                
                System.out.println("Response Message: " + getResponse.toString());
                return getResponse;
            } catch (IOException e) {
                return "Invalid";
            }
        }

        HttpURLConnection connection = null;
        try {
            // Convert InputStream requestBody to String
            System.out.println("Sending " + method + " request to: " + targetUrl + " with body: " + body);
    
            // Create URL and open connection
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
    
            // Set request method to the method received from the original request
            connection.setRequestMethod(method);
    
            // If the original request is POST, we need to write the body to the output stream
            if ("POST".equals(method)) {
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
    
                try (OutputStream os = connection.getOutputStream()) {
                    byte[] input = body.getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
            }
    
            // Check the response code and read the response
            int responseCode = connection.getResponseCode();
            InputStream responseStream = (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) ? connection.getInputStream() : connection.getErrorStream();
    
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                String responseLine;
                while ((responseLine = br.readLine()) != null) {
                    response.append(responseLine.trim());
                }
            }
    
            System.out.println("Response Code: " + responseCode);
            System.out.println("Response Message: " + response.toString());
    
            return response.toString();
    
        } catch (IOException e) {
            e.printStackTrace();
            return "Error: Unable to connect to service.";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static String forwardRequestToService(int type, String path, InputStream requestBody, String method) throws IOException {
        // Convert InputStream requestBody to String
        String body = new BufferedReader(new InputStreamReader(requestBody))
                        .lines().collect(Collectors.joining("\n"));

        return forwardMessageToService(type, path, body, method);
    
    }

    private static String sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");
    
        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("Received HTTP " + responseCode + " from " + urlString);
        }
    
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return response.toString();
    }
    
}
