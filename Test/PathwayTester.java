import org.w3c.dom.*;
import javax.xml.parsers.*;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class GraphMLPathwayTester {
    private static final String GRAPHML_FILE = "pathway.graphml";
    private static final String GRAPHML_NS = "http://graphml.graphdrawing.org/xmlns";

    private final List<Node> nodes = new ArrayList<>();
    private final List<Edge> edges = new ArrayList<>();
    private final List<SensitiveData> sensitiveData = new ArrayList<>();
    private final List<TestResult> testResults = new ArrayList<>();

    public static void main(String[] args) {
        GraphMLPathwayTester tester = new GraphMLPathwayTester();
        tester.runTests();
        tester.generateReports();
    }

    public void runTests() {
        loadData();
        testFileValidation();
        testNodeClassification();
        testPathwayIntegrity();
        testSanitizationCoverage();
        testDrugTargetAnalysis();
        generateTestSummary();
    }

    private void loadData() {
        try {
            // Parse GraphML file
            File inputFile = new File(GRAPHML_FILE);
            DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
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
                    String type = getNodeType(element);
                    nodes.add(new Node(id, type));

                    // Identify drug compounds as sensitive sources
                    if (type.equals("source")) {
                        sensitiveData.add(new SensitiveData("drug_" + id, id));
                    }
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
                    String interaction = getEdgeLabel(element);
                    edges.add(new Edge(from, to, interaction));
                }
            }

            testResults.add(new TestResult("graphml_loaded", "passed",
                    "GraphML file loaded successfully with " + nodes.size() + " nodes and " + edges.size() + " edges"));

        } catch (Exception e) {
            testResults.add(new TestResult("data_loading", "failed",
                    "Error loading GraphML file: " + e.getMessage()));
        }
    }

    private String getNodeType(Element nodeElement) {
        NodeList dataList = nodeElement.getElementsByTagNameNS(GRAPHML_NS, "data");
        for (int i = 0; i < dataList.getLength(); i++) {
            Element data = (Element) dataList.item(i);
            if (data.getAttribute("key").equals("type")) {
                String type = data.getTextContent().toLowerCase();
                // Map GraphML types to our pathway types
                switch (type) {
                    case "compound":
                        // Check if it's a drug compound
                        String nodeId = nodeElement.getAttribute("id");
                        if (nodeId.toLowerCase().contains("drug")) {
                            return "source";
                        }
                        return "normal";
                    case "pathway": return "sink";
                    case "enzyme": return "sanitizer";
                    case "reaction": return "normal";
                    case "group": return "normal";
                    default: return "normal";
                }
            }
        }
        return "normal"; // default type
    }

    private String getEdgeLabel(Element edgeElement) {
        NodeList dataList = edgeElement.getElementsByTagNameNS(GRAPHML_NS, "data");
        for (int i = 0; i < dataList.getLength(); i++) {
            Element data = (Element) dataList.item(i);
            if (data.getAttribute("key").equals("label")) {
                return data.getTextContent();
            }
        }
        return "";
    }

    // The rest of your existing PathwayTester implementation remains the same
    // Only the data loading parts have been modified

    private void testFileValidation() {
        boolean allLoaded = testResults.stream()
                .filter(r -> r.testName.equals("graphml_loaded"))
                .anyMatch(r -> "passed".equals(r.status));

        if (allLoaded) {
            testResults.add(new TestResult("file_validation", "passed",
                    "GraphML file loaded successfully"));
        } else {
            testResults.add(new TestResult("file_validation", "failed",
                    "Failed to load GraphML file"));
        }
    }

    // 2. Node Classification Tests
    private void testNodeClassification() {
        long sources = nodes.stream().filter(n -> "source".equals(n.type)).count();
        long sinks = nodes.stream().filter(n -> "sink".equals(n.type)).count();
        long sanitizers = nodes.stream().filter(n -> "sanitizer".equals(n.type)).count();

        String message = String.format("Found %d sources, %d sinks, and %d sanitizers",
                sources, sinks, sanitizers);

        if (sources > 0 && sinks > 0 && sanitizers > 0) {
            testResults.add(new TestResult("node_classification", "passed", message));
        } else {
            testResults.add(new TestResult("node_classification", "failed", message));
        }
    }

    // 3. Pathway Integrity Tests
    private void testPathwayIntegrity() {
        Map<String, Set<String>> reachability = buildReachabilityGraph();
        List<CompletePathway> completePathways = findCompletePathways(reachability);

        String message = String.format("Found %d complete source-to-sink pathways",
                completePathways.size());

        if (!completePathways.isEmpty()) {
            testResults.add(new TestResult("pathway_integrity", "passed", message));
            writeCompletePathways(completePathways);
        } else {
            testResults.add(new TestResult("pathway_integrity", "failed",
                    "No complete pathways found"));
        }
    }

    // 4. Sanitization Coverage Tests
    private void testSanitizationCoverage() {
        Map<String, Set<String>> reachability = buildReachabilityGraph();
        List<CompletePathway> completePathways = findCompletePathways(reachability);
        List<UnsanitizedPathway> unsanitized = findUnsanitizedPathways(completePathways);

        String message = String.format("Found %d unsanitized sensitive pathways",
                unsanitized.size());

        if (unsanitized.isEmpty()) {
            testResults.add(new TestResult("sanitization_coverage", "passed",
                    "All sensitive pathways are sanitized"));
        } else {
            testResults.add(new TestResult("sanitization_coverage", "failed", message));
            writeUnsanitizedPathways(unsanitized);
        }
    }

    // 5. Drug Target Analysis Tests
    private void testDrugTargetAnalysis() {
        List<DrugTargetInteraction> interactions = findDrugTargetInteractions();

        String message = String.format("Found %d drug-target interactions",
                interactions.size());

        if (!interactions.isEmpty()) {
            testResults.add(new TestResult("drug_target_analysis", "passed", message));
            writeDrugInteractions(interactions);
        } else {
            testResults.add(new TestResult("drug_target_analysis", "failed",
                    "No drug-target interactions found"));
        }
    }

    // Helper methods
    private Map<String, Set<String>> buildReachabilityGraph() {
        Map<String, Set<String>> graph = new HashMap<>();

        // Initialize graph with all nodes
        nodes.forEach(n -> graph.put(n.name, new HashSet<>()));

        // Add direct edges, ignoring edges with missing nodes
        edges.forEach(e -> {
            if (graph.containsKey(e.from) && graph.containsKey(e.to)) {
                graph.get(e.from).add(e.to);
            } else {
                System.err.println("Warning: Skipping edge with missing node: " + e.from + " -> " + e.to);
            }
        });

        // Compute transitive closure
        boolean changed;
        do {
            changed = false;
            for (String node : graph.keySet()) {
                Set<String> reachable = new HashSet<>(graph.get(node));
                for (String neighbor : new HashSet<>(graph.get(node))) {
                    if (graph.containsKey(neighbor)) {
                        reachable.addAll(graph.get(neighbor));
                    }
                }
                if (!reachable.equals(graph.get(node))) {
                    graph.put(node, reachable);
                    changed = true;
                }
            }
        } while (changed);

        return graph;
    }

    private List<CompletePathway> findCompletePathways(Map<String, Set<String>> reachability) {
        List<String> sources = nodes.stream()
                .filter(n -> "source".equals(n.type))
                .map(n -> n.name)
                .collect(Collectors.toList());

        List<String> sinks = nodes.stream()
                .filter(n -> "sink".equals(n.type))
                .map(n -> n.name)
                .collect(Collectors.toList());

        List<CompletePathway> pathways = new ArrayList<>();

        for (String source : sources) {
            for (String sink : sinks) {
                if (reachability.getOrDefault(source, Collections.emptySet()).contains(sink)) {
                    pathways.add(new CompletePathway(source, sink));
                }
            }
        }

        return pathways;
    }

    private List<UnsanitizedPathway> findUnsanitizedPathways(List<CompletePathway> completePathways) {
        Set<String> sensitiveNodes = sensitiveData.stream()
                .map(sd -> sd.node)
                .collect(Collectors.toSet());

        Set<String> sanitizers = nodes.stream()
                .filter(n -> "sanitizer".equals(n.type))
                .map(n -> n.name)
                .collect(Collectors.toSet());

        Map<String, Set<String>> reachability = buildReachabilityGraph();

        return completePathways.stream()
                .filter(pathway -> sensitiveNodes.contains(pathway.source))
                .filter(pathway -> !isSanitized(pathway, sanitizers, reachability))
                .map(pathway -> new UnsanitizedPathway(pathway.source, pathway.sink, "no_sanitizer"))
                .collect(Collectors.toList());
    }

    private boolean isSanitized(CompletePathway pathway, Set<String> sanitizers,
                                Map<String, Set<String>> reachability) {
        return sanitizers.stream()
                .anyMatch(sanitizer ->
                        reachability.getOrDefault(pathway.source, Collections.emptySet()).contains(sanitizer) &&
                                reachability.getOrDefault(sanitizer, Collections.emptySet()).contains(pathway.sink));
    }

    private List<DrugTargetInteraction> findDrugTargetInteractions() {
        List<String> drugs = nodes.stream()
                .filter(n -> "source".equals(n.type))
                .map(n -> n.name)
                .collect(Collectors.toList());

        List<String> targets = nodes.stream()
                .filter(n -> "sanitizer".equals(n.type))
                .map(n -> n.name)
                .collect(Collectors.toList());

        Map<String, Set<String>> reachability = buildReachabilityGraph();

        List<DrugTargetInteraction> interactions = new ArrayList<>();

        for (String drug : drugs) {
            for (String target : targets) {
                if (reachability.getOrDefault(drug, Collections.emptySet()).contains(target)) {
                    // Find pathways this drug-target pair affects
                    List<String> affectedPathways = nodes.stream()
                            .filter(n -> "sink".equals(n.type))
                            .filter(n -> reachability.getOrDefault(target, Collections.emptySet()).contains(n.name))
                            .map(n -> n.name)
                            .collect(Collectors.toList());

                    for (String pathway : affectedPathways) {
                        interactions.add(new DrugTargetInteraction(drug, target, pathway));
                    }
                }
            }
        }

        return interactions;
    }

    private void generateTestSummary() {
        long total = testResults.stream()
                .map(r -> r.testName)
                .distinct()
                .count();

        long passed = testResults.stream()
                .filter(r -> "passed".equals(r.status))
                .map(r -> r.testName)
                .distinct()
                .count();

        System.out.printf("Test Summary: %d/%d tests passed%n", passed, total);
    }

    private void generateReports() {
        writeTestResults();
        writeTestSummary();
    }

    // Data classes
    private static class Node {
        String name;
        String type;

        Node(String name, String type) {
            this.name = name;
            this.type = type;
        }
    }

    private static class Edge {
        String from;
        String to;
        String interaction;

        Edge(String from, String to, String interaction) {
            this.from = from;
            this.to = to;
            this.interaction = interaction;
        }
    }

    private static class SensitiveData {
        String label;
        String node;

        SensitiveData(String label, String node) {
            this.label = label;
            this.node = node;
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

    private static class CompletePathway {
        String source;
        String sink;

        CompletePathway(String source, String sink) {
            this.source = source;
            this.sink = sink;
        }
    }

    private static class UnsanitizedPathway {
        String source;
        String sink;
        String reason;

        UnsanitizedPathway(String source, String sink, String reason) {
            this.source = source;
            this.sink = sink;
            this.reason = reason;
        }
    }

    private static class DrugTargetInteraction {
        String drug;
        String target;
        String pathway;

        DrugTargetInteraction(String drug, String target, String pathway) {
            this.drug = drug;
            this.target = target;
            this.pathway = pathway;
        }
    }

    private interface LineParser<T> {
        T parse(String[] parts) throws IOException;
    }

    private void writeTestResults() {
        writeFile("test_results.csv", testResults.stream()
                .map(r -> String.join("\t", r.testName, r.status, r.message))
                .collect(Collectors.toList()));
    }

    private void writeTestSummary() {
        long total = testResults.stream()
                .map(r -> r.testName)
                .distinct()
                .count();

        long passed = testResults.stream()
                .filter(r -> "passed".equals(r.status))
                .map(r -> r.testName)
                .distinct()
                .count();

        writeFile("test_summary.csv",
                Collections.singletonList(String.join("\t", "total", "passed")),
                Collections.singletonList(String.join("\t", String.valueOf(total), String.valueOf(passed))));
    }

    private void writeCompletePathways(List<CompletePathway> pathways) {
        writeFile("complete_pathways.facts",
                pathways.stream()
                        .map(p -> String.join("\t", p.source, p.sink))
                        .collect(Collectors.toList()));
    }

    private void writeUnsanitizedPathways(List<UnsanitizedPathway> pathways) {
        writeFile("unverified_pathways.facts",
                pathways.stream()
                        .map(p -> String.join("\t", p.source, p.sink, p.reason))
                        .collect(Collectors.toList()));
    }

    private void writeDrugInteractions(List<DrugTargetInteraction> interactions) {
        writeFile("drug_interactions.facts",
                interactions.stream()
                        .map(i -> String.join("\t", i.drug, i.target, i.pathway))
                        .collect(Collectors.toList()));
    }

    private void writeFile(String filename, List<String> lines) {
        writeFile(filename, Collections.emptyList(), lines);
    }

    private void writeFile(String filename, List<String> headers, List<String> lines) {
        try (PrintWriter writer = new PrintWriter(new FileWriter(filename))) {
            headers.forEach(writer::println);
            lines.forEach(writer::println);
        } catch (IOException e) {
            System.err.println("Error writing file: " + filename);
            e.printStackTrace();
        }
    }
}