#!/bin/bash

# Configuration
KEGG_DIR="KEGG pathways_graphml/data"
OUTPUT_DIR="output"

# Clean and prepare
rm -rf "${OUTPUT_DIR}"
mkdir -p "${OUTPUT_DIR}"

# Determine dataset type
if [ -d "${KEGG_DIR}" ]; then
    echo "Processing KEGG metabolic pathways..."
    DATASET_TYPE="kegg"
    INPUT_PREFIX="${OUTPUT_DIR}/kegg"
    
    # Convert GraphML to facts
    python3 graphml_to_fact.py "${KEGG_DIR}" "${INPUT_PREFIX}" || {
        echo "Failed to convert GraphML"
        exit 1
    }
fi

# Run validation tests
echo "Running validation tests..."
#souffle -F "${INPUT_PREFIX}_facts" -D "${OUTPUT_DIR}" test.dl || {
java Test/PathwayTester.java || {
    echo "Validation tests failed"
    [ -f "${OUTPUT_DIR}/TestResult.csv" ] && column -t -s $'\t' "${OUTPUT_DIR}/TestResult.csv"
    exit 1
}

if [ -f "TestsPassed.facts" ]; then
    echo "Tests passed. Running main analysis..."
    
    # Build the base command
    CMD="souffle -F ${OUTPUT_PREFIX}_facts -D output hospital_flows.dl -j4"
    
    # Add KEGG-specific flags if needed
    if [ "$DATASET_TYPE" = "kegg" ]; then
        CMD+=" --macro='DATASET_TYPE=kegg'"
    fi
    
    # Execute the command
    echo "Executing: $CMD"
    eval "$CMD" || {
        echo "Analysis failed"
        exit 1
    }
    
    # Generate visualization
    echo "Generating visualization..."
    cp hospital_viz.html "${OUTPUT_PREFIX}_viz.html"
    
    # Try to open in browser (cross-platform)
    if command -v xdg-open >/dev/null; then
        xdg-open "${OUTPUT_PREFIX}_viz.html" 2>/dev/null
    elif command -v open >/dev/null; then
        open "${OUTPUT_PREFIX}_viz.html" 2>/dev/null
    else
        echo "Visualization ready at: ${OUTPUT_PREFIX}_viz.html"
    fi
else
    echo "Error: Validation tests failed"
    [ -f "TestResult.csv" ] && column -t -s $'\t' TestResult.csv
    exit 1
fi