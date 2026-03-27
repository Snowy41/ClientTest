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

    public void post(Object event) {
        List<EventSubscriber> subs = subscribers.get(event.getClass());
        if (subs == null) return;
        for (EventSubscriber sub : subs) {
            try {
                sub.invoke(event);
            } catch (Exception e) {
                System.err.println("[Hades] Event execution error: " + e.getMessage());
            }
        }
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