#!/bin/sh
set -eu
# PID 1 owns lifecycle; no interactive shell, stdin dependency or shell EXIT trap.
exec python3 -B /usr/local/lib/isolation/lifecycle.py
