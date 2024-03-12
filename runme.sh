#!/bin/bash

#WINDOWS COMMANDS

# compile userService -> javac -cp './compiled/jarFiles/*;./src/userService' -d './compiled/userService' .\src\userService\userService.java
# run userService -> java -cp './compiled/jarFiles/*;./compiled/userService' src.userService.userService config.json

# compile orderService -> javac -cp './compiled/jarFiles/*;./src/orderService' -d './compiled/orderService' .\src\orderService\orderService.java
# run orderService -> java -cp './compiled/jarFiles/*;./compiled/orderService' src.orderService.orderService config.json

# compile productService -> javac -cp './compiled/jarFiles/*;./src/productService' -d './compiled/productService' .\src\productService\productService.java
# run productService -> java -cp './compiled/jarFiles/*;./compiled/productService' src.ProductService.productService config.json

# compile ISCS -> javac -cp './compiled/jarFiles/*;./src/ISCS' -d './compiled/ISCS' ./src/ISCS/ISCS.java
# run ISCS -> java -cp './compiled/jarFiles/*;./compiled/ISCS' src.ISCS.ISCS config.json

#run workloadparser -> py ./src/workloadparser/workloadparser.py config.json <workload file>

#LINUX COMMANDS
# compile userService (LINUX) javac -cp './compiled/jarFiles/*:./src/userService' -d './compiled/userService' ./src/userService/userService.java
# run userService (LINUX) ->  java -cp './compiled/jarFiles/*:./compiled/userService' src.userService.userService config.json

# compile orderService -> javac -cp './compiled/jarFiles/*:./src/orderService' -d './compiled/orderService' ./src/orderService/orderService.java
# run orderService -> java -cp './compiled/jarFiles/*:./compiled/orderService' src.orderService.orderService config.json

# compile productService -> javac -cp './compiled/jarFiles/*:./src/productService' -d './compiled/productService' ./src/ProductService/productService.java
# run productService -> java -cp './compiled/jarFiles/*:./compiled/productService' src.ProductService.productService config.json

# compile ISCS -> javac -cp './compiled/jarFiles/*:./src/ISCS' -d './compiled/ISCS' ./src/ISCS/ISCS.java
# run ISCS -> java -cp './compiled/jarFiles/*:./compiled/ISCS' src.ISCS.ISCS config.json

#run workloadparser -> python3 ./src/workloadparser/workloadparser.py config.json <workload file>
compile() {
    # Add compilation commands here
    echo "Compiling all code..."
    javac -cp './compiled/JarFiles/*:./src/UserService' -d './compiled/UserService' ./src/UserService/UserService.java
    javac -cp './compiled/JarFiles/*:./src/OrderService' -d './compiled/OrderService' ./src/OrderService/OrderService.java
    javac -cp './compiled/JarFiles/*:./src/ProductService' -d './compiled/ProductService' ./src/ProductService/ProductService.java
    javac -cp './compiled/JarFiles/*:./src/ISCS' -d './compiled/ISCS' ./src/ISCS/ISCS.java
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
    java -cp './compiled/JarFiles/*:./compiled/ISCS' -d src.ISCS.ISCS config.json
}

start_order_service() {
    # Add commands to start Order service here
    echo "Starting Order service..."
    java -cp './compiled/JarFiles/*:./compiled/OrderService' src.OrderService.OrderService config.json
}

start_workload_parser() {
    productWorkload="$1"
    # Add commands to start workload parser here
    echo "Starting workload parser with file: $productWorkload..."
    python3 ./compiled/workloadparser/workloadparser.py config.json $productWorkload
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

##########
#OLD CODE#
##########

# compile_code() {
#     echo "Compiling"

#     javac -d compiled/OrderService src/OrderService/OrderService.java
#     javac -d compiled/ProductService src/ProductService/ProductService.java
#     javac -d compiled/UserService src/UserService/UserService.java

# }

# start_user_service() {
#     echo "Starting User service"
#     java -cp compiled/UserService UserService
# }

# start_product_service() {
#     echo "Starting Product service"
#     java -cp compiled/ProductService ProductService
# }

# start_order_service() {
#     echo "Starting Order service"
#     java -cp compiled/OrderService OrderService
# }

# start_workload_parser() {
#     workload_file=$1
#     python3 workload_parser.py "$workload_file"
# }

# case "$1" in
#     -c)
#         compile_code
#         ;;
#     -u)
#         start_user_service
#         ;;
#     -p)
#         start_product_service
#         ;;
#     -i)
#         start_iscs
#         ;;
#     -o)
#         start_order_service
#         ;;
#     -w)
#         start_workload_parser "$2"
#         ;;
#     *)
#         echo "Usage: $0 {-c|-u|-p|-i|-o|-w <workloadfile>}"
#         exit 1
#         ;;
# esac
