#!/bin/bash
compile() {
    # Add compilation commands here
    echo "Compiling all code..."
    javac -cp './compiled/JarFiles/*:./src/UserService' -d './compiled/UserService' ./src/UserService/UserService.java
    javac -cp './compiled/JarFiles/*:./src/OrderService' -d './compiled/OrderService' ./src/OrderService/OrderService.java
    javac -cp './compiled/JarFiles/*:./src/ProductService' -d './compiled/ProductService' ./src/ProductService/ProductService.java
    echo "Downloading JAR files..."
    wget -P ./compiled/JarFiles/ https://repo1.maven.org/maven2/org/xerial/sqlite-jdbc/3.45.0.0/sqlite-jdbc-3.45.0.0.jar
}

start_user_service() {
    # Add commands to start User service here
    echo "Starting User service..."
    java -cp './compiled/JarFiles/*:./compiled/UserService' src.UserService.UserService config.json
}

start_product_service() {
    # Add commands to start Product service here
    echo "Starting Product service..."
    java -cp './compiled/JarFiles/*:./compiled/ProductService' src.ProductService.ProductService config.json
}

start_iscs() {
    # Add commands to start ISCS here
    echo "Starting ISCS..."
    python3 ./compiled/ISCS/iscs.py config.json
}

start_order_service() {
    # Add commands to start Order service here
    echo "Starting Order service..."
    java -cp './compiled/JarFiles/*:./compiled/OrderService' src.OrderService.OrderService config.json
}

start_workload_parser() {
    workload_file=$1
    python3 workload_parser.py "$workload_file"
}

usage() {
    echo "Usage: ./runme.sh [-c | -u | -p | -i | -o | -w workloadfile]"
    echo "  -c              : Compile all code"
    echo "  -u              : Start User service"
    echo "  -p              : Start Product service"
    echo "  -i              : Start ISCS"
    echo "  -o              : Start Order service"
    echo "  -w workloadfile : Start workload parser with specified file"
    exit 1
}

if [ "$#" -eq 0 ]; then
    usage
fi

while getopts ":cupiow:" option; do
    case $option in
        c)
            compile
            ;;
        u)
            start_user_service
            ;;
        p)
            start_product_service
            ;;
        i)
            start_iscs
            ;;
        o)
            start_order_service
            ;;
        w)
            workload_file="$OPTARG"
            start_workload_parser "$workload_file"
            ;;
        \?)
            echo "Invalid option: -$OPTARG" >&2
            usage
            ;;
        :)
            echo "Option -$OPTARG requires an argument." >&2
            usage
            ;;
    esac
done

