package engine.core;

import engine.metrics.ExecutionMetrics;
import engine.scheduler.TaskQueue;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages the lifecycle of worker threads.
 */
public class ThreadPoolManager {

    private final List<WorkerThread> workers = new ArrayList<>();

    public ThreadPoolManager(int numThreads, TaskQueue queue, ExecutionMetrics metrics) {
        for (int i = 0; i < numThreads; i++) {
            WorkerThread worker = new WorkerThread(queue, metrics);
            worker.setName("Worker-" + i);
            workers.add(worker);
            worker.start();
        }
    }

    public void shutdown() {
        for (WorkerThread worker : workers) {
            worker.shutdown();
        }
    }
}
