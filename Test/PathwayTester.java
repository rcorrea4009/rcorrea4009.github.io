import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GraphMLPathwayTester {
    private static final String GRAPHML_FILE = "pathways.graphml";
    private static final String GRAPHML_NS = "http://graphml.graphdrawing.org/xmlns";

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<TestResult> testResults = new ArrayList<>();

    public static void main(String[] args) {
        GraphMLPathwayTester tester = new GraphMLPathwayTester();
        tester.runTests();
        tester.generateReports();
    }

    public void runTests() {
        loadData();
        classifyNodes();
        testAllPaths();
        testUnsafePaths();
        testSafePaths();
        generateTestSummary();
    }

    private void loadData() {
        try {
            // Parse GraphML file
            File inputFile = new File(GRAPHML_FILE);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
            dbFactory.setNamespaceAware(true);
            DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
            Document doc = dBuilder.parse(inputFile);
            doc.getDocumentElement().normalize();

            // Parse nodes
            NodeList nodeList = doc.getElementsByTagNameNS(GRAPHML_NS, "node");
            for (int i = 0; i < nodeList.getLength(); i++) {
                org.w3c.dom.Node domNode = nodeList.item(i);
                if (domNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element element = (Element) domNode;
                    String id = element.getAttribute("id");
                    nodes.add(new Node(id));
                }
            }

            // Parse edges
            NodeList edgeList = doc.getElementsByTagNameNS(GRAPHML_NS, "edge");
            for (int i = 0; i < edgeList.getLength(); i++) {
                org.w3c.dom.Node domNode = edgeList.item(i);
                if (domNode.getNodeType() == org.w3c.dom.Node.ELEMENT_NODE) {
                    Element element = (Element) domNode;
                    String from = element.getAttribute("source");
                    String to = element.getAttribute("target");
                    edges.add(new Edge(from, to));
                }
            }

            testResults.add(new TestResult("graphml_loaded", "passed",
                    "GraphML file loaded successfully with " + nodes.size() + " nodes and " + edges.size() + " edges"));

        } catch (Exception e) {
            testResults.add(new TestResult("data_loading", "failed",
                    "Error loading GraphML file: " + e.getMessage()));
            e.printStackTrace();
        }
    }

private void classifyNodes() {
    // First build the graph structure
    Map<String, Set<String>> graph = new HashMap<>();
    nodes.forEach(n -> graph.put(n.id, new HashSet<>()));
    edges.forEach(e -> graph.get(e.from).add(e.to));

    // Mark sources (nodes with no incoming edges)
    Set<String> targets = edges.stream().map(e -> e.to).collect(Collectors.toSet());
    nodes.stream()
        .filter(n -> !targets.contains(n.id))
        .forEach(n -> n.type = "Source");
    
    // Mark sinks (nodes with no outgoing edges)
    Set<String> sources = edges.stream().map(e -> e.from).collect(Collectors.toSet());
    nodes.stream()
        .filter(n -> !sources.contains(n.id))
        .forEach(n -> n.type = "Sink");
    
    // Enhanced sanitizer selection based on node degree
    List<Node> candidateNodes = nodes.stream()
        .filter(n -> n.type == null) // Only non-source/sink nodes
        .collect(Collectors.toList());
    
    // Sort by degree (number of outgoing edges) and take top 5%
    candidateNodes.sort((a, b) -> Integer.compare(
        graph.getOrDefault(b.id, Collections.emptySet()).size(),
        graph.getOrDefault(a.id, Collections.emptySet()).size()));
    
    int sanitizerCount = Math.max(1, candidateNodes.size() / 20); // ~5% of candidate nodes
    candidateNodes.stream()
        .limit(sanitizerCount)
        .forEach(n -> n.type = "Sanitizer");
        
    // Count classifications
    long sourceCount = nodes.stream().filter(n -> "Source".equals(n.type)).count();
    long sinkCount = nodes.stream().filter(n -> "Sink".equals(n.type)).count();
    long sanitizersCount = nodes.stream().filter(n -> "Sanitizer".equals(n.type)).count();
    
    testResults.add(new TestResult("node_classification", "passed",
            String.format("Classified nodes: %d Sources, %d Sinks, %d Sanitizers", 
                    sourceCount, sinkCount, sanitizersCount)));
}

    private void testAllPaths() {
        Map<String, Set<String>> graph = buildGraph();
        List<Pathway> allPathways = findAllPathways(graph);
        
        testResults.add(new TestResult("all_paths", "passed",
                "Found " + allPathways.size() + " paths from Sources to Sinks"));
    }

    private void testUnsafePaths() {
        Map<String, Set<String>> graph = buildGraph();
        List<Pathway> allPathways = findAllPathways(graph);
        
        List<Pathway> unsafePathways = allPathways.stream()
            .filter(pathway -> !isPathwaySanitized(pathway, graph))
            .collect(Collectors.toList());
            
        testResults.add(new TestResult("unsafe_paths", unsafePathways.isEmpty() ? "passed" : "warning",
                "Found " + unsafePathways.size() + " unsafe paths (no sanitizer)"));
        
        // Write unsafe pathways to file
        writePathways("unsafe_pathways.csv", unsafePathways);
    }

    private void testSafePaths() {
        Map<String, Set<String>> graph = buildGraph();
        List<Pathway> allPathways = findAllPathways(graph);
        
        List<Pathway> safePathways = allPathways.stream()
            .filter(pathway -> isPathwaySanitized(pathway, graph))
            .collect(Collectors.toList());
            
        testResults.add(new TestResult("safe_paths", safePathways.isEmpty() ? "warning" : "passed",
                "Found " + safePathways.size() + " safe paths (with sanitizer)"));
    }

    private Map<String, Set<String>> buildGraph() {
        Map<String, Set<String>> graph = new HashMap<>();
        
        // Initialize all nodes
        nodes.forEach(n -> graph.put(n.id, new HashSet<>()));
        
        // Add edges
        edges.forEach(e -> graph.get(e.from).add(e.to));
        
        return graph;
    }

    private List<Pathway> findAllPathways(Map<String, Set<String>> graph) {
        List<String> sources = nodes.stream()
            .filter(n -> "Source".equals(n.type))
            .map(n -> n.id)
            .collect(Collectors.toList());
            
        List<String> sinks = nodes.stream()
            .filter(n -> "Sink".equals(n.type))
            .map(n -> n.id)
            .collect(Collectors.toList());
            
        List<Pathway> pathways = new ArrayList<>();
        
        // Simple BFS to find all paths from sources to sinks
        for (String source : sources) {
            Queue<List<String>> queue = new LinkedList<>();
            queue.add(Arrays.asList(source));
            
            while (!queue.isEmpty()) {
                List<String> path = queue.poll();
                String lastNode = path.get(path.size() - 1);
                
                if (sinks.contains(lastNode)) {
                    pathways.add(new Pathway(path));
                    continue;
                }
                
                for (String neighbor : graph.getOrDefault(lastNode, Collections.emptySet())) {
                    if (!path.contains(neighbor)) { // Avoid cycles
                        List<String> newPath = new ArrayList<>(path);
                        newPath.add(neighbor);
                        queue.add(newPath);
                    }
                }
            }
        }
        
        return pathways;
    }

    private boolean isPathwaySanitized(Pathway pathway, Map<String, Set<String>> graph) {
        return pathway.nodes.stream()
            .anyMatch(nodeId -> {
                Node node = nodes.stream().filter(n -> n.id.equals(nodeId)).findFirst().orElse(null);
                return node != null && "Sanitizer".equals(node.type);
            });
    }

    private void generateTestSummary() {
        long total = testResults.size();
        long passed = testResults.stream().filter(r -> "passed".equals(r.status)).count();
        
        System.out.printf("\nTest Summary: %d/%d tests passed\n", passed, total);
        testResults.forEach(r -> System.out.printf("- %s: %s (%s)\n", r.testName, r.status, r.message));
    }

    private void generateReports() {
        writeTestResults();
    }

    private void writeTestResults() {
        try (PrintWriter writer = new PrintWriter(new FileWriter("test_results.csv"))) {
            writer.println("Test Name,Status,Message");
            testResults.forEach(r -> writer.println(r.testName + "," + r.status + "," + r.message));
        } catch (IOException e) {
            System.err.println("Error writing test results: " + e.getMessage());
        }
    }

    private void writePathways(String filename, List<Pathway> pathways) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            writer.println("Path ID,Path Length,Nodes");
            for (int i = 0; i < pathways.size(); i++) {
                Pathway p = pathways.get(i);
                writer.printf("%d,%d,%s\n", i+1, p.nodes.size()-1, String.join("->", p.nodes));
            }
        } catch (IOException e) {
            System.err.println("Error writing pathways: " + e.getMessage());
        }
    }

    // Data classes
    private static class Node {
        String id;
        String type;
        
        Node(String id) {
            this.id = id;
        }
    }

    private static class Edge {
        String from;
        String to;
        
        Edge(String from, String to) {
            this.from = from;
            this.to = to;
        }
    }

    private static class Pathway {
        List<String> nodes;
        
        Pathway(List<String> nodes) {
            this.nodes = new ArrayList<>(nodes);
        }
    }

    private static class TestResult {
        String testName;
        String status;
        String message;
        
        TestResult(String testName, String status, String message) {
            this.testName = testName;
            this.status = status;
            this.message = message;
        }
    }
}