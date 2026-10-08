The problem: 

10 microservices × 5 pods × 20 Hikari connections each
→ 1000 potential PostgreSQL connections

-> High memory usage: 5-10 mb per backend process (one backend per connection)

-> CPU overhead

-> increased query latency during spikes

The proposed solution: 

    1- Reduce Hikari pool size: 10 connections

    2- Introduce PgBouncer proxy connection pooler!
        * pool_mode: transaction
        * pool_size: 50 (shared accross all pods)
        
        -> smaller shared pool
        -> hikari will reuse local connections to pgbouncer
    
    Key proposed results: 
        * reduced max connections on Postgres
        * reduced app connection latency 
        * reduced cpu usage
        * reduced memory usage
        * increase throughput
        
        * rate limiter to isolate certain apps (if needed)
        * easy failover management ( switch databases )
        * central visibility for the connections 
    
    Backfire results: 
        * high hikari pool size and small pgbouncer pool: app will get frequent connection timeouts
        * pgbouncer on transaction mode, and the app deals with long transactions or streaming: connections release too early
        * features that rely on session state ( cursors or temp tables ... ) won't work well on transaction mode
        * small network hop between app and postgres ( increased latency )