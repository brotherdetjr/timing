import Timing.Stage;
import Timing.StageBuilder;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

import static java.lang.System.nanoTime;
import static java.lang.Thread.sleep;
import static java.util.concurrent.CompletableFuture.allOf;
import static java.util.concurrent.Executors.newFixedThreadPool;
import static java.util.stream.Collectors.toList;

public class Main {
    public static void main(String[] args) throws Exception {
        ExecutorService executor = newFixedThreadPool(2);
        Timing timing = new Timing().append("step0");
        System.out.println(nanoTime() + ": step0/null");
        sleep(10L);
        timing.append("task0");
        System.out.println(nanoTime() + ": task0/null");
        CompletableFuture<Timing> future1 = new CompletableFuture<>();
        CompletableFuture<Void> future1a = future1.thenAccept(t -> {
            timing.append(t, "task0");
            timing.append("task0_done");
            System.out.println(nanoTime() + ": task0_done");
        });
        executor.submit(() -> {
            Timing timing1 = new Timing().append("step0");
            System.out.println(nanoTime() + ": step0/task0");
            sleep(5L);
            timing1.append("step1");
            System.out.println(nanoTime() + ": step1/task0");
            future1.complete(timing1);
            return null;
        });
        timing.append("task1");
        System.out.println(nanoTime() + ": task1/null");
        CompletableFuture<Timing> future2 = new CompletableFuture<>();
        CompletableFuture<Void> future2a = future2.thenAccept(t -> {
            timing.append(t, "task1");
            timing.append("task1_done");
            System.out.println(nanoTime() + ": task1_done");
        });
        executor.submit(() -> {
            Timing timing2 = new Timing().append("step0", "task1");
            System.out.println(nanoTime() + ": step0/task1");
            sleep(3L);
            timing2.append("step1");
            System.out.println(nanoTime() + ": step1/task1");
            sleep(3L);
            timing2.append("step2");
            System.out.println(nanoTime() + ": step2/task1");
            future2.complete(timing2);
            return null;
        });
        allOf(future1a, future2a).get();
        System.out.println("Timing: " + timing.stream().collect(toList()));
        Stage stage = new StageBuilder()
                .map("task0", "task0_done").to("STAGE_0")
                .map("task1", "task1_done").to("STAGE_1")
                .map("step0", "step1").to("SUBSTAGE_01")
                .map("step1", "step2").to("SUBSTAGE_12")
                .map("step0", "task0").to("PRESTAGE")
                .build(timing, "My complex task");
        System.out.println("Report:\n" + stage.toString());
        executor.shutdown();
    }
}
