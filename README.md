# Pub-Sub-Delivered

A food delivery app demo with Apache Kafka.

## Getting started

Follow these steps to run the full cluster (kafka + services) with Docker.

1. Clone the repo into your machine
   ```shell
   git clone https://github.com/fuljo/pub-sub-delivered.git
   cd ./pub-sub-delivered
   ```

2. Build the image for our application
    ```shell
    docker build -t fuljo/pub-sub-delivered .
    ```

3. Start the cluster with Docker Compose
   ```
   docker compose up
   ```

## Architecture

### Topics
#### Users
Holds all the user records, new records substitute old records.
- name: `users`
- written by: UsersService
- read by: UsersService, OrdersService
- key type: `String`
- value type: `User`

#### Products
Holds all the products and their availability info, new records substitute old records.
- name: `products`
- written by: OrdersService
- read by: OrdersService
- key type: `String`
- value type: `Product`