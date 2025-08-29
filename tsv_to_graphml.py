import csv
import re
import xml.etree.ElementTree as ET

def parse_summary(summary_str):
    """
    Extract Source, Sink, Sanitizers, Reach edges, and Sensitive markers
    from the nested summary string.
    """
    # Extract source & sink
    source_match = re.search(r"Source\[(N\d+)\]", summary_str)
    sink_match = re.search(r"Sink\[(N\d+)\]", summary_str)
    source = source_match.group(1) if source_match else None
    sink = sink_match.group(1) if sink_match else None

    # Extract sanitizer (can be multiple)
    sanitizers = re.findall(r"Sanitizer\[(N\d+)\]", summary_str)

    # Extract sensitive markers
    sensitive = re.findall(r"SensFromType\[(S\d+)\]", summary_str)

    # Extract Reach edges (E-nodes)
    reaches = re.findall(r"(E\d+)", summary_str)

    return source, sink, sanitizers, sensitive, reaches


def create_graphml(path_summary_file, output_graphml):
    nodes = set()
    edges = []

    with open(path_summary_file) as f:
        reader = csv.reader(f, delimiter="\t")
        for row in reader:
            summary_str = row[0]   # first column is the path summary string
            path_type = row[-1]    # last column says "complete" / "sanitized" / "unsanitized"

            source, sink, sanitizers, sensitive, reaches = parse_summary(summary_str)

            if not source or not sink:
                continue  # skip invalid rows

            # Collect all nodes
            nodes.add(source)
            nodes.add(sink)
            for s in sanitizers: nodes.add(s)
            for sens in sensitive: nodes.add(sens)
            for r in reaches: nodes.add(r)

            # Build edges along Reach chain
            prev = source
            for r in reaches:
                edges.append((prev, r, path_type))
                prev = r
            edges.append((prev, sink, path_type))

            # Attach sanitizers (like middleware)
            for s in sanitizers:
                edges.append((source, s, "sanitizer"))
                edges.append((s, sink, "sanitizer"))

    # Build GraphML
    ns = {"xmlns": "http://graphml.graphdrawing.org/xmlns"}
    ET.register_namespace("", ns["xmlns"])
    graphml = ET.Element("graphml", xmlns=ns["xmlns"])
    graph = ET.SubElement(graphml, "graph", edgedefault="directed")

    # Add nodes
    for node in nodes:
        ET.SubElement(graph, "node", id=node)

    # Add edges
    for idx, (src, dst, etype) in enumerate(edges):
        ET.SubElement(graph, "edge", id=f"e{idx}", source=src, target=dst, label=etype)

    # Save
    tree = ET.ElementTree(graphml)
    tree.write(output_graphml, encoding="utf-8", xml_declaration=True)
    print(f"GraphML saved to {output_graphml}")


if __name__ == "__main__":
    create_graphml("path_summary.tsv", "output/pathways.graphml")
