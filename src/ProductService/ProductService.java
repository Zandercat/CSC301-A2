package src.ProductService;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.*;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.text.ParseException;

public class ProductService {

    public static void main(String[] args) throws IOException {
        try {
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

            createNewTable();

            // start server
            HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
            HttpContext context = server.createContext("/product");  // endpoint /product
            context.setHandler(ProductService::handleRequest);
            server.start();
            System.out.println("Server started on IP " + ip + ", and port " + port + ".");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void createNewTable() {
        String url = "jdbc:sqlite:./compiled/db/data.db";
        
        // SQL statement for creating a new table
        String sql = "CREATE TABLE IF NOT EXISTS products (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	productname text NOT NULL,\n"
                + "	description text NOT NULL,\n"
                + "	price real NOT NULL,\n"
                + "	quantity integer NOT NULL\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement()) {
            // create a new table
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    private static Connection connect() {
        // SQLite connection string
        String url = "jdbc:sqlite:./compiled/db/data.db";
        Connection conn = null;
        try {
            conn = DriverManager.getConnection(url);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
        return conn;
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

    //return String.format("{\"id\": %d, \"name\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", product.id, product.name, product.description, product.price, product.quantity);

    // GET request handler
    private static String handleGetRequest(URI requestURI) {
        String path = requestURI.getPath();
        String[] pathParts = path.split("/");

        // should be 3 parts for GET, localhost / product / PRODUCTID
        if (pathParts.length == 3 && pathParts[1].equals("product")) {
            try {
                int productId = Integer.parseInt(pathParts[2]);
                String sql = "SELECT productname, description, price, quantity FROM products WHERE id = " + Integer.toString(productId);
                System.out.println(sql);
                try (Connection conn = connect();
                    Statement stmt  = conn.createStatement();
                    ResultSet rs    = stmt.executeQuery(sql)){
                    
                    // loop through the result set
                    while (rs.next()) {
                        return String.format("{\"id\": %d, \"productname\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", 
                                             productId, 
                                             rs.getString("productname"), 
                                             rs.getString("description"), 
                                             rs.getFloat("price"),
                                             rs.getInt("quantity"));
                    }
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
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

        String productname = data.get("productname");
        String description = data.get("description");
        double price = Double.parseDouble(data.get("price"));
        int quantity = Integer.parseInt(data.get("quantity"));
        if (price < 0 || quantity < 0) {
            return "Price and quantity must be positive";
        }

        String sql = "INSERT INTO products(id,productname,description,price,quantity) VALUES(?,?,?,?,?)";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, productname);
            pstmt.setString(3, description);
            pstmt.setDouble(4, price);
            pstmt.setInt(5, quantity);
            pstmt.executeUpdate();
            return String.format("{\"id\": %d, \"productname\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, productname, description, price, quantity);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "Product already exists";
        }
    }

    // method to update product
    private static String updateProduct(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id")); // convert

        String productname = data.get("productname");
        String description = data.get("description");
        double price = Double.parseDouble(data.get("price"));
        int quantity = Integer.parseInt(data.get("quantity"));
        if (price < 0 || quantity < 0) {
            return "Price and quantity must be positive";
        }
        
        String sql = "UPDATE products SET productname = ? , "
                    + "description = ? , "
                    + "price = ? , "
                    + "quantity = ? "
                    + "WHERE id = ?";

                    try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {

                // set the corresponding param
                pstmt.setString(1, productname);
                pstmt.setString(2, description);
                pstmt.setDouble(3, price);
                pstmt.setInt(4, quantity);
                pstmt.setInt(5, id);
                // update 
                pstmt.executeUpdate();
                return String.format("{\"id\": %d, \"productname\": \"%s\", \"description\": \"%s\", \"price\": %.2f, \"quantity\": %d}", id, productname, description, price, quantity);
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                return "Product already exists";
            }
    }

    // method to delete product only if all info are matched
    private static String deleteProduct(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));
        String productname = data.get("productname");
        double price = Double.parseDouble(data.get("price"));
        int quantity = Integer.parseInt(data.get("quantity"));

        String sql = "DELETE FROM products WHERE id = ? "
                        + "AND productname = ? "
                        + "AND price = ? "
                        + "AND quantity = ?";

            try (Connection conn = connect();
                    PreparedStatement pstmt = conn.prepareStatement(sql)) {

                // set the corresponding param
                pstmt.setInt(1, id);
                pstmt.setString(2, productname);
                pstmt.setDouble(3, price);
                pstmt.setInt(4, quantity);
                // execute the delete statement
                pstmt.executeUpdate();
                return "{Product deleted successfully}";
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                return "Product info does not match";
            }
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
