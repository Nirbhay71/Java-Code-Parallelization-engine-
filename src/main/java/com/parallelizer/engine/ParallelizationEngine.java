package com.parallelizer.engine;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.List;

/**
 * Transforms sequential patterns into multithreaded equivalents.
 */
public class ParallelizationEngine {
 
    public void parallelizeLoops(CompilationUnit cu, List<ForStmt> loops) {
        parallelizeLoops(cu, loops, new java.util.HashMap<>());
    }

    public void parallelizeLoops(CompilationUnit cu, List<ForStmt> loops, java.util.Map<ForStmt, java.util.Set<String>> atomicMap) {
        if (loops.isEmpty()) return;

        cu.addImport("java.util.concurrent.ExecutorService");
        cu.addImport("java.util.concurrent.Executors");
        cu.addImport("java.util.concurrent.TimeUnit");
        cu.addImport("java.util.concurrent.atomic.AtomicInteger");

        for (ForStmt loop : loops) {
            transformLoop(loop, atomicMap.getOrDefault(loop, new java.util.HashSet<>()));
        }
    }

    private void transformLoop(ForStmt loop, java.util.Set<String> atomicVars) {
        Statement body = loop.getBody();
        Expression initializer = loop.getInitialization().get(0);
        
        String iterVar = "i";
        if (initializer.isVariableDeclarationExpr()) {
             VariableDeclarator decl = initializer.asVariableDeclarationExpr().getVariable(0);
             iterVar = decl.getNameAsString();
        }

        ExpressionStmt poolInit = StaticJavaParser.parseStatement(
            "ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());"
        ).asExpressionStmt();

        String finalVar = "task_" + iterVar;
        BlockStmt newBody = new BlockStmt();
        newBody.addStatement(StaticJavaParser.parseStatement("final int " + finalVar + " = " + iterVar + ";"));
        
        body.accept(new VoidVisitorAdapter<String>() {
            @Override
            public void visit(NameExpr n, String arg) {
                if (n.getNameAsString().equals(arg)) {
                    n.setName("task_" + arg);
                }
            }
            
            @Override
            public void visit(UnaryExpr n, String arg) {
                super.visit(n, arg);
                if (n.getExpression().isNameExpr()) {
                    String varName = n.getExpression().asNameExpr().getNameAsString();
                    if (atomicVars.contains(varName)) {
                        if (n.getOperator() == UnaryExpr.Operator.POSTFIX_INCREMENT || n.getOperator() == UnaryExpr.Operator.PREFIX_INCREMENT) {
                             n.replace(new MethodCallExpr(n.getExpression(), "incrementAndGet"));
                        } else if (n.getOperator() == UnaryExpr.Operator.POSTFIX_DECREMENT || n.getOperator() == UnaryExpr.Operator.PREFIX_DECREMENT) {
                             n.replace(new MethodCallExpr(n.getExpression(), "decrementAndGet"));
                        }
                    }
                }
            }

            @Override
            public void visit(AssignExpr n, String arg) {
                super.visit(n, arg);
                if (n.getTarget().isNameExpr()) {
                    String varName = n.getTarget().asNameExpr().getNameAsString();
                    if (atomicVars.contains(varName)) {
                        if (n.getOperator() == AssignExpr.Operator.PLUS) {
                            n.replace(new MethodCallExpr(n.getTarget(), "addAndGet", new NodeList<>(n.getValue())));
                        } else if (n.getOperator() == AssignExpr.Operator.MINUS) {
                            n.replace(new MethodCallExpr(n.getTarget(), "addAndGet", new NodeList<>(new UnaryExpr(n.getValue(), UnaryExpr.Operator.MINUS))));
                        }
                    }
                }
            }
        }, iterVar);
        
        LambdaExpr lambda = new LambdaExpr();
        lambda.setEnclosingParameters(true);
        lambda.setBody(body);
        
        MethodCallExpr submitCall = new MethodCallExpr(new NameExpr("executor"), "submit");
        submitCall.addArgument(lambda);
        newBody.addStatement(new ExpressionStmt(submitCall));
        
        loop.setBody(newBody);

        Statement shutdown = StaticJavaParser.parseStatement("executor.shutdown();");
        Statement await = StaticJavaParser.parseStatement(
            "try { executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS); } catch (InterruptedException e) { e.printStackTrace(); }"
        );

        BlockStmt parent = (BlockStmt) loop.getParentNode().get();
        NodeList<Statement> stmts = parent.getStatements();
        int index = stmts.indexOf(loop);
        
        // Add after the loop first (to not shift indices for the loop itself yet)
        stmts.add(index + 1, shutdown);
        stmts.add(index + 2, await);
        
        // Add before the loop
        stmts.add(index, poolInit);

        // Modify declarations for atomic variables
        for (String varName : atomicVars) {
            findAndModifyDeclaration(parent, varName);
        }
    }

    private void findAndModifyDeclaration(NodeList<Statement> statements, String varName) {
        for (Statement stmt : statements) {
            if (stmt.isExpressionStmt() && stmt.asExpressionStmt().getExpression().isVariableDeclarationExpr()) {
                VariableDeclarationExpr declExpr = stmt.asExpressionStmt().getExpression().asVariableDeclarationExpr();
                for (VariableDeclarator declarator : declExpr.getVariables()) {
                    if (declarator.getNameAsString().equals(varName)) {
                        declarator.setType(StaticJavaParser.parseType("AtomicInteger"));
                        if (declarator.getInitializer().isPresent()) {
                            Expression oldInit = declarator.getInitializer().get();
                            declarator.setInitializer(new ObjectCreationExpr(null, StaticJavaParser.parseClassOrInterfaceType("AtomicInteger"), new NodeList<>(oldInit)));
                        }
                        return;
                    }
                }
            }
        }
    }

    private void findAndModifyDeclaration(BlockStmt block, String varName) {
        // Simple search in the current block
        findAndModifyDeclaration(block.getStatements(), varName);
    }

    public void parallelizeMethodCalls(CompilationUnit cu, List<BlockStmt> blocks) {
        if (blocks.isEmpty()) return;

        cu.addImport("java.util.concurrent.CompletableFuture");

        for (BlockStmt block : blocks) {
            transformBlock(block);
        }
    }

    private void transformBlock(BlockStmt block) {
        List<Statement> statements = block.getStatements();
        List<Statement> newStatements = new java.util.ArrayList<>();
        List<String> futureNames = new java.util.ArrayList<>();

        int futureCount = 0;
        for (Statement stmt : statements) {
            if (stmt.isExpressionStmt() && stmt.asExpressionStmt().getExpression().isMethodCallExpr()) {
                String futureName = "f" + (++futureCount);
                futureNames.add(futureName);

                // Create: CompletableFuture<Void> f1 = CompletableFuture.runAsync(() -> methodCall());
                LambdaExpr lambda = new LambdaExpr();
                lambda.setEnclosingParameters(true);
                lambda.setBody(stmt.clone());

                MethodCallExpr runAsync = new MethodCallExpr(new NameExpr("CompletableFuture"), "runAsync");
                runAsync.addArgument(lambda);

                VariableDeclarator dec = new VariableDeclarator(
                    StaticJavaParser.parseType("CompletableFuture<Void>"),
                    futureName,
                    runAsync
                );
                
                VariableDeclarationExpr declExpr = new VariableDeclarationExpr(dec);
                newStatements.add(new ExpressionStmt(declExpr));
            } else {
                newStatements.add(stmt.clone());
            }
        }

        // Add CompletableFuture.allOf(f1, f2).join();
        if (!futureNames.isEmpty()) {
            MethodCallExpr allOf = new MethodCallExpr(new NameExpr("CompletableFuture"), "allOf");
            for (String name : futureNames) {
                allOf.addArgument(new NameExpr(name));
            }
            MethodCallExpr join = new MethodCallExpr(allOf, "join");
            newStatements.add(new ExpressionStmt(join));
        }

        block.setStatements(new NodeList<>(newStatements));
    }
}
