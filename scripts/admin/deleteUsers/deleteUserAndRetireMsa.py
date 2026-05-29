import csv
import requests
import sys
import os


# Define the CSV file names
input_csv_file = sys.argv[1] or "deleteUser.csv"
output_csv_file = sys.argv[2] or "deleteUserOutput.csv"

# Define the API endpoint URL
url_base = os.environ.get('DELETE_USER_URL_BASE') or "http://localhost:8080"
api_url = url_base + "/api/admin/deleteUserAndRetireMsa"

# Define the global secret key
shared_secret = os.environ.get('DELETE_USER_SHARED_SECRET') or "shared_secret"

print("Deleting Users with info from: " + input_csv_file)
print("Calling api at: " + api_url + " with shared secret: ********" + shared_secret[len(shared_secret) - 4:])
print("Putting results into: " + output_csv_file)


def send_post_request_and_log(data, writer):
    try:
        params = {"shared_secret": shared_secret}
        json_object = {
            "providerMsaId": data["provider_msa_id"],
            "userIdentifier": {
                "value": data["value"],
                "type": data["type"],
                "priority": data["priority"]
            }
        }
        response = requests.post(api_url, json=json_object, params=params)
        if response.status_code == 200:
            status = "success"
        else:
            status = "failure"
        writer.writerow({
            "provider_msa_id": data["provider_msa_id"],
            "value": data["value"],
            "type": data["type"],
            "priority": data["priority"],
            "status": status,
            "code": response.status_code,
            "error": response.text if status == "failure" else ""
        })
    except Exception as error:
        status = "failure"
        writer.writerow({
            "provider_msa_id": data["provider_msa_id"],
            "value": data["value"],
            "type": data["type"],
            "priority": data["priority"],
            "status": status,
            "code": "null",
            "error": str(error)
        })


# Open the input and output CSV files
try:
    with open(input_csv_file, "r") as input_file, open(output_csv_file, "w", newline="") as output_file:
        csv_reader = csv.DictReader(input_file)
        fieldnames = ["provider_msa_id", "value", "type", "priority", "status", "code", "error"]
        csv_writer = csv.DictWriter(output_file, fieldnames=fieldnames)
        csv_writer.writeheader()

        for row in csv_reader:
            # Assuming your CSV file has these column names
            send_post_request_and_log(row, csv_writer)
except FileNotFoundError:
    print(f"Input file '{input_csv_file}' not found.")
except Exception as e:
    print("An error occurred:", str(e))
