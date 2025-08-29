import csv
import random
import networkx as nx
from pathlib import Path

def generate_watts_strogatz_facts(n=30, k=4, p=0.15, out_dir="facts", seed=42):
    """
    Build a Watts–Strogatz graph and write Soufflé .facts:
      - nodes.facts     (id, node, type)
      - edges.facts     (id, from, to, interaction)
      - sensitive.facts (id, node, tag)
    """
    random.seed(seed)
    Path(out_dir).mkdir(parents=True, exist_ok=True)

    # 1) Graph (undirected)
    G_und = nx.watts_strogatz_graph(n=n, k=k, p=p, seed=seed)

    # 2) Roles
    source = 0
    sink = n - 1
    node_type = {}
    for v in G_und.nodes():
        if v == source:
            node_type[v] = "source"
        elif v == sink:
            node_type[v] = "sink"
        elif v % 5 == 0:
            node_type[v] = "sanitizer"
        else:
            node_type[v] = "normal"

    # 3) Sensitive nodes (example rule: every 3rd node, excluding s/t)
    sensitive_nodes = [v for v in G_und.nodes() if (v % 3 == 0 and v not in (source, sink))]
    sensitive_tag = "pii_compound"  # matches your Datalog

    # 4) Write nodes.facts
    with open(Path(out_dir, "nodes.facts"), "w", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        for idx, v in enumerate(sorted(G_und.nodes())):
            w.writerow([f"N{idx}", str(v), node_type[v]])

    # 5) Write edges.facts (make it directed both ways)
    with open(Path(out_dir, "edges.facts"), "w", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        eidx = 0
        for u, v in sorted(G_und.edges()):
            w.writerow([f"E{eidx}", str(u), str(v), "interacts"]); eidx += 1
            w.writerow([f"E{eidx}", str(v), str(u), "interacts"]); eidx += 1

    # 6) Write sensitive.facts
    with open(Path(out_dir, "sensitive.facts"), "w", newline="") as f:
        w = csv.writer(f, delimiter="\t")
        for idx, v in enumerate(sorted(sensitive_nodes)):
            w.writerow([f"S{idx}", str(v), sensitive_tag])

    print(f"[OK] Wrote facts to: {Path(out_dir).resolve()}")
    print(" - nodes.facts")
    print(" - edges.facts")
    print(" - sensitive.facts")

if __name__ == "__main__":
    # tweak n,k,p as you like
    generate_watts_strogatz_facts(n=30, k=4, p=0.15, out_dir="facts", seed=42)
