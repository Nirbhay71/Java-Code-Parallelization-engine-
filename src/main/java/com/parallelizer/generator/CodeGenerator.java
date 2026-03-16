package com.parallelizer.generator;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.DefaultPrettyPrinter;

/**
 * Converts the transformed AST back into formatted Java code.
 */
public class CodeGenerator {

    public String generate(CompilationUnit cu) {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        return printer.print(cu);
    }
}
