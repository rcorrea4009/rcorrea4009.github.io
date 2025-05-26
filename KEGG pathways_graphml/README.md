Downloaded from [Graph Layout Benchmark Datasets Repository](https://visdunneright.github.io/gd_benchmark_sets/)

structure of files:

    directed: false if reversible, true otherwise
    nodes:
        id: Name of compound
        formula: Formula, if given
        description: Description, if given
    links:
        source/target: Reagent ids
        reaction_id: Code corresponding to the reaction between these reagents
        reaction_name: Reaction name
        reversible: If the reaction is reversible
        subsystem: Subsystem the reaction belongs to (usually matches file name)

NOTE: these graphs are subgraphs of a much larger metabolic pathways graph corresponding to different subsystems. They can be combined into the full pathways network.