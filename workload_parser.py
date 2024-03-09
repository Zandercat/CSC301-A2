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

def parse_user(parts, extracted_url):
    if parts[0] == "get":
        if len(parts) == 2:
            user_id = parts[1]
            url = f"{extracted_url}/user/{user_id}"
            make_get_request(url)
        else:
            print("Invalid GET request format.")
        return

    if len(parts) < 2:
        print("Missing command details for non-GET request.")
        return

    command = parts[0]
    
    user_id = parts[1] if len(parts) > 1 and parts[1].isdigit() else "placeholder"

    data = {
        "command": command,
        "id": user_id
    }

    if command == "create":
        while len(parts) < 5:
            parts.append("placeholder")

        user = parts[2] if parts[2] else "placeholder"
        email_candidate = parts[3]
        email = email_candidate if email_candidate.count('@') == 1 and '.' in email_candidate[email_candidate.index('@'):] else "placeholder"
        password = parts[4] if parts[4] else "placeholder"

        data.update({"username": user, "email": email, "password": password})

    elif command == "update":
        if user_id != "placeholder":
            if len(parts) > 2:
                data["username"] = parts[2] if not parts[2].isdigit() else "placeholder"
            
            if len(parts) > 3:
                email_candidate = parts[3]
                is_valid_email = email_candidate.count('@') == 1 and '.' in email_candidate[email_candidate.index('@'):]
                data["email"] = email_candidate if is_valid_email and not email_candidate.isdigit() else "placeholder"

            if len(parts) > 4:
                data["password"] = parts[4] if parts[4] else "placeholder"

    if command in ["create", "update", "delete"]:
        url = f"{extracted_url}/user"
        print(data)
        make_post_request(url, data)
    else:
        print("Unsupported command.")



        
def parse_product(parts, extracted_url):
    if parts[0] == "info" and len(parts) == 2:
        id = parts[1]
        url = f"{extracted_url}/product/{id}"
        make_get_request(url)
    elif parts[0] == "create":
        command, id, name = parts[:3]  
        description = "" 

        i = 3 
        while i < len(parts):
            part = parts[i]
            try:
                price = float(part)
                if i + 1 < len(parts):
                    quantity = int(parts[i + 1])
                break  
            except ValueError:
                description += ("" if description == "" else " ") + part
            i += 1 
        description = description.strip('"')

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
    print(parts)
    if parts[0] in "place order":
        current_size = len(parts)
        if current_size < 4:
            additional_elements = 4 - current_size
            parts.extend(["placeholder"] * additional_elements)
        command,user_id,product_id,quantity = parts
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