package src.ProductService;

import com.sun.net.httpserver.*;

//import src.UserService.UserService;

import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import com.google.gson.Gson;

public class ProductService {

    private static HttpServer server;

    private static Boolean isStartingUp = true;

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
            ProductService.server = HttpServer.create(new InetSocketAddress(ip, port), 0);
            HttpContext context = ProductService.server.createContext("/product");  // endpoint /product
            context.setHandler(ProductService::handleRequest);
            ProductService.server.start();
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
        Boolean shutdown = false;
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
                if (response.equals("Error Placeholder")) {
                    responseCode = 400;
                }
                else if (response == "Product already exists") {
                    responseCode = 409;
                }
                else if (response == "Price and quantity must be positive") {
                    responseCode = 400;
                }
                else if (response == "Product not found") {
                    responseCode = 404;
                }
                else if (response == "Product info does not match") {
                    responseCode = 404;
                }
                else if (response.equals("command:shutdown")) {
                    responseCode = 200;
                    response = new Gson().toJson(Map.of("command", "shutdown"));
                    shutdown = true;
                } else if (response.equals("command:restart")) {
                    if (isStartingUp) {
                        System.out.println("restarting");
                        responseCode = 200;
                        response = new Gson().toJson(Map.of("command", "restart"));
                        isStartingUp = false;
                    } else {
                        responseCode = 405;
                        response = "Invalid request command in restart";
                    }
                    
                } else {
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

        if (shutdown) {
            ProductService.server.stop(0);
            System.exit(0);
        }
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
                        return new Gson().toJson(Map.of(
                                "id", productId,
                                "productname", rs.getString("productname"),
                                "description", rs.getString("description"),
                                "price", rs.getString("price"),
                                "quantity", rs.getInt("quantity")
                            ));
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

            if (!command.equals("restart") && isStartingUp) {
                System.out.println("Starting from scratch");
                try (Connection conn = connect(); Statement statement = conn.createStatement()) {
                    statement.execute("DELETE FROM products WHERE 1=1");
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                isStartingUp = false;
            }
            switch (command) {
                case "create":
                    return createProduct(data);
                case "update":
                    return updateProduct(data);
                case "delete":
                    return deleteProduct(data);
                case "shutdown":
                    return "command:shutdown";
                case "restart":
                    return "command:restart";
                default:
                    return "Invalid command";
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
            return new Gson().toJson(Map.of(
                                "id", id,
                                "productname", productname,
                                "description", description,
                                "price", price,
                                "quantity", quantity
                            ));
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
                return new Gson().toJson(Map.of(
                                "id", id,
                                "productname", productname,
                                "description", description,
                                "price", price,
                                "quantity", quantity
                            ));
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
                return new Gson().toJson(Map.of("success", "Product deleted successfully"));
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
