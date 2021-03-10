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

1. Duration of handling a message: 
  `irate(perfo_seconds_sum[1m])/irate(perfo_seconds_count[1m])`
2. Nr of succesfull handled messages per second: 
  `irate(success_seconds_count[1m])`
3. Duration of processing after a connection is acquired:
  `irate(success_seconds_sum[1m])/irate(success_seconds_count[1m])`
4. Number of available connections: 
  `connectionpool`

If you increase the number of concurrency in the flows to add up above the `poolSize`, 
you will notice that metric #1 goes up, but the metric #2 and #3 will keep about the same.
The reason is that congestion is starting to happen. There are coroutines waiting for
a thread to be available so that the message can be processed.

Now change the number of Threads available in the `database` threadpool. You'll see
that timeouts start to occur. In the commandline you'll see mentions of `TimeoutException`. 
There are more threads battling for a connection than that there are connections.
The result is that this will accumulate to such a problem that you'll see that the application
completely stops handling messages succesfully.
