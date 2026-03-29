package com.hades.client.event;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

public class EventBus {
    private final Map<Class<?>, List<EventSubscriber>> subscribers = new ConcurrentHashMap<>();

    public void register(Object listener) {
        for (Method method : listener.getClass().getDeclaredMethods()) {
            if (method.isAnnotationPresent(EventHandler.class)
                    && method.getParameterCount() == 1) {
                method.setAccessible(true);
                Class<?> eventType = method.getParameterTypes()[0];
                int priority = method.getAnnotation(EventHandler.class).priority();

                subscribers.computeIfAbsent(eventType, k -> new CopyOnWriteArrayList<>())
                        .add(new EventSubscriber(listener, method, priority, eventType));

                // Sort by priority (higher first)
                subscribers.get(eventType).sort(
                        Comparator.comparingInt(EventSubscriber::getPriority).reversed());
            }
        }
    }

    public void unregister(Object listener) {
        for (List<EventSubscriber> subs : subscribers.values()) {
            subs.removeIf(s -> s.getListener() == listener);
        }
    }

    // --- Profiler System ---
    public static boolean isProfilerEnabled = false;
    public static final ConcurrentHashMap<String, ProfilerEntry> PROFILER_METRICS = new ConcurrentHashMap<>();
    private static long lastMetricClear = System.currentTimeMillis();

    public static class ProfilerEntry {
        public long liveNanos = 0;
        public long liveAllocatedBytes = 0;
        public int liveInvocations = 0;

        public long lastSecNanos = 0;
        public long lastSecAllocatedBytes = 0;
        public int lastSecInvocations = 0;
        
        public void cycle() {
            lastSecNanos = liveNanos;
            lastSecAllocatedBytes = liveAllocatedBytes;
            lastSecInvocations = liveInvocations;
            liveNanos = 0;
            liveAllocatedBytes = 0;
            liveInvocations = 0;
        }
    }

    private static java.lang.reflect.Method getThreadAllocatedBytesMethod;
    private static Object threadMXBean;

    static {
        try {
            threadMXBean = java.lang.management.ManagementFactory.getThreadMXBean();
            getThreadAllocatedBytesMethod = threadMXBean.getClass().getMethod("getThreadAllocatedBytes", long.class);
            getThreadAllocatedBytesMethod.setAccessible(true);
        } catch (Throwable t) {
            getThreadAllocatedBytesMethod = null;
        }
    }

    public void post(Object event) {
        List<EventSubscriber> subs = subscribers.get(event.getClass());
        if (subs == null) return;

        if (!isProfilerEnabled) {
            // Fast path
            for (EventSubscriber sub : subs) {
                try {
                    sub.invoke(event);
                } catch (Exception e) {
                    System.err.println("[Hades] Event execution error: " + e.getMessage());
                }
            }
            return;
        }

        // Profiled path
        long currentMs = System.currentTimeMillis();
        if (currentMs - lastMetricClear > 1000) {
            for (ProfilerEntry entry : PROFILER_METRICS.values()) {
                entry.cycle();
            }
            lastMetricClear = currentMs;
        }

        long threadId = Thread.currentThread().getId();
        for (EventSubscriber sub : subs) {
            long startNanos = System.nanoTime();
            long startAlloc = 0;
            if (getThreadAllocatedBytesMethod != null) {
                try { startAlloc = (long) getThreadAllocatedBytesMethod.invoke(threadMXBean, threadId); } catch (Throwable t) {}
            }

            try {
                sub.invoke(event);
            } catch (Exception e) {
                System.err.println("[Hades] Event execution error: " + e.getMessage());
            }

            long elapsedNanos = System.nanoTime() - startNanos;
            long endAlloc = 0;
            if (getThreadAllocatedBytesMethod != null) {
                try { endAlloc = (long) getThreadAllocatedBytesMethod.invoke(threadMXBean, threadId); } catch (Throwable t) {}
            }

            String identifier = sub.getListener().getClass().getSimpleName();
            ProfilerEntry entry = PROFILER_METRICS.computeIfAbsent(identifier, k -> new ProfilerEntry());
            entry.liveNanos += elapsedNanos;
            long allocated = endAlloc - startAlloc;
            if (allocated > 0) entry.liveAllocatedBytes += allocated;
            entry.liveInvocations++;
        }
    }

    // --- Global Profiling API ---
    private static final ThreadLocal<Map<String, Long[]>> threadMetrics = ThreadLocal.withInitial(ConcurrentHashMap::new);

    public static void startSection(String name) {
        if (!isProfilerEnabled) return;
        long threadId = Thread.currentThread().getId();
        long startAlloc = 0;
        if (getThreadAllocatedBytesMethod != null) {
            try { startAlloc = (long) getThreadAllocatedBytesMethod.invoke(threadMXBean, threadId); } catch (Throwable t) {}
        }
        threadMetrics.get().put(name, new Long[]{System.nanoTime(), startAlloc});
    }

    public static void endSection(String name) {
        if (!isProfilerEnabled) return;
        Long[] startData = threadMetrics.get().remove(name);
        if (startData == null) return;

        long elapsedNanos = System.nanoTime() - startData[0];
        long endAlloc = 0;
        long threadId = Thread.currentThread().getId();
        if (getThreadAllocatedBytesMethod != null) {
            try { endAlloc = (long) getThreadAllocatedBytesMethod.invoke(threadMXBean, threadId); } catch (Throwable t) {}
        }

        ProfilerEntry entry = PROFILER_METRICS.computeIfAbsent(name, k -> new ProfilerEntry());
        entry.liveNanos += elapsedNanos;
        long allocated = endAlloc - startData[1];
        if (allocated > 0) entry.liveAllocatedBytes += allocated;
        entry.liveInvocations++;
    }

    private static class EventSubscriber {
        private final Object listener;
        private final int priority;
        private final java.util.function.Consumer<Object> executor;

        @SuppressWarnings("unchecked")
        EventSubscriber(Object listener, Method method, int priority, Class<?> eventType) {
            this.listener = listener;
            this.priority = priority;

            java.util.function.Consumer<Object> lambdaExecutor = null;
            try {
                method.setAccessible(true);
                
                // Try to create high-performance lambda wrapper
                java.lang.invoke.MethodHandles.Lookup lookup = java.lang.invoke.MethodHandles.lookup();
                // For Java 9+, we might need privateLookupIn if method is private, but we'll try standard unreflect first.
                // Assuming methods are public or package-private in the same hierarchy:
                java.lang.invoke.MethodHandles.Lookup caller = lookup.in(listener.getClass());
                java.lang.invoke.MethodHandle methodHandle = caller.unreflect(method);
                
                java.lang.invoke.CallSite site = java.lang.invoke.LambdaMetafactory.metafactory(
                        caller,
                        "accept",
                        java.lang.invoke.MethodType.methodType(java.util.function.Consumer.class, listener.getClass()),
                        java.lang.invoke.MethodType.methodType(void.class, Object.class),
                        methodHandle,
                        java.lang.invoke.MethodType.methodType(void.class, eventType)
                );
                
                lambdaExecutor = (java.util.function.Consumer<Object>) site.getTarget().invoke(listener);
            } catch (Throwable e) {
                // Fallback to Reflection if LambdaMetafactory fails (e.g. for private methods on strict JVMs)
                lambdaExecutor = event -> {
                    try {
                        method.invoke(listener, event);
                    } catch (Exception ex) {
                        System.err.println("[Hades] Event Reflection error: " + ex.getMessage());
                    }
                };
            }
            this.executor = lambdaExecutor;
        }

        void invoke(Object event) {
            executor.accept(event);
        }

        Object getListener() { return listener; }
        int getPriority() { return priority; }
    }
}