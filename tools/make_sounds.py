#!/usr/bin/env python3
"""Synthesises the app's sound effects into app/src/main/res/raw.

Everything is built from noise bursts and decaying sine tones, so the sounds are the project's
own and can be regenerated or retuned here. Needs numpy.

    python3 tools/make_sounds.py
"""
import os
import wave

import numpy as np

RATE = 44100
OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "app", "src", "main", "res", "raw")
rng = np.random.default_rng(20261106)


def seconds(duration):
    return np.arange(int(duration * RATE)) / RATE


def noise(duration, low, high):
    """White noise limited to a band, by zeroing the rest of its spectrum."""
    n = int(duration * RATE)
    spectrum = np.fft.rfft(rng.standard_normal(n))
    freq = np.fft.rfftfreq(n, 1 / RATE)
    spectrum[(freq < low) | (freq > high)] = 0
    return np.fft.irfft(spectrum, n)


def shape(signal, attack, decay):
    """A fast rise and an exponential fall."""
    t = np.arange(len(signal)) / RATE
    return signal * np.minimum(1.0, t / attack) * np.exp(-t / decay)


def tone(freq, duration, decay):
    t = seconds(duration)
    return np.sin(2 * np.pi * freq * t) * np.exp(-t / decay)


def mix(duration, parts):
    out = np.zeros(int(duration * RATE))
    for at, signal, gain in parts:
        start = int(at * RATE)
        end = min(len(out), start + len(signal))
        out[start:end] += signal[: end - start] * gain
    return out


def click():
    """One chip landing on another: a few bright partials that die within milliseconds."""
    partials = sum(tone(rng.uniform(2400, 6200), 0.05, rng.uniform(0.004, 0.009)) for _ in range(3))
    return partials + shape(noise(0.05, 3000, 9000), 0.0005, 0.002) * 0.6


def knock():
    return tone(150, 0.12, 0.022) + shape(noise(0.12, 150, 1400), 0.001, 0.012) * 0.5


def swish(duration=0.12):
    return shape(noise(duration, 1400, 8500), 0.008, duration / 3.5)


def save(name, signal, level=0.8):
    signal = signal / np.max(np.abs(signal)) * level
    fade = min(len(signal), int(0.004 * RATE))
    signal[-fade:] *= np.linspace(1, 0, fade)
    pcm = (signal * 32767).astype("<i2")
    os.makedirs(OUT, exist_ok=True)
    with wave.open(os.path.join(OUT, name + ".wav"), "wb") as f:
        f.setnchannels(1)
        f.setsampwidth(2)
        f.setframerate(RATE)
        f.writeframes(pcm.tobytes())
    print(f"{name}.wav  {len(signal) / RATE:.2f}s")


# Check: two knuckles on the table.
save("check", mix(0.3, [(0.0, knock(), 1.0), (0.13, knock(), 0.8)]), 0.7)

# Bet, call or raise: a short stack of chips pushed forward.
save("chips", mix(0.28, [(t, click(), g) for t, g in [(0, 1), (0.04, 0.7), (0.085, 0.9), (0.12, 0.6), (0.17, 0.8)]]), 0.75)

# Fold: cards slid away.
save("fold", shape(noise(0.26, 700, 5200), 0.02, 0.07), 0.55)

# Flop: three cards dealt.
save("deal", mix(0.42, [(0.0, swish(), 1.0), (0.12, swish(), 0.9), (0.24, swish(), 1.0)]), 0.6)

# Turn and river: one card.
save("card", mix(0.2, [(0.0, swish(0.14), 1.0), (0.09, click(), 0.25)]), 0.6)

# Pot pushed to the winner: chips tumbling, faster as they go, under a small three-note rise.
times = np.cumsum(np.linspace(0.07, 0.022, 16))
rise = [(0.02 + i * 0.11, tone(f, 0.5, 0.16), 0.5) for i, f in enumerate([880, 1108.7, 1318.5])]
save("win", mix(1.0, [(t, click(), rng.uniform(0.5, 1.0)) for t in times] + rise), 0.75)

# The blinds went up: two bright notes, the second higher.
save("level", mix(0.75, [(0.0, tone(784, 0.45, 0.13), 1.0), (0.17, tone(1174.7, 0.55, 0.17), 0.9)]), 0.6)

# Your turn: one soft, short note that sits under the other sounds.
save("turn", tone(988, 0.12, 0.028), 0.3)

# Game over: a room clapping. Each clap is a few milliseconds of mid-band noise.
length = 2.8
claps = []
for _ in range(520):
    at = rng.beta(1.6, 2.4) * (length - 0.1)
    claps.append((at, shape(noise(0.06, rng.uniform(700, 1200), rng.uniform(2600, 4200)), 0.001, rng.uniform(0.008, 0.016)), rng.uniform(0.3, 1.0)))
bed = noise(length, 500, 5000) * np.interp(seconds(length), [0, 0.3, 1.2, length], [0, 0.05, 0.04, 0])
save("applause", mix(length, claps) + bed * 6, 0.7)
