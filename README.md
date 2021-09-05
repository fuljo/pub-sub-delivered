# Pub-Sub-Delivered

A food delivery app built with microservices, using Apache Kafka.

For more details see the [project report](https://github.com/fuljo/pub-sub-delivered/releases/latest/download/psd_report.pdf).

## Getting started

Follow these steps to run the full cluster (Kafka + services) with Docker.

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

4. The REST API is exposed at `http://localhost/api/`, check the last section of the report for a complete reference.

   Inside the `tools` directory we also provide a Python client library for the API and a [notebook](tools/test_interactive.ipynb) with a demo interaction.

To stop the cluster use `docker compose down` or `docker compose down -v` if you also want to remove the persisted data from the brokers.


## Report
A PDF project report describing the project, its design principles, and with a full reference of the REST API can be downloaded [here](https://github.com/fuljo/pub-sub-delivered/releases/latest/download/psd_report.pdf).
Alternatively it can be locally compiled with XeLaTeX:
```
cd report
latexmk
```

## Credits
This project was created as part of the exam for the 2020 edition of the *Middleware Technologies for Distributed Systems* at Politecnico di Milano.

The design is inspired by the free ebook [Designing Event-Driven Systems](https://www.confluent.io/designing-event-driven-systems/) by Ben Stopford of Confluent.

## License
This program (excluding external data and libraries) is distributed under the MIT license.