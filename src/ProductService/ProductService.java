import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public class ProductService {

    // class ProductData to store products
    static class ProductData {
        public int id;
        public String name;
        public String description;
        public double price;
        public int quantity;

        // ProductData constructor
        public ProductData(int id, String name, String description, double price, int quantity) {
            this.id = id;
            this.name = name;
            this.description = description;
            this.price = price;
            this.quantity = quantity;
        }
    }

    // using map to store products, as a simple memory database for A1
    private static Map<Integer, ProductData> products = new HashMap<>();

    public static void main(String[] args) throws IOException {
        // use "config.json" as the default config file name
        String configFileName = "config.json";
        // if an argument is provided, use it as the configuration file name
        if (args.length > 0) {
            configFileName = args[0]; // get filename in same path
        }

        // parse JSON to Map in helper
        String configContent = new String(Files.readAllBytes(Paths.get(configFileName)), "UTF-8");
        String productServiceConfigContent = extractServiceConfig(configContent, "ProductService");
        Map<String, String> productServiceConfig = JSONParser(productServiceConfigContent);
        // get server port and IP from config.json
        int port = Integer.parseInt(productServiceConfig.get("port"));
        String ip = productServiceConfig.get("ip");

        // start server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        HttpContext context = server.createContext("/product");  // endpoint /product
        context.setHandler(ProductService::handleRequest);
        server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");
    }

    // handler method for all HTTP requests
    private static void handleRequest(HttpExchange exchange) throws IOException {
        String requestMethod = exchange.getRequestMethod();
        String response = "";
        int responseCode = 200;
        // determine request type and pass to handler
        switch (requestMethod) {
            case "GET":
                response = handleGetRequest(exchange.getRequestURI());
                if (response.charAt(0) == '{') {
                    responseCode = 200;
                }
                if (response == "Product not found") {
                    responseCode = 404;
                }
                break;
            case "POST":
                response = handlePostRequest(exchange.getRequestBody());
                if (response == "Product already exists") {
                    responseCode = 409;
                }
                if (response == "Price and quantity must be positive") {
                    responseCode = 400;
                }
                if (response == "Product not found") {
                    responseCode = 404;
                }
                if (response == "Product info does not match") {
                    responseCode = 404;
                }
                if (response.charAt(0) == '{') {
                    responseCode = 200;
                }
                break;
            default:
                response = "Unsupported method";
                exchange.sendResponseHeaders(405, response.length());
        }
        // send response back to client
        exchange.sendResponseHeaders(responseCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();
    }

    // GET request handler
    private static String handleGetRequest(URI requestURI) {
        String path = requestURI.getPath();
        String[] pathParts = path.split("/");

        // should be 3 parts for GET, localhost / product / PRODUCTID
        if (pathParts.length == 3 && pathParts[1].equals("product")) {
            try {
                int productId = Integer.parseInt(pathParts[2]);
                ProductData product = products.get(productId);
                if (product != null) {
                    // return PRODUCT information in format
                    return String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", product.id, product.name, product.description, product.price, product.quantity);
                }
                return "Product not found";
            } catch (NumberFormatException e) {
                return "Invalid product ID";
            }
        }

        return "Invalid request";
    }

    // POST request handler to create, update and delete PRODUCT
    private static String handlePostRequest(InputStream requestBody) {
        try (Scanner scanner = new Scanner(requestBody, StandardCharsets.UTF_8.name())) {
            // read the entire input stream into a single string
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);
            // get command in input, otherwise return Invalid
            String command = data.get("command");
            switch (command) {
                case "create":
                    return createProduct(data);
                case "update":
                    return updateProduct(data);
                case "delete":
                    return deleteProduct(data);
                default:
                    return "Invalid command.";
            }
        } catch (Exception e) {
            return "Invalid request.";
        }
    }

    // actual methods to manipulate product data
    // method to create product
    private static String createProduct(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id")); // convert

        if (products.containsKey(id)) {
            return "Product already exists";
        }

        String name = data.get("name");
        String description = data.get("description");
        double price = Double.parseDouble(data.get("price"));
        int quantity = Integer.parseInt(data.get("quantity"));
        if (price < 0 || quantity < 0) {
            return "Price and quantity must be positive";
        }

        products.put(id, new ProductData(id, name, description, price, quantity));
        // return PRODUCT information in format
        return String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, name, description, price, quantity);
    }

    // method to update product
    private static String updateProduct(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));
        if (!products.containsKey(id)) {
            return "Product not found";
        }

        ProductData product = products.get(id);
        if (data.containsKey("name")) product.name = data.get("name");
        if (data.containsKey("description")) product.description = data.get("description");
        if (data.containsKey("price")) product.price = Double.parseDouble(data.get("price"));
        if (data.containsKey("quantity")) product.quantity = Integer.parseInt(data.get("quantity"));

        // return PRODUCT information in format
        return String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, product.name, product.description, product.price, product.quantity);
    }

    // method to delete product only if all info are matched
    private static String deleteProduct(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));
        if (!products.containsKey(id)) {
            return "Product not found";
        }

        // strict checker to ensure matching
        ProductData product = products.get(id);
        if (product.name.equals(data.get("name")) && product.price == Double.valueOf(data.get("price")) && String.valueOf(product.quantity).equals(data.get("quantity"))) {
            products.remove(id);
            return "{Product deleted successfully}";
        }
        return "Product info does not match";
    }

    // ---Helper---
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
