# WaveLink Proxy

1. Turn off the WaveLink software to release port 1884
2. Start the WaveLink proxy
3. Start WaveLink again, it will now use port 1885

Any application connecting to port 1884 will be proxied to port 1885,
allowing you to intercept and modify the data being sent between the WaveLink
software and any connected clients.
