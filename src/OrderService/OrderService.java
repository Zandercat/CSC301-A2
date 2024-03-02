import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.UUID;
import java.util.stream.Collectors;
import com.sun.net.httpserver.HttpExchange;


public class OrderService {

    private static String filename;

    // class OrderData to store data of users, stores id, username, email and password
    static class OrderData {
        public UUID order_id;
        public int user_id;
        public int product_id;
        public int quantity;
      
        // OrderData constructor setting user details
        public OrderData(UUID order_id, int user_id, int product_id, int quantity) {
            this.order_id = order_id;
            this.user_id = user_id;
            this.product_id = product_id;
            this.quantity = quantity;
        }
    }

    // using map to store orders, as a simple memory database for A1
    private static Map<UUID, OrderData> orders = new HashMap<>();

    public static Map<String, String> readConfigFile( String type) throws IOException {
        String configContent = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
        String userServiceConfigContent = extractServiceConfig(configContent, type);
        return JSONParser(userServiceConfigContent);
    }

    public static void main(String[] args) throws IOException {
        // use "config.json" as the default config file name
        filename = "config.json";
        // if an argument is provided, use it as the configuration file name
        if (args.length > 0) {
            filename = args[0]; // get filename in same path
        }

        /// Retrieve port and IP from the config file
        Map<String, String> userServiceConfig = readConfigFile("OrderService");
        int port = Integer.parseInt(userServiceConfig.get("port"));
        String ip = userServiceConfig.get("ip");

        // Start the server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        // endpoints
        HttpContext orderContext = server.createContext("/order");
        HttpContext productContext = server.createContext("/product");
        HttpContext userContext = server.createContext("/user");

        orderContext.setHandler(OrderService::handleRequest);
        productContext.setHandler(OrderService::handleRequest); 
        userContext.setHandler(OrderService::handleRequest);
        server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");
    }

    // handler method for all HTTP requests
    public static void handleRequest(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        URI requestURI = exchange.getRequestURI();
        String path = requestURI.getPath();
        String response = "";
        int responseCode = 200;
        int type = 0;

        if (path.startsWith("/order")) {
            // handle /order endpoint
            response = handleOrderRequest(exchange.getRequestBody());
            if (response.charAt(0) == '{') {
                responseCode = 200;
            }
            if (response == "Invalid"){
                responseCode = 404;
            }
        } else if (path.startsWith("/product")) {
            // forward /product requests to another service
            type = 0;
            response = forwardRequestToService(type, path, exchange.getRequestBody(),exchange.getRequestMethod());
            if (response == "Placeholder found.") {
                responseCode = 400;
            }
            if (response == "Invalid"){
                responseCode = 404;
            }
        } else if (path.startsWith("/user")) {
            // forward /users requests to another service
            type = 1;
           
            response = forwardRequestToService(type, path, exchange.getRequestBody(),exchange.getRequestMethod());
            if (response == "Placeholder found.") {
                responseCode = 400;
            }
            if (response == "Invalid"){
                responseCode = 404;
            }
        } else {
            response = "Unsupported endpoint.";
            responseCode = 404;
        }

        // send response back to client
        exchange.sendResponseHeaders(responseCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    // type 1 = User, type 0 = Product
    private static String forwardRequestToService(int type, String path, InputStream requestBody, String method) throws IOException {
        Map<String, String> serviceConfig;
        if (type == 0) {
            serviceConfig = readConfigFile("ProductService");
        } else {
            serviceConfig = readConfigFile("UserService");
        }
    
        int port = Integer.parseInt(serviceConfig.get("port"));
        String ip = serviceConfig.get("ip");
        String targetUrl = "http://" + ip + ":" + port + (type == 0 ? "/product" : "/user");

        // GET request forwarding
        if ("GET".equals(method)) {
            String getResponse;
            String[] paths = path.split("/");
            try {
                getResponse = sendGetRequest(targetUrl + '/' + paths[2]);
                System.out.println("Response Message: " + getResponse.toString());
                return getResponse;
            } catch (IOException e) {
                return "Invalid";
            }
        }

        HttpURLConnection connection = null;
        try {
            // Convert InputStream requestBody to String
            String body = new BufferedReader(new InputStreamReader(requestBody))
                            .lines().collect(Collectors.joining("\n"));
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
    

    // POST request handler to place Order, to be expanded on for a2
    private static String handleOrderRequest(InputStream requestBody) {
        try (Scanner scanner = new Scanner(requestBody, "UTF-8")) {
            // read the entire input stream into a single string
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);
            // get command in input, otherwise return Invalid
            String command = data.get("command");
            switch (command) {
                case "place order":
                    return placeOrder(data);
                default:
                    return "Invalid command.";
            }
        } catch (Exception e) {
            return "Invalid";
        }
    }

    // actual methods to manipulate data
    // method to create user
    // GET request: analyze quantity, analyze User, then POST request
    private static String placeOrder(Map<String, String> data) {
        int product_id = Integer.parseInt(data.get("product_id"));
        for (String value : data.values()) {
            if (value instanceof String && ((String) value).equals("placeholder")) {
                return "Placeholder found.";
            }
        }

        // declare userConfig here
        Map<String, String> userConfig;
        try {
            userConfig = readConfigFile("UserService");
        } catch (IOException e) {
            return "Error reading service configuration: " + e.getMessage();
        }

        int port = Integer.parseInt(userConfig.get("port"));
        String ip = userConfig.get("ip");
        String targetUrl = "http://" + ip + ":" + port + "/user";
        int user_id = Integer.parseInt(data.get("user_id"));
        int requestedQuantity = Integer.parseInt(data.get("quantity"));
        // Generate a random UUID for the order ID
        UUID order_id = UUID.randomUUID();

        // check if User exists
        String userResponse;
        try {
            userResponse = sendGetRequest(targetUrl + '/' + user_id);
        } catch (IOException e) {
            orders.put(order_id, new OrderData(order_id, product_id, user_id, requestedQuantity));
            return String.format("{\"id\": \"%s\", \"product_id\": %d, \"user_id\": %d, \"quantity\": %d, \"status\": \"Invalid Request\"}", order_id.toString(), product_id, user_id, requestedQuantity);
        }

        // Declare serviceConfig here
        Map<String, String> serviceConfig;
        try {
            serviceConfig = readConfigFile("ProductService");
        } catch (IOException e) {
            return "Error reading service configuration: " + e.getMessage();
        }

        port = Integer.parseInt(serviceConfig.get("port"));
        ip = serviceConfig.get("ip");
        targetUrl = "http://" + ip + ":" + port + "/product";

        String productResponse;
        try {
            productResponse = sendGetRequest(targetUrl + '/' + product_id);
        } catch (IOException e) {
            return "Error sending GET request: " + e.getMessage();
        }
        Map<String, String> productData = JSONParser(productResponse);
        int availableQuantity = Integer.parseInt((String) productData.get("quantity"));

        // Check if requested quantity is available
        if (requestedQuantity > availableQuantity) {
            // Process the order
            orders.put(order_id, new OrderData(order_id, product_id, user_id, requestedQuantity));
            return String.format("{\"id\": \"%s\", \"product_id\": %d, \"user_id\": %d, \"quantity\": %d, \"status\": \"Exceeded quantity limit\"}", order_id.toString(), product_id, user_id, requestedQuantity);
        }
        int quantity = availableQuantity - requestedQuantity;
        int id = Integer.parseInt((String) productData.get("id"));
        String name = productData.get("name");
        String description = productData.get("description");
        double price = Double.valueOf((String) productData.get("price"));
        
        // POST Request
        String postResponse;

        // create the JSON string using String.format
        String jsonInputString = String.format("{\"command\": \"update\", \"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, name, description, price, quantity);
        try {
            postResponse = sendPostRequest(targetUrl, jsonInputString);
        } catch (IOException e) {
            return "Error sending POST request: " + e.getMessage();
        }

        // process the order
        orders.put(order_id, new OrderData(order_id, product_id, user_id, requestedQuantity));
        return String.format("{\"id\": \"%s\", \"product_id\": %d, \"user_id\": %d, \"quantity\": %d, \"status\": \"Success\"}", order_id.toString(), product_id, user_id, requestedQuantity);
    }

    // ---Helper---
    private static String sendGetRequest(String urlString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("GET");

        int responseCode = connection.getResponseCode();
        if (responseCode != HttpURLConnection.HTTP_OK) {
            throw new IOException("User Does not Exist.");
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

    private static String sendPostRequest(String urlString, String jsonInputString) throws IOException {
        URL url = new URL(urlString);
        HttpURLConnection connection = (HttpURLConnection) url.openConnection();
        connection.setRequestMethod("POST");
        connection.setRequestProperty("Content-Type", "application/json");
        connection.setDoOutput(true);

        // Send the request body
        try (OutputStream os = connection.getOutputStream()) {
            byte[] input = jsonInputString.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        // Read the response
        int responseCode = connection.getResponseCode();
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                (responseCode < HttpURLConnection.HTTP_BAD_REQUEST) ? connection.getInputStream() : connection.getErrorStream(),
                StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        } finally {
            connection.disconnect();
        }
        return response.toString();
    }

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

    // extracts the configuration for service
    private static String extractServiceConfig(String json, String serviceName) {
        String servicePattern = "\"" + serviceName + "\": {";
        int startIndex = json.indexOf(servicePattern);
        if (startIndex == -1) {
            return "{}"; // service not found, return empty JSON object
        }
        startIndex += servicePattern.length() - 1; // move to the opening brace

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
}
