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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;
import com.google.gson.Gson;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ISCS {

    static class ServiceInstance {
        public int connections = 0;
        public String IP;
        public int port;

        public ServiceInstance(String IP, int port){
            this.IP = IP;
            this.port = port;
        }
    }
    private static Connection connection;

    private static ExecutorService executors;

    private static List<ServiceInstance> OrderServices = new ArrayList<ServiceInstance>();
    private static List<ServiceInstance> UserServices = new ArrayList<ServiceInstance>();
    private static List<ServiceInstance> ProductServices = new ArrayList<ServiceInstance>();

    private static Map<Integer, HttpExchange> exchanges;
    private static int lastID = 0;

    private static HttpServer server;

    private static String filename;

    private static void initializeConnection() throws SQLException {
        // Adjust the URL for SQLite
        String url = "jdbc:sqlite:./compiled/db/data.db";
        connection = DriverManager.getConnection(url);
    }
    private static void initializeDatabases() {
        String createTableSQL = "CREATE TABLE IF NOT EXISTS orders (" +
                "order_id INTEGER PRIMARY KEY AUTOINCREMENT,"  +
                "user_id INT NOT NULL," +
                "product_id INT NOT NULL," +
                "quantity INT NOT NULL," +
                "order_date TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP," +
                "FOREIGN KEY(user_id) REFERENCES users(user_id)," +
                "FOREIGN KEY(product_id) REFERENCES products(product_id))";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        createTableSQL = "CREATE TABLE IF NOT EXISTS users ("
                + "id INTEGER PRIMARY KEY, "
                + "username TEXT NOT NULL, "
                + "email TEXT, "
                + "password TEXT NOT NULL);";

        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }

        createTableSQL = "CREATE TABLE IF NOT EXISTS products (\n"
                + "	id integer PRIMARY KEY,\n"
                + "	productname text NOT NULL,\n"
                + "	description text NOT NULL,\n"
                + "	price real NOT NULL,\n"
                + "	quantity integer NOT NULL\n"
                + ");";
        try (Statement statement = connection.createStatement()) {
            statement.execute(createTableSQL);
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) throws IOException {
        //setup connection to database and all tables
        try {
            initializeConnection();
            initializeDatabases();
        } catch (SQLException e) {
            System.out.println("Failed to initialize database connection.");
            e.printStackTrace();
            return;
        }

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
        HttpContext setupServer = ISCS.server.createContext("/setup");

        orderContext.setHandler(ISCS::handleRequest);
        productContext.setHandler(ISCS::handleRequest);
        userContext.setHandler(ISCS::handleRequest);
        userPurchasedContext.setHandler(ISCS::handleRequest);
        setupServer.setHandler(ISCS::addServer);

        executors = Executors.newFixedThreadPool(10); //TODO: Change from hardcoded to config?

        //TODO: start multiple instances of each microservice
        //I'm not sure how to best do this or if they are supposed to all be on the same machine or different ones

        /*List<Map<String, String>> UserConfig = readConfigFileList("UserService");

        for (Map<String, String> map : UserConfig) {
            try {
                // runProcess("java -cp './compiled/JarFiles/*;./compiled/UserService' src.UserService.UserService", map.get("port"), map.get("ip"));
                String command = "java -cp ./compiled/JarFiles/*;./compiled/UserService src.UserService.UserService";
                Process pro = new ProcessBuilder("java", "-cp", "./compiled/JarFiles/*;./compiled/UserService", "src.UserService.UserService", map.get("port"), map.get("ip")).start();
                printLines(command + " stdout:", pro.getInputStream());
                printLines(command + " stderr:", pro.getErrorStream());
                // System.out.println(command + " exitValue() " + pro.exitValue());

            } catch (Exception e) {
                e.printStackTrace();
            }
            
        }*/

        ISCS.server.start();
        System.out.println("Server started on IP " + ip + ", and port " + port + ".");
    }

    private static void printLines(String cmd, InputStream ins) throws Exception {
        String line = null;
        BufferedReader in = new BufferedReader(
            new InputStreamReader(ins));
        while ((line = in.readLine()) != null) {
            System.out.println(cmd + " " + line);
        }
    }

    private static void handleRequest(HttpExchange exchange) throws IOException {
        final int exchangeID = lastID;
        lastID++;
        //save the exchange with the current client request
        exchanges.put(exchangeID, exchange);
        //pass the handling of this exchange off to the thread pool to be picked up and handled by a thread
        System.out.println("Handling request on exchange " + exchangeID);
        executors.submit(new Runnable() {
            @Override
            public void run() {
                System.out.println("Creating thread...");
                String method = exchange.getRequestMethod();
                InputStream requestBody = exchange.getRequestBody();

                Scanner scanner = new Scanner(requestBody, StandardCharsets.UTF_8);
                String body = scanner.useDelimiter("\\A").next();

                //determine whether request is a user, order or product request by extracting the first slash and after
                URI requestURI = exchange.getRequestURI();
                String path = requestURI.getPath();
                String endpoint = path.substring(path.indexOf("/") + 1);

                List<ServiceInstance> services = null;
                switch (endpoint.substring(0, endpoint.indexOf("/"))) {
                    case "product" -> services = ProductServices;
                    case "user" -> services = UserServices;
                    case "order" -> services = OrderServices;
                }

                //get the service with the least active connections and make a new connection to it
                ServiceInstance service = getLeastBusyService(services);
                service.connections++;

                int port = service.port;
                String ip = service.IP;

                String targetUrl = "http://" + ip + ":" + port + "/" + endpoint;
                // (yeah, we re-add the / after stripping it, makes the substring stuff for services easier)

                HttpURLConnection connection = null;
                try {
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

                    StringBuilder responseBuilder = new StringBuilder();
                    try (BufferedReader br = new BufferedReader(
                            new InputStreamReader(responseStream, StandardCharsets.UTF_8))) {
                        String responseLine;
                        while ((responseLine = br.readLine()) != null) {
                            responseBuilder.append(responseLine.trim());
                        }
                    }

                    String response = responseBuilder.toString();

                    //end connection and mark it as available
                    connection.disconnect();
                    service.connections--;

                    //TODO: once you are 100% sure everything is working, comment out all debug output for performance
                    System.out.println("Response Code: " + responseCode);
                    System.out.println("Response Message: " + response);

                    //return response to the saved exchange
                    HttpExchange exchange = exchanges.get(exchangeID);
                    exchange.sendResponseHeaders(responseCode, response.getBytes().length);
                    OutputStream os = exchange.getResponseBody();
                    os.write(response.getBytes());
                    try {
                        os.close();
                    } catch (IOException e) {
                        System.out.println("Error when closing output stream.");
                        e.printStackTrace();
                    }

                    exchanges.remove(exchangeID);

                } catch (IOException e) {
                    System.out.println("Error in handling connection.");
                    e.printStackTrace();
                }
            }
        });
    }

    // when a service is started up, it should send one of these requests to the ISCS HTTP server
    // with a body of {"IP": "<ip address>", "port": "<port>", "type": "user/product/order"}
    // so that the ISCS can register it and communicate with it
    // TODO: Make services do this
    public static void addServer(HttpExchange exchange) throws IOException {
        InputStream requestBody = exchange.getRequestBody();
        try (Scanner scanner = new Scanner(requestBody, StandardCharsets.UTF_8)) {
            String body = scanner.useDelimiter("\\A").next();
            Map<String, String> data = JSONParser(body);

            String type = data.get("type");
            ServiceInstance service = new ISCS.ServiceInstance(data.get("IP"), Integer.parseInt(data.get("port")));
            switch (type) {
                case "user":
                    ISCS.UserServices.add(service);
                    break;

                case "product":
                    ISCS.ProductServices.add(service);
                    break;

                case "order":
                    ISCS.OrderServices.add(service);
                    break;
            }
            System.out.println("Server added on IP " + data.get("IP") + ", and port " + data.get("port") + ".");

        } catch (Exception e) {
            return;
        }
    }

    // extracts the configuration for service
    private static String extractServiceConfig(String json, String serviceName) {
        String servicePattern = "\"" + serviceName + "\": [";
        int startIndex = json.indexOf(servicePattern);
        if (startIndex == -1) {
            return "[]"; // service not found, return empty JSON object
        }
        startIndex += servicePattern.length() - 1; // move to the opening brace

        int bracesCount = 1;
        int endIndex = startIndex;
        while (endIndex < json.length() && bracesCount > 0) {
            endIndex++;
            char ch = json.charAt(endIndex);
            if (ch == '[') {
                bracesCount++;
            } else if (ch == ']') {
                bracesCount--;
            }
        }
        return json.substring(startIndex, endIndex + 1); // include closing brace
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

    private static List<Map<String, String>> JSONListParser(String json) {
        List<Map<String, String>> datalist = new ArrayList<Map<String, String>>();
        json = json.trim().replace("[", "").replace("]", "");
        String[] maps = json.split("\\} *, *\\{");
        for (String map : maps) {
            Map<String, String> data = JSONParser(map);
            datalist.add(data);
        }
        return datalist;
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

    public static List<Map<String, String>> readConfigFileList( String type) throws IOException {
        String configContent = new String(Files.readAllBytes(Paths.get(filename)), "UTF-8");
        String userServiceConfigContent = extractServiceConfig(configContent, type);
        return JSONListParser(userServiceConfigContent);
    }

    public static ServiceInstance getLeastBusyService(List<ServiceInstance> services) {
        int index = 0;
        int min = -1;
        int connections;
        for (int i = 0; i < services.size(); i++) {
            connections = services.get(i).connections;
            if (min < 0 || connections < min) {
                min = connections;
                index = i;
            }
        }
        return services.get(index);
    }

}