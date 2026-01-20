def get_height(v0):
    y = 0
    v = v0
    while v > 0:
        y += v
        v = (v - 0.08) * 0.98
    return y

target = 7.0
v = 0.0
while get_height(v) < target:
    v += 0.001

print(f"Velocity for 7 blocks: {v:.4f}")
