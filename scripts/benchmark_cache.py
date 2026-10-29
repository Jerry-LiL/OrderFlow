import time
import requests
import statistics
import redis

BASE_URL = "http://localhost:8089/api/v1/products/1"
r = redis.Redis(host='localhost', port=6379, db=0)

def benchmark_cold(n=500):
    latencies = []
    for _ in range(n):
        # Evict product cache to force database hit
        r.delete("products::1")
        start = time.perf_counter()
        resp = requests.get(BASE_URL)
        duration_ms = (time.perf_counter() - start) * 1000.0
        if resp.status_code == 200:
            latencies.append(duration_ms)
    return latencies

def benchmark_warm(n=500):
    # Prime cache
    requests.get(BASE_URL)
    latencies = []
    for _ in range(n):
        start = time.perf_counter()
        resp = requests.get(BASE_URL)
        duration_ms = (time.perf_counter() - start) * 1000.0
        if resp.status_code == 200:
            latencies.append(duration_ms)
    return latencies

print("Warming up JVM...")
for _ in range(100):
    requests.get(BASE_URL)

print("Running 500 Cold Requests (PostgreSQL query every time)...")
cold_times = benchmark_cold(500)

print("Running 500 Warm Requests (Redis read-through cache hit)...")
warm_times = benchmark_warm(500)

def stats(times):
    return {
        "mean": round(statistics.mean(times), 2),
        "median": round(statistics.median(times), 2),
        "p95": round(sorted(times)[int(len(times) * 0.95)], 2),
        "p99": round(sorted(times)[int(len(times) * 0.99)], 2),
        "min": round(min(times), 2),
        "max": round(max(times), 2)
    }

cold_s = stats(cold_times)
warm_s = stats(warm_times)

print("--- BENCHMARK RESULTS ---")
print(f"Cold (DB):   Mean={cold_s['mean']}ms, Median={cold_s['median']}ms, p95={cold_s['p95']}ms, p99={cold_s['p99']}ms, Min={cold_s['min']}ms, Max={cold_s['max']}ms")
print(f"Warm (Redis): Mean={warm_s['mean']}ms, Median={warm_s['median']}ms, p95={warm_s['p95']}ms, p99={warm_s['p99']}ms, Min={warm_s['min']}ms, Max={warm_s['max']}ms")
improvement = round((cold_s['mean'] - warm_s['mean']) / cold_s['mean'] * 100, 1)
speedup = round(cold_s['mean'] / warm_s['mean'], 1)
print(f"Latency Reduction: {improvement}% ({speedup}x speedup)")
