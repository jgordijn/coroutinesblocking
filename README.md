# coroutinesblocking
Test congestion for calling blocking code

Download prometheus: (prometheus.io)
Change the yaml to have (check the correct place to put it)
```
  scrape_interval:     1s # Set the scrape interval to every 15 seconds. Default is every 1 minute.
  evaluation_interval: 1s # Evaluate rules every 15 seconds. The default is every 1 minute.
  metrics_path: '/prometheus'
  static_configs:
    - targets: ['localhost:8080']
```

This wil scrape every second. Just for the convenience of testing.

Start the main in Test2. 

Metrics can be found here: http://localhost:8080/prometheus

Go to prometheus via localhost:9090 and add the following panels:

* Duration of handling a message: 
  `irate(perfo_seconds_sum[1m])/irate(perfo_seconds_count[1m])`
* Nr of succesfull handled messages per second: 
  `irate(success_seconds_count[1m])`
* Number of available connections: 
  `connectionpool`

