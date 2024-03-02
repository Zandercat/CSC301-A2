#!/bin/bash

compile_code() {
    echo "Compiling"

    javac -d compiled/OrderService src/OrderService/OrderService.java
    javac -d compiled/ProductService src/ProductService/ProductService.java
    javac -d compiled/UserService src/UserService/UserService.java

}

start_user_service() {
    echo "Starting User service"
    java -cp compiled/UserService UserService
}

start_product_service() {
    echo "Starting Product service"
    java -cp compiled/ProductService ProductService
}

start_order_service() {
    echo "Starting Order service"
    java -cp compiled/OrderService OrderService
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
    -i)
        start_iscs
        ;;
    -o)
        start_order_service
        ;;
    -w)
        start_workload_parser "$2"
        ;;
    *)
        echo "Usage: $0 {-c|-u|-p|-i|-o|-w <workloadfile>}"
        exit 1
        ;;
esac
