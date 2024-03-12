import requests
import sys
import json


def load_json(path):
    file = open(path, 'r')
    config = json.load(file)
    return config

def make_post_request(url, data):
    try:
        headers = {'Content-Type': 'application/json'}
        response = requests.post(url, json=data, headers=headers)
        
        if response.status_code == 200:
            print("POST request successful. Response text:", response.text)
        else:
            print("POST request failed. Status Code:", response.status_code)
    except Exception as e:
        print("An error occurred:", e)

def make_get_request(url):
    try:
        headers = {'Authorization': 'Bearer your_token'}
        response = requests.get(url, headers=headers)

        if response.status_code == 200:
            print(f"GET request was successful: {response.status_code}")
            print("Response:", response.text)
        else:
            print(f"GET request failed: {response.status_code}")
            print("Response:", response.text)
    except Exception as e:
        print(e)

def parse_file(f):
    file = open(f)
    config = load_json("config.json")
    order_service= config['OrderService']
    extracted_url = f"http://{order_service['ip']}:{order_service['port']}"
    

    for line in file:
        if line == '\n':
            continue
        parts = line.strip().split()

        item = parts[0]
        if item == "USER":
            parse_user(parts[1:],extracted_url)
        elif item == "PRODUCT":
            parse_product(parts[1:],extracted_url)
        elif item == "ORDER":
            parse_order(parts[1:],extracted_url)
        elif item == "shutdown" or item =="reload":
            power(item,extracted_url)
        else:
            print("Error: Unknown Value: }")

def parse_user(parts,extracted_url):
    
    if parts[0] == "get" and len(parts) == 2:
        id = parts[1]
        url = f"{extracted_url}/user/{id}"
        make_get_request(url)
    
    elif parts[0] != "get":
        current_size = len(parts)

        if current_size < 5:
            additional_elements = 5 - current_size
            parts.extend(["placeholder"] * additional_elements)

        command,id,user,email,password =parts
        if parts[0] == "create" or parts[0] == "delete":
            data = {
            "command": parts[0],
            "id": id,
            "username": user,
            "email": email,
            "password": password
        }
            url = f"{extracted_url}/user"
            make_post_request(url, data)
        elif command == "update":
            id = parts[1]  
            data = {"command": command, "id": id}

            for part in parts[2:]:
                if ':' in part:
                    key, value = part.split(':', 1)
                    data[key] = value

            url = f"{extracted_url}/user"
            print(data)
            make_post_request(url, data)

    else:
        print("Improper Format")
        
def parse_product(parts, extracted_url):
    if parts[0] == "info" and len(parts) == 2:
        id = parts[1]
        url = f"{extracted_url}/product/{id}"
        make_get_request(url)
    elif parts[0] == "create" :
        
        current_size = len(parts)
        if current_size < 6:
            additional_elements = 6 - current_size
            parts.extend(["placeholder"] * additional_elements)
        
        command, id, name, description, price, quantity = parts
        data = {
            "command": command,
            "id": id,
            "name": name,
            "description": description,
            "price": price,
            "quantity": quantity
        }
        
        url = f"{extracted_url}/product"
        make_post_request(url, data)

    elif parts[0] == "DELETE":

        current_size = len(parts)
        parts[0] = "delete"
        if current_size < 5:
            additional_elements = 5 - current_size
            print(additional_elements)
            parts.extend(["placeholder"] * additional_elements)

        print(parts)
        command, id, name, price, quantity = parts
        data = {
            "command": command,
            "id": id,
            "name": name,
            "price": price,
            "quantity": quantity
        }
        url = f"{extracted_url}/product"
        make_post_request(url, data)

    elif parts[0] == "update":
        command = parts[0]
        id = parts[1]
        data = {"command": command, "id": id}

        for i in parts[2:]:
            key, value = i.split(':')
            data[key] = value

        url = f"{extracted_url}/product"
        print(data)
        make_post_request(url, data)

    else:
        print("Improper Format")

def parse_order(parts,extracted_url):
    if parts[0] == "place" or parts[0] == "place order":
        current_size = len(parts)
        if current_size < 4:
            additional_elements = 4 - current_size
            parts.extend(["placeholder"] * additional_elements)
        command,product_id,user_id,quantity = parts
        data = {
            "command": "place order",
            "product_id": product_id,
            "user_id": user_id,
            "quantity": quantity,
        }

        url = f"{extracted_url}/order"
        make_post_request(url,data)
    
    else:
        print("Improper Format")
        

def power(parts,extracted_url):
    if parts[0] == "shutdown":
        data = {
            "command": "shutdown",
        }
    else:
        data = {
            "command": "restart",
        }

    url = f"{extracted_url}/order"
    make_post_request(url,data)
    

if __name__ == "__main__":
    if len(sys.argv) > 1:
        file = sys.argv[1]
        parse_file(file)
    else:
        print("Please provide a file path.")