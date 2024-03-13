package src.UserService;

import com.sun.net.httpserver.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.util.stream.Collectors;
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


public class UserService {

    private static String filename;

    private static Connection connection;

    private static HttpServer server;

    private static Boolean isStartingUp = true;

    // Initialize SQLite database connection
    private static void initializeConnection() throws SQLException {
        String url = "jdbc:sqlite:./compiled/db/data.db"; 
        connection = DriverManager.getConnection(url);
    }

    private static void initializeDatabase() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY, "
                + "username TEXT NOT NULL, "
                + "email TEXT, "
                + "password TEXT NOT NULL);"; 

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    //method to read config file
    public static Map<String, String> readConfigFile( String type) throws IOException {
        String configContent = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
        String userServiceConfigContent = extractServiceConfig(configContent, type);
        return JSONParser(userServiceConfigContent);
    }

    public static void main(String[] args) throws IOException {

        //setup connection
        try {
            initializeConnection(); 
            initializeDatabase();   
        } catch (SQLException e) {
            System.out.println("Failed to initialize database connection.");
            e.printStackTrace();
            return;
        }

        // use "config.json" as the default config file name
        filename = "config.json";
        /* // if an argument is provided, use it as the configuration file name
        if (args.length > 0) {
            configFileName = args[0]; // get filename in same path
        }

        // parse JSON to Map in helper
        String configContent = new String(Files.readAllBytes(Paths.get(configFileName)), "UTF-8");
        String userServiceConfigContent = extractServiceConfig(configContent, "UserService");
        Map<String, String> userServiceConfig = JSONParser(userServiceConfigContent);
        // get server port and IP from config.json
        int port = Integer.parseInt(userServiceConfig.get("port"));
        String ip = userServiceConfig.get("ip"); */

        System.out.println(Arrays.toString(args));

        int port = Integer.parseInt(args[1]);
        String ip = "127.0.0.1";

        forwardConfigToISCS(port, ip);

        // start server
        UserService.server = HttpServer.create(new InetSocketAddress(ip, port), 0);
        HttpContext context = UserService.server.createContext("/user");  // endpoint /user
        context.setHandler(UserService::handleRequest);
        UserService.server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");


        
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
                if (!response.contains("error")) {
                    responseCode = 200;
                }
                if (response.equals("User not found")) { 
                    responseCode = 404;
                }
                break;
                case "POST":
                response = handlePostRequest(exchange.getRequestBody());
                if (response.equals("Error Placeholder")) {
                    responseCode = 400;
                } else if (response.equals("User ID already exists")) {
                    responseCode = 409;
                } else if (response.equals("User not found")) {
                    responseCode = 404;
                } else if (response.equals("User data does not match")) {
                    responseCode = 401;
                } else if (response.equals("command:shutdown")) {
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
                response = "Unsupported method.";
                exchange.sendResponseHeaders(405, response.length());
        }
        // send response back to client
        exchange.sendResponseHeaders(responseCode, response.getBytes().length);
        OutputStream os = exchange.getResponseBody();
        os.write(response.getBytes());
        os.close();

        if (shutdown) {
            UserService.server.stop(0);
            System.exit(0);
        }

    }

    private static String handleGetRequest(URI requestURI) {
        String path = requestURI.getPath();
        String[] pathParts = path.split("/");
    
        // should be 3 parts for basic GET, localhost / user / USERID
        //System.out.println(path);
        if (pathParts.length == 3 && "user".equals(pathParts[1])) {
            try {
                int userId = Integer.parseInt(pathParts[2]);
                String sql = "SELECT username, email, password FROM users WHERE id = ?";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setInt(1, userId);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        if (rs.next()) {
                            return new Gson().toJson(Map.of(
                                "id", userId,
                                "username", rs.getString("username"),
                                "email", rs.getString("email"),
                                "password", rs.getString("password")
                            ));
                        }
                        return "User not found";
                    }
                }
            } catch (NumberFormatException e) {
                return "Invalid user ID.";
            } catch (SQLException e) {
                System.out.println(e.getMessage());
                return "Database error.";
            }
        }
        return "Invalid request.";
    }
    

    // POST request handler to create, update and delete USER
    private static String handlePostRequest(InputStream requestBody) {
        try (Scanner scanner = new Scanner(requestBody, "UTF-8")) {
            // read the entire input stream into a single string
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);
            // get command in input, otherwise return Invalid

            if ("placeholder".equals(data.get("username")) || 
                "placeholder".equals(data.get("email")) || 
                "placeholder".equals(data.get("password"))) {
            return "Error Placeholder";
            }

            System.out.println(body);

            String command = data.get("command");
            if (!command.equals("restart") && isStartingUp) {
                System.out.println("Starting from scratch");
                try (Statement statement = connection.createStatement()) {
                    statement.execute("DELETE FROM users WHERE 1=1");
                } catch (SQLException e) {
                    e.printStackTrace();
                }

                isStartingUp = false;
            }

            switch (command) {
                case "create":
                    return createUser(data);
                case "update":
                    return updateUser(data);
                case "delete":
                    return deleteUser(data);
                case "shutdown":
                    return "command:shutdown";
                case "restart":
                    return "command:restart";
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
        String username = data.get("username");
        String email = data.get("email");
        String password = data.get("password");
        String userIdString = data.get("id");
        int id = Integer.parseInt(userIdString);
    
        if ("placeholder".equals(username) || "placeholder".equals(email) || "placeholder".equals(password) || "placeholder".equals(userIdString)) {
            return "Error Placeholder";
        }
    
        // Hash the password
        String passwordHash = hashPassword(password);
    
        String sql = "INSERT INTO users(id, username, email, password) VALUES(?, ?, ?, ?)";
    
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, passwordHash);
            pstmt.executeUpdate();
            return new Gson().toJson(Map.of("id", id, "username", username, "email", email, "password", passwordHash));
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "User already exists";
        }
    }
    

    // method to update user
    private static String updateUser(Map<String, String> data) {
        int id = Integer.parseInt(data.get("id"));
        String username = data.get("username");
        String email = data.get("email");
        String password = data.get("password");
        String passwordHash = hashPassword(password);
    
        String sql = "UPDATE users SET username = ?, email = ?, password = ? WHERE id = ?";
    
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, username);
            pstmt.setString(2, email);
            pstmt.setString(3, passwordHash);
            pstmt.setInt(4, id);
            int affected = pstmt.executeUpdate();
            
            // Check if any rows were updated
            if (affected > 0) {
                return new Gson().toJson(Map.of("id", id, "username", username, "email", email, "password", passwordHash));
            } else {
                return "User not found";

            }
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
    
        String sql = "DELETE FROM users WHERE id = ? AND username = ? AND email = ? AND password = ?";
    
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, id);
            pstmt.setString(2, username);
            pstmt.setString(3, email);
            pstmt.setString(4, passwordHash);
            int affected = pstmt.executeUpdate();
    
            // Check if any rows were deleted
            if (affected > 0) {
                return new Gson().toJson(Map.of("message", "User deleted successfully"));
            } else {
                return "User data does not match";
            }
        } catch (SQLException e) {
            System.out.println(e.getMessage());
            return "User data does not match";
        }
    }

    private static String forwardConfigToISCS(int thisPort, String thisIP) throws IOException {
        Map<String, String> serviceConfig;
        serviceConfig = readConfigFile("InterServiceCommunication");
    
        int port = Integer.parseInt(serviceConfig.get("port"));
        String ip = serviceConfig.get("ip");
        String targetUrl = "http://" + ip + ":" + port + "/setup";

        HttpURLConnection connection = null;
        try {
            // Convert InputStream requestBody to String
            String body = new Gson().toJson(Map.of("IP", thisIP, "port", thisPort, "type", "user"));
            System.out.println("Sending POST request to: " + targetUrl + " with body: " + body);
    
            // Create URL and open connection
            URL url = new URL(targetUrl);
            connection = (HttpURLConnection) url.openConnection();
    
            // Set request method to the method received from the original request
            connection.setRequestMethod("POST");
    
            // If the original request is POST, we need to write the body to the output stream
            connection.setRequestProperty("Content-Type", "application/json");
            connection.setDoOutput(true);

            try (OutputStream os = connection.getOutputStream()) {
                byte[] input = body.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
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
            return "Error: Unable to connect to ISCS.";
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
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
