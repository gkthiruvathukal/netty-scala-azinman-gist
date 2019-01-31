# Synopsis

This is based on some nice work by Aaron Zinman to look at Netty performance (in 2011, it seems). See  https://gist.github.com/azinman/779303 for the original code.

There were some aging dependencies, especially Configgy and custom logging, which I replaced by log4s.

To run this code, `sbt` must be available. I can write some code to package a jar file if needed but this is not done yet.


# Building

```
sbt compile
```

This will be done automatically for you but it is good to build before starting.

# Running the client

```
sbt "runMain com.mypackage.benchmark.BenchmarkClientMain <server hostname> <server port> <number of connections>"
```

# Running the server

```
sbt "runMain com.mypackage.benchmark.BenchmarkServerMain <port to listen on>"
```


