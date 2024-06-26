package src.UserService;

import com.sun.net.httpserver.*;
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

public class UserService {

    // class UserData to store data of users, stores id, username, email and password
    static class UserData {
        public int id;
        public String username;
        public String email;
        public String password;
        public Map<String, Integer> purchases;

        // UserData constructor setting user details
        public UserData(int id, String username, String email, String password) {
            this.id = id;
            this.username = username;
            this.email = email;
            this.password = password;
            this.purchases = Collections.emptyMap(); //new users have no purchase history
        }
    }

    public static void main(String[] args) throws IOException {
        // use "config.json" as the default config file name
        String configFileName = "config.json";
        // if an argument is provided, use it as the configuration file name
        if (args.length > 0) {
            configFileName = args[0]; // get filename in same path
        }

        // parse JSON to Map in helper
        String configContent = new String(Files.readAllBytes(Paths.get(configFileName)), "UTF-8");
        String userServiceConfigContent = extractServiceConfig(configContent, "UserService");
        Map<String, String> userServiceConfig = JSONParser(userServiceConfigContent);
        // get server port and IP from config.json
        int port = Integer.parseInt(userServiceConfig.get("port"));
        String ip = userServiceConfig.get("ip");

        // start server
        HttpServer server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        HttpContext context = server.createContext("/user");  // endpoint /user
        context.setHandler(UserService::handleRequest);
        server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");

        // TODO: create table
        createNewTable();
        
    }

    // Create a new table in the test database
    private static void createNewTable() {
        String url = "jdbc:sqlite:./compiled/db/data.db";
        
        // SQL statement for creating a new table
        String sql = "CREATE TABLE IF NOT EXISTS users (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	username text NOT NULL,\n"
                + "	email text NOT NULL,\n"
                + "	password text NOT NULL\n"
                + ");";
        try (Connection conn = DriverManager.getConnection(url);
                Statement stmt = conn.createStatement()) {
            // create a new table
            stmt.execute(sql);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    // database connection method
    private static Connection connect() {
        // postgresql connection string
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
                if (response == "User not found") {
                    responseCode = 404;
                }
                break;
            case "POST":
                response = handlePostRequest(exchange.getRequestBody());
                if (response == "User already exists") {
                    responseCode = 409;
                }
                if (response == "User not found") {
                    responseCode = 404;
                }
                if (response == "User data does not match") {
                    responseCode = 401;
                }
                if (response.charAt(0) == '{') {
                    responseCode = 200;
                }
                break;
            default:
                response = "Unsupported method.";
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

        // should be 3 parts for basic GET, localhost / user / USERID
        if (pathParts.length == 3 && pathParts[1].equals("user")) {
            try {
                int userId = Integer.parseInt(pathParts[2]);
                String sql = "SELECT username, email, password FROM users WHERE id = " + Integer.toString(userId);
                System.out.println(sql);
                try (Connection conn = connect();
                    Statement stmt  = conn.createStatement();
                    ResultSet rs    = stmt.executeQuery(sql)){
                    
                    // loop through the result set
                    while (rs.next()) {
                        return String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", 
                                             userId, 
                                             rs.getString("username"), 
                                             rs.getString("email"), 
                                             rs.getString("password"));
                    }
                } catch (SQLException e) {
                    System.out.println(e.getMessage());
                }
                return "User not found";
            } catch (NumberFormatException e) {
                return "Invalid user ID.";
            }
        } else if (pathParts.length == 4 && pathParts[1].equals("user") && pathParts[2].equals("purchased")) {
            try {
                /* int userID = Integer.parseInt(pathParts[3]);
                UserData user = users.get(userID);
                if (user != null) {
                    // return purchased information in format
                    Map<String, Integer> purchases = user.purchases;
                    String purchaseHistory = "{";
                    for (Map.Entry<String, Integer> entry : purchases.entrySet()) {
                        purchaseHistory.concat(String.format("\"%s\": %d, ", entry.getKey(), entry.getValue()));
                    }
                    purchaseHistory = purchaseHistory.substring(0, purchaseHistory.length() - 1) + '}';
                    return purchaseHistory;
                } */
            } catch (NumberFormatException e) {
                return "Invalid user ID.";
            }
        }
        return "Invalid request."; // invalid request if not according to format
    }

    // POST request handler to create, update and delete USER
    private static String handlePostRequest(InputStream requestBody) {
        try (Scanner scanner = new Scanner(requestBody, "UTF-8")) {
            // read the entire input stream into a single string
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);
            // get command in input, otherwise return Invalid
            String command = data.get("command");
            switch (command) {
                case "create":
                    return createUser(data);
                case "update":
                    return updateUser(data);
                case "delete":
                    return deleteUser(data);
                default:
                    return "Invalid command.";
            }
        } catch (Exception e) {
            return "Invalid request.";
        }
    }

    // actual methods to manipulate user data
    // method to create user
    private static String createUser(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id")); // convert

        String username = data.get("username");
        String email = data.get("email");
        String password = data.get("password");
        // return USER information in format
        String passwordHash = hashPassword(password);

        String sql = "INSERT INTO users(id,username,email,password) VALUES(?,?,?,?)";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, passwordHash);
            pstmt.executeUpdate();
            return String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", id, username, email, passwordHash);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "User already exists";
        }
    }

    // method to update user
    private static String updateUser(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));

        // return USER information in format
        String username = data.get("username");
        String email = data.get("email");
        String password = data.get("password");
        String passwordHash = hashPassword(password);

        String sql = "UPDATE users SET username = ? , "
                    + "email = ? , "
                    + "password = ? "
                    + "WHERE id = ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, passwordHash);
            pstmt.setInt(4, id);
            // update 
            pstmt.executeUpdate();
            return String.format("{\"id\": %d, \"username\": \"%s\", \"email\": \"%s\", \"password\": \"%s\"}", id, username, email, passwordHash);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "User not found";
        }
    }

    // method to delete user only if all details are matched
    private static String deleteUser(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));

        String username = data.get("username");
        String email = data.get("email");
        String password = data.get("password");
        String passwordHash = hashPassword(password);

        String sql = "DELETE FROM users WHERE id = ? "
                    + "AND username = ? "
                    + "AND email = ? "
                    + "AND password = ?";

        try (Connection conn = connect();
                PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // set the corresponding param
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, passwordHash);
            // execute the delete statement
            pstmt.executeUpdate();
            return "{User deleted successfully}";

        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "User data does not match";
        }
    }

    // ---Helper---
    // SHA-256 Hasher
    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(password.getBytes(StandardCharsets.UTF_8));
            return bytesToHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to hash password", e);
        }
    }

    // hash helper
    private static String bytesToHex(byte[] hash) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : hash) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
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
