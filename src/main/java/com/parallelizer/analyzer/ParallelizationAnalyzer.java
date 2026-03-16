package com.parallelizer.analyzer;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.NameExpr;
import com.github.javaparser.ast.expr.UnaryExpr;
import com.github.javaparser.ast.expr.VariableDeclarationExpr;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ForStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Analyzes the AST to detect parallelizable patterns.
 */
public class ParallelizationAnalyzer {

    private java.util.Map<ForStmt, java.util.Set<String>> atomicMap = new java.util.HashMap<>();

    public java.util.Map<ForStmt, java.util.Set<String>> getAtomicMap() {
        return atomicMap;
    }

    public List<ForStmt> findParallelizableLoops(CompilationUnit cu) {
        List<ForStmt> loops = new ArrayList<>();
        atomicMap.clear();
        cu.accept(new VoidVisitorAdapter<List<ForStmt>>() {
            @Override
            public void visit(ForStmt n, List<ForStmt> arg) {
                super.visit(n, arg);
                Set<String> unsafeVars = getUnsafeDependencies(n);
                if (unsafeVars.isEmpty()) {
                    arg.add(n);
                } else {
                    Set<String> atomicSuggest = new HashSet<>();
                    Set<String> trulyUnsafe = new HashSet<>();
                    for (String var : unsafeVars) {
                        if (isAtomicConvertible(var, n)) {
                            atomicSuggest.add(var);
                        } else {
                            trulyUnsafe.add(var);
                        }
                    }
                    
                    if (trulyUnsafe.isEmpty() && !atomicSuggest.isEmpty()) {
                        System.out.println("Line " + n.getBegin().get().line + ": Loop is safe to parallelize with Atomic variables: " + atomicSuggest);
                        atomicMap.put(n, atomicSuggest);
                        arg.add(n);
                    } else {
                        System.out.println("Warning: Skipping loop at line " + n.getBegin().get().line + " due to critically unsafe dependencies: " + trulyUnsafe);
                        if (!atomicSuggest.isEmpty()) {
                             System.out.println("  Suggestion for future: " + atomicSuggest + " could be atomic, but other variables are critically unsafe.");
                        }
                    }
                }
            }
        }, loops);
        return loops;
    }

    private Set<String> getUnsafeDependencies(ForStmt loop) {
        Set<String> localVars = new HashSet<>();
        Set<String> unsafeVars = new HashSet<>();
        
        // Add variables from loop initialization
        loop.getInitialization().forEach(init -> {
            if (init.isVariableDeclarationExpr()) {
                init.asVariableDeclarationExpr().getVariables().forEach(v -> localVars.add(v.getNameAsString()));
            }
        });

        // Add variables declared inside the loop body
        loop.getBody().accept(new VoidVisitorAdapter<Set<String>>() {
            @Override
            public void visit(VariableDeclarationExpr n, Set<String> arg) {
                n.getVariables().forEach(v -> arg.add(v.getNameAsString()));
                super.visit(n, arg);
            }
        }, localVars);

        // Check for assignments to variables NOT in localVars
        loop.getBody().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(AssignExpr n, Void arg) {
                if (n.getTarget().isNameExpr()) {
                    String varName = n.getTarget().asNameExpr().getNameAsString();
                    if (!localVars.contains(varName)) {
                        unsafeVars.add(varName);
                    }
                }
                super.visit(n, arg);
            }

            @Override
            public void visit(UnaryExpr n, Void arg) {
                if (n.getOperator().isPostfix() || n.getOperator().isPrefix()) {
                    if (n.getExpression().isNameExpr()) {
                        String varName = n.getExpression().asNameExpr().getNameAsString();
                        if (!localVars.contains(varName)) {
                            unsafeVars.add(varName);
                        }
                    }
                }
                super.visit(n, arg);
            }
        }, null);

        return unsafeVars;
    }

    public boolean isAtomicConvertible(String varName, ForStmt loop) {
        final boolean[] onlyAtomicOps = {true};
        loop.getBody().accept(new VoidVisitorAdapter<Void>() {
            @Override
            public void visit(AssignExpr n, Void arg) {
                if (n.getTarget().isNameExpr() && n.getTarget().asNameExpr().getNameAsString().equals(varName)) {
                    AssignExpr.Operator op = n.getOperator();
                    if (op != AssignExpr.Operator.PLUS && op != AssignExpr.Operator.MINUS) {
                        onlyAtomicOps[0] = false;
                    }
                }
                super.visit(n, arg);
            }

            @Override
            public void visit(UnaryExpr n, Void arg) {
                if (n.getExpression().isNameExpr() && n.getExpression().asNameExpr().getNameAsString().equals(varName)) {
                    // ++ and -- are atomic-convertible
                }
                super.visit(n, arg);
            }
            
            // Should also check if the variable is used in other complex expressions
            @Override
            public void visit(NameExpr n, Void arg) {
                if (n.getNameAsString().equals(varName)) {
                    // If it's used as a value (not as a target of assignment/unary), it might be risky
                    // but for MVP we focus on writes.
                }
                super.visit(n, arg);
            }
        }, null);
        return onlyAtomicOps[0];
    }

    public List<BlockStmt> findParallelizableBlocks(CompilationUnit cu) {
        List<BlockStmt> blocks = new ArrayList<>();
        cu.accept(new VoidVisitorAdapter<List<BlockStmt>>() {
            @Override
            public void visit(BlockStmt n, List<BlockStmt> arg) {
                super.visit(n, arg);
                if (isParallelizableBlock(n)) {
                    arg.add(n);
                }
            }
        }, blocks);
        return blocks;
    }

    private boolean isParallelizableBlock(BlockStmt block) {
        // Simple heuristic: a block is parallelizable if it contains 2 or more 
        // consecutive method calls that don't share variables.
        // For the MVP, we just detect blocks with multiple ExpressionStmts that are MethodCallExprs.
        int callCount = 0;
        for (Statement stmt : block.getStatements()) {
            if (stmt.isExpressionStmt() && stmt.asExpressionStmt().getExpression().isMethodCallExpr()) {
                callCount++;
            }
        }
        return callCount >= 2;
    }
}
