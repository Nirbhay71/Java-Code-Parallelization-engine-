package com.parallelizer.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.CompilationUnit;

import java.io.IOException;

/**
 * Wrapper for JavaParser to convert source code into an AST (CompilationUnit).
 */
public class JavaCodeParser {

    private final JavaParser javaParser;

    public JavaCodeParser() {
        this.javaParser = new JavaParser();
    }

    public CompilationUnit parse(String code) throws IOException {
        ParseResult<CompilationUnit> result = javaParser.parse(code);
        if (result.isSuccessful() && result.getResult().isPresent()) {
            return result.getResult().get();
        } else {
            throw new IOException("Failed to parse Java code: " + result.getProblems().toString());
        }
    }
}
