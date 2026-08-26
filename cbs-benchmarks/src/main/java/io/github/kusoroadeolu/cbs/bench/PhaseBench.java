package io.github.kusoroadeolu.cbs.bench;

import io.github.kusoroadeolu.cbs.RPQ;
import io.github.kusoroadeolu.cbs.bench.factory.RPQFactory;
import io.github.kusoroadeolu.cbs.rmq.KQueue;
import io.github.kusoroadeolu.cbs.utils.MiscUtils;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.openjdk.jmh.profile.JavaFlightRecorderProfiler;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.concurrent.*;

/*
 * This benchmark aims to measure how long it takes in a single shot
 * to fill the queue (up to an extent) under phased inserts
 * and then fully drain the queue
 *  * */
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
@Warmup(iterations = 10, time = 1)
@Measurement(iterations = 15, time = 1)
@Fork(value = 10, jvmArgs = {
        JvmArgs.I_HEAP_ARG, JvmArgs.M_HEAP_ARG, JvmArgs.GC_TYPE_ARG
})
public class PhaseBench {

    private RPQ<Integer> queue;

    @Param({"8"})
    private String consumerProducerThreadCount;

    private ExecutorService producerEs;
    private ExecutorService consumerEs;

    @Param({RPQFactory.KQ, RPQFactory.PBQ})
    private String type;

    private CountDownLatch producerStarted;
    private CountDownLatch consumerStopped;

    private static final int DELAY_PRODUCER = 0;
    private static final int DELAY_CONSUMER = 0;
    private static final int BURST_TOTAL = MiscUtils.roundToPowerOfTwo(3_200);

    private Producer[] producers;
    private Consumer[] consumers;


    @Setup(Level.Trial)
    public void setupQueueAndProducersAndConsumers() {
        int workerCount = Integer.parseInt(consumerProducerThreadCount);
        int producerCount =  workerCount;
        int consumerCount = workerCount;

        queue = RPQFactory.createRPQ(type, 128_000);

        producers = new Producer[producerCount];


        for (int i = 0; i < producerCount; ++i) {
            producers[i] = new Producer(i, queue, (BURST_TOTAL / producerCount), new ProducerEvent());
        }

        consumers = new Consumer[consumerCount];
        for (int i = 0; i < consumerCount; i++) {
            consumers[i] = new Consumer(queue);
        }

        producerEs = Executors.newFixedThreadPool(producerCount);
        consumerEs = Executors.newFixedThreadPool(consumerCount);


        //Size the queue initially then clear it to prevent allocations during runs
        //Ideally we should have a constructor for this but im too lazy
    }

    @Setup(Level.Iteration)
    public void startAll()  {
        int workerCount = Integer.parseInt(consumerProducerThreadCount);
        int producerCount =  workerCount;
        int consumerCount = workerCount;

        CountDownLatch producerStarted = new CountDownLatch(1);
        CountDownLatch producerStopped = new CountDownLatch(producerCount); //also used to start consumers
        CountDownLatch consumerStopped = new CountDownLatch(consumerCount);
        this.producerStarted = producerStarted;
        this.consumerStopped = consumerStopped;



        for (int i = 0; i < producerCount; i++) {
            producers[i].isRunning = true;
            producers[i].event.reset();
            producers[i].stopped = producerStopped;
            producers[i].started = producerStarted;
            producerEs.execute(producers[i]);
        }



        for (int i = 0; i < consumerCount; i++) {
            consumers[i].isRunning = true;
            consumers[i].stopped = consumerStopped;
            consumers[i].started = producerStopped;
            consumerEs.execute(consumers[i]);
        }

    }

    @TearDown(Level.Trial)
    public void stopExecutor() {
        producerEs.shutdown();
        consumerEs.shutdown();
    }


    @Benchmark
    @CompilerControl(CompilerControl.Mode.DONT_INLINE)
    public void producerBurstCost() throws InterruptedException {
        producerStarted.countDown();
        consumerStopped.await();
    }

    static class LPad {
        public long p40,p41,p42,p43,p44,p45,p46;
        public long p30,p31,p32,p33,p34,p35,p36,p37;
    }


    static class SharedFields extends LPad {
        final RPQ<Integer> queue;
        CountDownLatch started;
        CountDownLatch stopped;
        volatile boolean isRunning;

        public SharedFields(RPQ<Integer> queue) {
            this.queue = queue;
        }
    }

    static class Producer extends SharedFields implements Runnable {
        final int pid;
        final ProducerEvent event;
        final int burstValue;

        public Producer(int pid, RPQ<Integer> queue, int burstValue ,ProducerEvent event) {
            this.pid = pid;
            this.burstValue = burstValue;
            this.event = event;
            super(queue);
        }

        @Override
        public void run() {
            final CountDownLatch stopped = this.stopped;
            final CountDownLatch started = this.started;
            final ProducerEvent pe = this.event;
            final RPQ<Integer> q = this.queue;
            try {
                started.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
            while (isRunning) {
                produce(q);
                if (pe.increment() == burstValue) break;
            }

            isRunning = false;
            stopped.countDown();
        }


        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        private void produce(RPQ<Integer> q) {
            q.offer(ThreadLocalRandom.current().nextInt(1_000_000));
            if (DELAY_PRODUCER > 0) Blackhole.consumeCPU(DELAY_PRODUCER);
        }
    }

    static class Consumer extends SharedFields implements Runnable{
        public Consumer(RPQ<Integer> queue) {
            super(queue);
        }

        @Override
        public void run() {
            final CountDownLatch stopped = this.stopped;
            final CountDownLatch started = this.started;
            try {
                started.await();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }

            final RPQ<Integer> q = this.queue;
            while (isRunning) {
                if (!consume(q)) break;
            }

            isRunning = false;
            stopped.countDown();
        }

        @CompilerControl(CompilerControl.Mode.DONT_INLINE)
        private boolean consume(RPQ<Integer> q)
        {
            Integer poll = q.poll();
            if (DELAY_PRODUCER > 0) Blackhole.consumeCPU(DELAY_CONSUMER);
            return poll != null;
        }
    }


    static class ProducerEvent {
        int count;

        int increment() {
            return ++count;
        }


        void reset() {
            count = 0;
        }
    }

    static class BenchRunner {
        static void main() throws RunnerException {
            Options options = new OptionsBuilder()
                    .include(PhaseBench.class.getSimpleName())
                    .addProfiler(JavaFlightRecorderProfiler.class, "dir=C:\\jfr-mpmc-pq")
                    .build();
            new org.openjdk.jmh.runner.Runner(options).run();
        }
    }
}

/*
╭ io.github.kusoroadeolu.cbs.bench.PhaseBench.producerBurstCost ─╮
│  ConsumerProducerThreadCount Type Score    Error     Unit      │
│  --------------------------- ---- -------- --------- -----     │
│  8                           KQ   2081.842 ± 317.353 us/op     │
│  8                           PBQ  1217.208 ± 90.112  us/op     │
╰────────────────────────────────────────────────────────────────╯
* */

