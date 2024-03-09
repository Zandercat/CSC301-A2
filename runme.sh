#!/bin/bash

LOGGING_API="./slf4j-simple-{version}.jar"
JDBC_DRIVER="./sqlite-jdbc-3.45.1.0.jar"  
API_PATH="./slf4j-api-1.7.36.jar"
GSON_JAR="./gson-2.8.5.jar" 

compile_code() {
    echo "Compiling"

    javac -cp ".:$GSON_JAR" -d compiled/OrderService src/OrderService/OrderService.java
    javac -cp ".:$GSON_JAR" -d compiled/ProductService src/ProductService/ProductService.java
    javac -cp ".:$GSON_JAR" -d compiled/UserService src/UserService/UserService.java
}

start_user_service() {
    echo "Starting User service"
    java -cp "compiled/UserService:$JDBC_DRIVER:$API_PATH:$GSON_JAR" UserService
}

start_product_service() {
    echo "Starting Product service"
    java -cp "compiled/ProductService:$JDBC_DRIVER:$API_PATH:$GSON_JAR" ProductService
}

start_order_service() {
    echo "Starting Order service"
    java -cp "compiled/OrderService:$JDBC_DRIVER:$API_PATH:$GSON_JAR" OrderService
}

start_workload_parser() {
    workload_file=$1
    python3 workload_parser.py "$workload_file"
}

case "$1" in
    -c)
        compile_code
        ;;
    -u)
        start_user_service
        ;;
    -p)
        start_product_service
        ;;
    -o)
        start_order_service
        ;;
    -w)
        start_workload_parser "$2"
        ;;
    *)
        echo "Usage: $0 {-c|-u|-p|-o|-w <workloadfile>}"
        exit 1
        ;;
esac