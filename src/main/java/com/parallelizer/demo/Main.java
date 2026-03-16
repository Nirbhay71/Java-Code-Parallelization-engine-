package com.parallelizer.demo;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.parallelizer.analyzer.ParallelizationAnalyzer;
import com.parallelizer.engine.ParallelizationEngine;
import com.parallelizer.generator.CodeGenerator;
import com.parallelizer.input.InputReader;
import com.parallelizer.output.OutputWriter;
import com.parallelizer.parser.JavaCodeParser;

import java.util.List;

/**
 * Main entry point for the Java Code Parallelizer tool.
 */
public class Main {

    public static void main(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: java com.parallelizer.demo.Main <input-file.java>");
            return;
        }

        String inputFile = args[0];
        
        try {
            System.out.println("Reading file: " + inputFile);
            InputReader reader = new InputReader();
            String code = reader.readFile(inputFile);

            System.out.println("Parsing code...");
            JavaCodeParser parser = new JavaCodeParser();
            CompilationUnit cu = parser.parse(code);

            System.out.println("Analyzing patterns...");
            ParallelizationAnalyzer analyzer = new ParallelizationAnalyzer();
            List<ForStmt> loops = analyzer.findParallelizableLoops(cu);
            List<BlockStmt> blocks = analyzer.findParallelizableBlocks(cu);
            System.out.println("Found " + loops.size() + " loops and " + blocks.size() + " method blocks.");

            System.out.println("Applying parallelization transformations...");
            ParallelizationEngine engine = new ParallelizationEngine();
            engine.parallelizeLoops(cu, loops, analyzer.getAtomicMap());
            engine.parallelizeMethodCalls(cu, blocks);

            System.out.println("Generating output code...");
            CodeGenerator generator = new CodeGenerator();
            String result = generator.generate(cu);

            OutputWriter writer = new OutputWriter();
            writer.writeToConsole(result);
            
            String outputFile = inputFile.replace(".java", "_parallel.java");
            writer.writeFile(outputFile, result);
            System.out.println("Saved parallelized code to: " + outputFile);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
