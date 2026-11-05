"""
Cluster Capacity & Metrics Plotter
Polls server capacities via Admin socket and visualizes subscriber distribution.
"""

import sys
import time
import socket
import argparse
from datetime import datetime

SERVERS = {
    1: {"host": "localhost", "admin_port": 7001, "client_port": 6001},
    2: {"host": "localhost", "admin_port": 5002, "client_port": 6002},
    3: {"host": "localhost", "admin_port": 5003, "client_port": 6003},
}

def query_server_capacity(host, port, server_id, timeout=2.0):
    """
    Connects to server admin socket and queries current subscriber capacity.
    """
    try:
        with socket.create_connection((host, port), timeout=timeout) as s:
            # Protocol: Send single byte (1 = Capacity Request)
            s.sendall(bytes([1]))
            data = s.recv(1024)
            if data:
                # Raw bytes response: extract load if available or return connected status
                return len(data)
    except Exception:
        return None
    return 0

def generate_report(metrics_history):
    """Prints a terminal-based capacity summary table."""
    print("\n" + "=" * 60)
    print(f" DISTRIBUTED CLUSTER METRICS - {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)
    print(f"{'Server ID':<12} | {'Admin Address':<20} | {'Status':<12}")
    print("-" * 60)
    for s_id, conf in SERVERS.items():
        val = query_server_capacity(conf["host"], conf["admin_port"], s_id)
        status = "ONLINE" if val is not None else "OFFLINE"
        print(f"Server {s_id:<5} | {conf['host'] + ':' + str(conf['admin_port']):<20} | {status:<12}")
    print("=" * 60 + "\n")

def run_live_plot():
    """Live interactive plot using matplotlib if available."""
    try:
        import matplotlib.pyplot as plt
        import matplotlib.animation as animation
    except ImportError:
        print("[!] matplotlib is not installed. Falling back to CLI telemetry monitor.")
        print("    Install via: pip install matplotlib\n")
        try:
            while True:
                generate_report({})
                time.sleep(3)
        except KeyboardInterrupt:
            print("\nExiting monitor.")
        return

    fig, ax = plt.subplots(figsize=(9, 5))
    server_names = [f"Server {s}" for s in SERVERS]
    colors = ["#2563eb", "#16a34a", "#dc2626"]

    def update(frame):
        ax.clear()
        capacities = []
        for s_id, conf in SERVERS.items():
            cap = query_server_capacity(conf["host"], conf["admin_port"], s_id)
            capacities.append(cap if cap is not None else 0)

        bars = ax.bar(server_names, capacities, color=colors, alpha=0.85, width=0.45)
        ax.set_ylabel("Reported Load / Packets")
        ax.set_title("Distributed Cluster Real-Time Load Monitor")
        ax.set_ylim(0, max(max(capacities) + 5, 20))
        for bar in bars:
            yval = bar.get_height()
            ax.text(bar.get_x() + bar.get_width() / 2, yval + 0.5, int(yval), ha="center", va="bottom", fontweight="bold")

    ani = animation.FuncAnimation(fig, update, interval=2000)
    plt.tight_layout()
    plt.show()

if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Distributed Subscriber Service Capacity Plotter")
    parser.add_argument("--cli", action="store_true", help="Run in CLI monitor mode without GUI")
    args = parser.parse_args()

    if args.cli:
        try:
            while True:
                generate_report({})
                time.sleep(3)
        except KeyboardInterrupt:
            print("\nMonitor stopped.")
    else:
        run_live_plot()
