def get_height(v0):
    y = 0
    v = v0
    # Simulate ticks until peak height (velocity becomes negative or zero)
    while v > 0:
        y += v
        v = (v - 0.08) * 0.98
    return y

targets = [3.0, 12.0]
results = {}

for target in targets:
    v = 0.0
    # Simple linear search
    while get_height(v) < target:
        v += 0.001
    results[target] = v

print(f"Velocity for 3 blocks: {results[3.0]:.4f}")
print(f"Velocity for 12 blocks: {results[12.0]:.4f}")
