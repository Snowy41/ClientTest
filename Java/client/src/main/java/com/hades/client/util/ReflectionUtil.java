package com.hades.client.util;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class ReflectionUtil {
    
    public static Class<?> findClass(String... names) {
        com.hades.client.event.EventBus.startSection("ReflectionUtil.findClass");
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            for (String name : names) {
                try {
                    if (cl != null) return Class.forName(name, false, cl);
                } catch (ClassNotFoundException ignored) {}
            }
            for (String name : names) {
                try {
                    return Class.forName(name);
                } catch (ClassNotFoundException ignored) {}
            }
            for (Thread t : Thread.getAllStackTraces().keySet()) {
                ClassLoader tcl = t.getContextClassLoader();
                if (tcl == null) continue;
                for (String name : names) {
                    try {
                        return Class.forName(name, false, tcl);
                    } catch (ClassNotFoundException ignored) {}
                }
            }
            return null;
        } finally {
            com.hades.client.event.EventBus.endSection("ReflectionUtil.findClass");
        }
    }

    public static Field findField(Class<?> clazz, String... names) {
        com.hades.client.event.EventBus.startSection("ReflectionUtil.findField");
        try {
            Class<?> current = clazz;
            while (current != null) {
                for (String name : names) {
                    try {
                        Field f = current.getDeclaredField(name);
                        f.setAccessible(true);
                        return f;
                    } catch (NoSuchFieldException ignored) {}
                }
                current = current.getSuperclass();
            }
            return null;
        } finally {
            com.hades.client.event.EventBus.endSection("ReflectionUtil.findField");
        }
    }

    public static Method findMethod(Class<?> clazz, String[] names, Class<?>... params) {
        com.hades.client.event.EventBus.startSection("ReflectionUtil.findMethod");
        try {
            Class<?> current = clazz;
            while (current != null) {
                for (String name : names) {
                    try {
                        Method m = current.getDeclaredMethod(name, params);
                        m.setAccessible(true);
                        return m;
                    } catch (NoSuchMethodException ignored) {}
                }
                current = current.getSuperclass();
            }
            return null;
        } finally {
            com.hades.client.event.EventBus.endSection("ReflectionUtil.findMethod");
        }
    }

    // -- Unsafe Native Memory Integration --
    private static sun.misc.Unsafe unsafeInstance;
    static {
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
            theUnsafe.setAccessible(true);
            unsafeInstance = (sun.misc.Unsafe) theUnsafe.get(null);
        } catch (Throwable t) {
            unsafeInstance = null;
        }
    }

    public static long getFieldOffset(Field f) {
        if (unsafeInstance != null && f != null) {
            try {
                return unsafeInstance.objectFieldOffset(f);
            } catch (Throwable t) {}
        }
        return -1L;
    }

    public static double getDoubleFast(Object obj, long offset, Field fallback) {
        if (offset != -1L && unsafeInstance != null) {
            return unsafeInstance.getDouble(obj, offset);
        }
        return getDoubleField(obj, fallback);
    }

    public static float getFloatFast(Object obj, long offset, Field fallback) {
        if (offset != -1L && unsafeInstance != null) {
            return unsafeInstance.getFloat(obj, offset);
        }
        return getFloatField(obj, fallback);
    }

    public static boolean getBoolFast(Object obj, long offset, Field fallback) {
        if (offset != -1L && unsafeInstance != null) {
            return unsafeInstance.getBoolean(obj, offset);
        }
        return getBoolField(obj, fallback);
    }

    public static int getIntFast(Object obj, long offset, Field fallback) {
        if (offset != -1L && unsafeInstance != null) {
            return unsafeInstance.getInt(obj, offset);
        }
        return getIntField(obj, fallback);
    }

    public static void setDoubleFast(Object obj, long offset, Field fallback, double val) {
        if (offset != -1L && unsafeInstance != null) {
            unsafeInstance.putDouble(obj, offset, val);
            return;
        }
        setDoubleField(obj, fallback, val);
    }

    public static void setFloatFast(Object obj, long offset, Field fallback, float val) {
        if (offset != -1L && unsafeInstance != null) {
            unsafeInstance.putFloat(obj, offset, val);
            return;
        }
        setFloatField(obj, fallback, val);
    }

    public static Object getFieldValue(Object obj, Field f) {
        try { return obj != null && f != null ? f.get(obj) : null; } catch (Exception e) { return null; }
    }

    public static double getDoubleField(Object obj, Field f) {
        try { return obj != null && f != null ? f.getDouble(obj) : 0.0; } catch (Exception e) { return 0.0; }
    }

    public static float getFloatField(Object obj, Field f) {
        try { return obj != null && f != null ? f.getFloat(obj) : 0f; } catch (Exception e) { return 0f; }
    }

    public static int getIntField(Object obj, Field field) {
        try { return obj != null && field != null ? field.getInt(obj) : 0; } catch (Exception e) { return 0; }
    }

    public static byte getByteField(Object obj, Field field) {
        try { return obj != null && field != null ? field.getByte(obj) : 0; } catch (Exception e) { return 0; }
    }

    public static boolean getBoolField(Object obj, Field f) {
        try { return obj != null && f != null && f.getBoolean(obj); } catch (Exception e) { return false; }
    }

    public static void setDoubleField(Object obj, Field f, double v) {
        try { if (obj != null && f != null) f.setDouble(obj, v); } catch (Exception ignored) {}
    }

    public static void setFloatField(Object obj, Field f, float v) {
        try { if (obj != null && f != null) f.setFloat(obj, v); } catch (Exception ignored) {}
    }

    public static void setBoolField(Object obj, Field f, boolean v) {
        try { if (obj != null && f != null) f.setBoolean(obj, v); } catch (Exception ignored) {}
    }

    /**
     * Scans the classpath to find all classes within a specific package.
     * Supports both IDE directories and compiled JAR files.
     */
    public static java.util.Set<Class<?>> getClasses(String packageName) throws Exception {
        com.hades.client.event.EventBus.startSection("ReflectionUtil.getClasses");
        try {
            java.util.Set<Class<?>> classes = new java.util.HashSet<>();
            String path = packageName.replace('.', '/');
            ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
            if (classLoader == null) {
                classLoader = ReflectionUtil.class.getClassLoader();
            }
            
            java.util.Enumeration<java.net.URL> resources = classLoader.getResources(path);
            while (resources.hasMoreElements()) {
                java.net.URL resource = resources.nextElement();
                if (resource.getProtocol().equals("jar")) {
                    // Handle jar files
                    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                    try (java.util.jar.JarFile jar = new java.util.jar.JarFile(java.net.URLDecoder.decode(jarPath, "UTF-8"))) {
                        java.util.Enumeration<java.util.jar.JarEntry> entries = jar.entries();
                        while (entries.hasMoreElements()) {
                            java.util.jar.JarEntry entry = entries.nextElement();
                            String name = entry.getName();
                            if (name.startsWith(path) && name.endsWith(".class") && !entry.isDirectory()) {
                                String className = name.substring(0, name.length() - 6).replace('/', '.');
                                try {
                                    classes.add(Class.forName(className));
                                } catch (ClassNotFoundException ignored) {}
                            }
                        }
                    }
                } else {
                    // Handle IDE direct files
                    java.io.File directory = new java.io.File(java.net.URLDecoder.decode(resource.getFile(), "UTF-8"));
                    classes.addAll(findClasses(directory, packageName));
                }
            }
            return classes;
        } finally {
            com.hades.client.event.EventBus.endSection("ReflectionUtil.getClasses");
        }
    }

    private static java.util.List<Class<?>> findClasses(java.io.File directory, String packageName) {
        java.util.List<Class<?>> classes = new java.util.ArrayList<>();
        if (!directory.exists()) return classes;
        
        java.io.File[] files = directory.listFiles();
        if (files == null) return classes;
        
        for (java.io.File file : files) {
            if (file.isDirectory()) {
                classes.addAll(findClasses(file, packageName + "." + file.getName()));
            } else if (file.getName().endsWith(".class")) {
                try {
                    classes.add(Class.forName(packageName + '.' + file.getName().substring(0, file.getName().length() - 6)));
                } catch (ClassNotFoundException ignored) {}
            }
        }
        return classes;
    }
}
