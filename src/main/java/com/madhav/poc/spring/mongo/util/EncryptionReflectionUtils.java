package com.madhav.poc.spring.mongo.util;

import org.bson.Document;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class EncryptionReflectionUtils {

    private EncryptionReflectionUtils() {}

    /**
     * Encrypts or decrypts all @Encrypted fields in an entity.
     */
    public static void processEntity(Object entity, EncryptionUtil encryptionUtil, boolean encrypt) {
        if (entity == null) return;

        Class<?> clazz = entity.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                if (value == null) continue;

                if (field.isAnnotationPresent(Encrypted.class)) {
                    if (value instanceof String strVal) {
                        field.set(entity, encrypt ? encryptionUtil.encrypt(strVal) : encryptionUtil.decrypt(strVal));
                    } else if (value instanceof LocalDateTime dateTime) {
                        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                        String str = dateTime.format(formatter);
                        String processed = encrypt ? encryptionUtil.encrypt(str) : encryptionUtil.decrypt(str);
                        field.set(entity, LocalDateTime.parse(processed, formatter));
                    }
                } else if (!field.getType().isPrimitive() && !field.getType().getName().startsWith("java.")) {
                    processEntity(value, encryptionUtil, encrypt);
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) processEntity(item, encryptionUtil, encrypt);
                } else if (value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) processEntity(Array.get(value, i), encryptionUtil, encrypt);
                } else if (value instanceof Map<?, ?> map) {
                    for (Object entryObj : map.entrySet()) {
                        processEntity(((Map.Entry<?, ?>) entryObj).getValue(), encryptionUtil, encrypt);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error processing entity encryption", e);
            }
        }
    }

    /**
     * Collect all paths of fields marked with @Encrypted(hash=true).
     */
    public static void collectHashablePaths(Object entity, String basePath, Map<String, String> out) {
        if (entity == null) return;

        Class<?> clazz = entity.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            String name = field.getName();
            String path = basePath.isEmpty() ? name : basePath + "." + name;

            try {
                Object value = field.get(entity);

                if (field.isAnnotationPresent(Encrypted.class)) {
                    Encrypted annotation = field.getAnnotation(Encrypted.class);
                    if (annotation.hash()) {
                        String hashFieldName = annotation.hashFieldName().isBlank() ? (name + "_hash") : annotation.hashFieldName();
                        out.put(path, hashFieldName);
                    }
                }

                if (value == null) continue;

                if (!field.getType().isPrimitive() && !field.getType().getName().startsWith("java.")) {
                    collectHashablePaths(value, path, out);
                } else if (value instanceof Iterable<?> it) {
                    for (Object item : it) collectHashablePaths(item, path, out);
                } else if (value.getClass().isArray()) {
                    int n = Array.getLength(value);
                    for (int i = 0; i < n; i++) collectHashablePaths(Array.get(value, i), path, out);
                } else if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> me : map.entrySet()) {
                        collectHashablePaths(me.getValue(), path + "." + me.getKey(), out);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error collecting hashable paths", e);
            }
        }
    }

    /**
     * Resolves path in BSON Document, decrypts, hashes, and adds hash field.
     */
    public static void resolveAndHash(Document root,
                                      String dottedPath,
                                      String hashFieldName,
                                      EncryptionUtil encryptionUtil,
                                      java.util.function.Function<String, String> hashFn) {
        PathResolution res = resolveParentAndLeaf(root, dottedPath);
        if (res == null || !(res.parent instanceof Document parentDoc)) return;

        Object encryptedVal = parentDoc.get(res.leaf);
        if (encryptedVal instanceof String encText) {
            try {
                String plaintext = encryptionUtil.decrypt(encText);
                parentDoc.put(hashFieldName, hashFn.apply(plaintext));
            } catch (Exception ex) {
                parentDoc.put(hashFieldName, hashFn.apply(encText));
            }
        }
    }

    private static PathResolution resolveParentAndLeaf(Document root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        Document parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = parent.get(parts[i]);
            if (!(next instanceof Document)) return null;
            parent = (Document) next;
        }
        return new PathResolution(parent, parts[parts.length - 1]);
    }

    private record PathResolution(Object parent, String leaf) {}
}
