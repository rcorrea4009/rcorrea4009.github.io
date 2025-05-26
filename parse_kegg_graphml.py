import os
import xml.etree.ElementTree as ET
from xml.dom import minidom

def parse_kegg_graphml(directory, output_file):
    """Process all KEGG GraphML files and create a single consolidated pathway.graphml"""
    # Initialize XML structure with proper namespaces
    graphml = ET.Element('graphml', xmlns="http://graphml.graphdrawing.org/xmlns")

    # Add required GraphML keys for KEGG data
    keys = [
        {'id': 'type', 'for': 'node', 'attr.name': 'type', 'attr.type': 'string'},
        {'id': 'label', 'for': 'edge', 'attr.name': 'label', 'attr.type': 'string'},
        {'id': 'class', 'for': 'node', 'attr.name': 'class', 'attr.type': 'string'},
        {'id': 'name', 'for': 'node', 'attr.name': 'name', 'attr.type': 'string'},
        {'id': 'keggid', 'for': 'node', 'attr.name': 'keggid', 'attr.type': 'string'}
    ]

    for key in keys:
        ET.SubElement(graphml, 'key', attrib=key)

    graph = ET.SubElement(graphml, 'graph', id="pathway", edgedefault="directed")

    # Enhanced classification rules
    def classify_node(node_id, description):
        """Determine node class based on ID and description"""
        # Convert to lowercase for case-insensitive matching
        node_id_lower = node_id.lower()
        desc_lower = description.lower() if description else ""

        # 1. Detect sinks (pathway maps)
        if ('pathway' in node_id_lower or
                'map' in node_id_lower or
                node_id.endswith('map') or
                'pathway' in desc_lower):
            return 'sink'

        # 2. Detect sanitizers (enzymes)
        enzyme_indicators = [
            'enzyme', 'ec', 'ase', 'cysteine', 'coenzyme',
            '[enzyme]', 'f420', 'f-420', 'coenzyme_f420'
        ]
        if any(indicator in node_id_lower for indicator in enzyme_indicators) or \
                any(indicator in desc_lower for indicator in enzyme_indicators):
            return 'sanitizer'

        # 3. Detect sources (drugs)
        if ('drug' in node_id_lower or
                'drug' in desc_lower or
                node_id.startswith('D')):
            return 'source'

        return 'normal'

    # Track all processed elements
    processed_nodes = set()
    processed_edges = set()
    node_counts = {'source': 0, 'sink': 0, 'sanitizer': 0}

    # Process each KEGG GraphML file
    for filename in sorted(os.listdir(directory)):
        if not filename.endswith('.graphml'):
            continue

        filepath = os.path.join(directory, filename)
        print(f"\nProcessing {filename}...")

        try:
            # Parse with namespace
            ns = {'g': 'http://graphml.graphdrawing.org/xmlns'}
            tree = ET.parse(filepath)
            root = tree.getroot()

            print(f"Root tag: {root.tag}")

            # Find nodes
            nodes = root.findall('.//g:node', ns)
            if not nodes:
                nodes = root.findall('.//node')  # Fallback
            print(f"Found {len(nodes)} nodes")

            # Process nodes
            for node in nodes:
                node_id = node.get('id')
                if not node_id or node_id in processed_nodes:
                    continue

                # Initialize node attributes
                node_type = 'compound'  # Default type
                description = ''
                formula = ''

                # Extract node properties
                for data in node.findall('g:data', ns):
                    key = data.get('key')
                    value = data.text if data.text else ''

                    if key == 'd0':  # formula
                        formula = value
                    elif key == 'd1':  # description
                        description = value

                # Classify the node
                node_class = classify_node(node_id, description)

                # Debug print for enzymes and sinks
                if node_class in ['sanitizer', 'sink']:
                    print(f"Classified as {node_class}: {node_id}")

                # Update counts
                if node_class in node_counts:
                    node_counts[node_class] += 1

                # Create node in consolidated graph
                node_elem = ET.SubElement(graph, 'node', id=node_id)
                ET.SubElement(node_elem, 'data', key='type').text = node_type
                ET.SubElement(node_elem, 'data', key='class').text = node_class
                ET.SubElement(node_elem, 'data', key='name').text = description if description else node_id
                ET.SubElement(node_elem, 'data', key='keggid').text = ''  # Not available in your files
                processed_nodes.add(node_id)

            # Process edges (unchanged from previous version)
            edges = root.findall('.//g:edge', ns)
            if not edges:
                edges = root.findall('.//edge')
            print(f"Found {len(edges)} edges")

            for edge in edges:
                source = edge.get('source')
                target = edge.get('target')

                if not source or not target or (source, target) in processed_edges:
                    continue

                if source in processed_nodes and target in processed_nodes:
                    label = ''
                    for data in edge.findall('g:data', ns):
                        if data.get('key') == 'label':
                            label = data.text if data.text else ''
                            break

                    edge_elem = ET.SubElement(graph, 'edge', source=source, target=target)
                    if label:
                        ET.SubElement(edge_elem, 'data', key='label').text = label
                    processed_edges.add((source, target))

        except Exception as e:
            print(f"Error processing {filename}: {str(e)}")
            continue

    # Generate output
    rough_xml = ET.tostring(graphml, encoding='utf-8')
    parsed_xml = minidom.parseString(rough_xml)
    pretty_xml = parsed_xml.toprettyxml(indent="  ", encoding='utf-8')

    with open(output_file, 'wb') as f:
        f.write(pretty_xml)

    print(f"\nSuccessfully created consolidated KEGG pathway file: {output_file}")
    print(f"Total nodes: {len(processed_nodes)}")
    print(f"Total edges: {len(processed_edges)}")
    print(f"Sources: {node_counts['source']} | Sinks: {node_counts['sink']} | Sanitizers: {node_counts['sanitizer']}")

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 3:
        print("Usage: python kegg_consolidator.py <kegg_graphml_dir> <output_pathway.graphml>")
        print("Example: python kegg_consolidator.py kegg_pathway_graphml pathway.graphml")
        sys.exit(1)

    input_dir = sys.argv[1]
    output_file = sys.argv[2]

    if not os.path.isdir(input_dir):
        print(f"Error: Input directory '{input_dir}' does not exist")
        sys.exit(1)

    parse_kegg_graphml(input_dir, output_file)