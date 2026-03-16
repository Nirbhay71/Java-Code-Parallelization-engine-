# Java Code Parallelizer 🚀

**Java Code Parallelizer** is an automated source-to-source transformation tool that converts sequential Java programs into multithreaded versions. It uses Abstract Syntax Tree (AST) analysis to identify parallelizable patterns and injects concurrency utilities while maintaining program correctness.

## ✨ Key Features

- **Automated For-Loop Parallelization**: Automatically wraps loop bodies into `ExecutorService` tasks, utilizing multiple CPU cores.
- **Async Method Chains**: Detects independent method blocks and converts them into non-blocking `CompletableFuture` executions.
- **Smart Safety Engine**: Analyzes data dependencies within loops to prevent race conditions.
- **Automatic Atomic Transformation**: Specifically identifies shared counters (e.g., `count++`) and automatically converts them to `AtomicInteger` for thread safety.
- **Robust AST Parsing**: Built on top of [JavaParser](https://javaparser.org/) for precise code manipulation.

## 🛠️ How it Works

The tool follows a professional compiler-like pipeline:
1. **Parser**: Reads sequential code and generates an AST.
2. **Analyzer**: Detects loop patterns, method blocks, and shared variable conflicts.
3. **Engine**: Applies transformations (Atomic mapping, Thread pool injection, Lambda wrapping).
4. **Generator**: Outputs clean, formatted, and compile-ready multithreaded Java code.

## 🚀 Quick Start

### Prerequisites
- JDK 8 or higher
- `javaparser-core-3.26.1.jar` (included in `lib/`)

### Running the Parallelizer
```powershell
# Compile the tool
javac -d bin -cp "lib/*" src/main/java/com/parallelizer/**/*.java

# Run on a Java file
java -cp "bin;lib/*" com.parallelizer.demo.Main MySequentialCode.java
```

## 📊 Example Transformation

**Sequential Input:**
```java
int count = 0;
for (int i = 0; i < 1000; i++) {
    count++; // Shared variable
    processData(i);
}
```

**Parallelized Output:**
```java
AtomicInteger count = new AtomicInteger(0);
ExecutorService executor = Executors.newFixedThreadPool(AVAILABLE_CORES);
for (int i = 0; i < 1000; i++) {
    final int task_i = i;
    executor.submit(() -> {
        count.incrementAndGet();
        processData(task_i);
    });
}
executor.shutdown();
```

## ⚖️ License
This project is licensed under the MIT License.
