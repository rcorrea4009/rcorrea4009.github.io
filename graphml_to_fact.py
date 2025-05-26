import os
import xml.etree.ElementTree as ET
import csv

def parse_kegg_graphml(directory, output_prefix):
    """Process all GraphML files in KEGG directory"""
    nodes = set()
    edges = []
    
    # Define KEGG node type mappings
    TYPE_MAPPING = {
        'compound': 'normal',
        'pathway': 'sink',
        'enzyme': 'sanitizer',
        'reaction': 'normal',
        'group': 'normal'
    }
    
    for filename in os.listdir(directory):
        if filename.endswith('.graphml'):
            path = os.path.join(directory, filename)
            tree = ET.parse(path)
            root = tree.getroot()
            
            # XML namespace handling
            ns = {'g': 'http://graphml.graphdrawing.org/xmlns'}
            
            # Parse nodes
            for node in root.findall('.//g:node', ns):
                node_id = node.get('id')
                node_type = 'compound'  # Default type
                
                # Get type from GraphML data
                for data in node.findall('.//g:data[@key="type"]', ns):
                    node_type = data.text.lower()
                
                # Apply mapping
                mapped_type = TYPE_MAPPING.get(node_type, 'normal')
                nodes.add((node_id, mapped_type))
            
            # Parse edges
            for edge in root.findall('.//g:edge', ns):
                source = edge.get('source')
                target = edge.get('target')
                label = ''
                for data in edge.findall('.//g:data[@key="label"]', ns):
                    label = data.text
                edges.append((source, target, label))
    
    # Mark drug-like compounds as sources
    sources = set()
    for node_id, node_type in nodes:
        if 'drug' in node_id.lower() or 'compound' in node_type.lower():
            sources.add(node_id)
    
    # Write output files
    with open(f'{output_prefix}_nodes.facts', 'w') as f:
        writer = csv.writer(f, delimiter='\t')
        for node_id, node_type in nodes:
            # Override type if it's a source compound
            final_type = 'source' if node_id in sources else node_type
            writer.writerow([node_id, final_type])
    
    with open(f'{output_prefix}_edges.facts', 'w') as f:
        writer = csv.writer(f, delimiter='\t')
        for source, target, label in edges:
            writer.writerow([source, target, label])
    
    # Create sensitive.facts
    with open(f'{output_prefix}_sensitive.facts', 'w') as f:
        writer = csv.writer(f, delimiter='\t')
        for node_id in sources:
            writer.writerow([f'drug_{node_id}', node_id])

if __name__ == '__main__':
    import sys
    if len(sys.argv) != 3:
        print("Usage: python graphml_to_fact.py <input_dir> <output_prefix>")
        sys.exit(1)
    parse_kegg_graphml(sys.argv[1], sys.argv[2])