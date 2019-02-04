package com.ss.benchmark.httpclient.chaos;

import com.google.common.util.concurrent.RateLimiter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

public class FixedRateRunner {

    private ExecutorService taskexecutor = null;
    private Runnable task;
    private RateLimiter rateLimiter = null;
    private AtomicBoolean executing = new AtomicBoolean(false);
    private int threads = 1;

    public static FixedRateRunner create(int numberofthreads, double ratepersecond, Runnable task){
        return new FixedRateRunner(numberofthreads, ratepersecond, task);
    }

    private FixedRateRunner(int numberofthreads, double ratepersecond, Runnable task){
        this.task = task;
        threads = numberofthreads;
        rateLimiter = RateLimiter.create(ratepersecond);
    }

    public void start(){
        if (executing.get()){
            //we are executing just bail
            return;
        }
        if (threads <= 1){
            taskexecutor = Executors.newSingleThreadExecutor();
        }else{

            taskexecutor = Executors.newFixedThreadPool(threads);
        }

        Runnable producer = () -> {
            while (executing.get()) {
                //pull the rate so this rate limits us
                rateLimiter.acquire();
                //we are good to go!! execute the task
                task.run();
            }
        };

        //turn on the flag for tasks to execute
        executing.set(true);
        //queue up our task runner to execute in parallel
        for (int i = 0 ; i < threads ; i++) {
            taskexecutor.submit(producer);
        }
    }

    public void stop(){
        executing.set(false);
        taskexecutor.shutdownNow();
        try {
            taskexecutor.awaitTermination(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {

        }
    }
}
