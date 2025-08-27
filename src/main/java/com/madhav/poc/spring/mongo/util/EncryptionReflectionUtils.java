package com.madhav.poc.spring.mongo.util;

import org.bson.Document;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.util.*;
import java.util.function.Function;

public class EncryptionReflectionUtils {

    private EncryptionReflectionUtils() {}

    // ======== ENTITY PROCESSING (encrypt/decrypt) ========

    public static void processEntity(Object entity, EncryptionUtil encryptionUtil, boolean encrypt) {
        if (entity == null) return;
        Class<?> clazz = entity.getClass();

        for (Field f : clazz.getDeclaredFields()) {
            f.setAccessible(true);
            try {
                Object value = f.get(entity);
                if (value == null) continue;

                boolean annotated = f.isAnnotationPresent(Encrypted.class);

                // Case 1: Annotated scalar String
                if (annotated && value instanceof String str) {
                    if (encrypt && !encryptionUtil.isEncrypted(str)) {
                        f.set(entity, encryptionUtil.encrypt(str));
                    } else if (!encrypt && encryptionUtil.isEncrypted(str)) {
                        f.set(entity, encryptionUtil.decrypt(str));
                    }
                    continue;
                }

                // Case 2: Annotated Collections/Arrays/Maps containing Strings
                if (annotated) {
                    if (value instanceof Iterable<?> it) {
                        encryptIterableStrings(it, encryptionUtil, encrypt);
                        continue;
                    }
                    if (value.getClass().isArray()) {
                        encryptArrayStrings(value, encryptionUtil, encrypt);
                        continue;
                    }
                    if (value instanceof Map<?, ?> map) {
                        encryptMapStringValues((Map<?, ?>) map, encryptionUtil, encrypt);
                        continue;
                    }
                }

                // Case 3: Nested custom object / collections / arrays / maps
                if (!isJavaLangOrPrimitive(f.getType())) {
                    processEntity(value, encryptionUtil, encrypt);
                } else if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) processEntity(item, encryptionUtil, encrypt);
                } else if (value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) processEntity(Array.get(value, i), encryptionUtil, encrypt);
                } else if (value instanceof Map<?, ?> m) {
                    for (Object entryObj : m.entrySet()) {
                        Map.Entry<?, ?> e = (Map.Entry<?, ?>) entryObj;
                        processEntity(e.getValue(), encryptionUtil, encrypt);
                    }
                }

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error processing encryption", e);
            }
        }
    }

    private static void encryptIterableStrings(Iterable<?> it, EncryptionUtil util, boolean encrypt) {
        if (!(it instanceof List<?>)) {
            // best-effort: iterate and replace if possible (only List allows set)
            int idx = 0;
            for (Object ignored : it) idx++;
            return;
        }
        @SuppressWarnings("unchecked")
        List<Object> list = (List<Object>) it;
        ListIterator<Object> li = list.listIterator();
        while (li.hasNext()) {
            Object v = li.next();
            if (v instanceof String s) {
                if (encrypt && !util.isEncrypted(s)) li.set(util.encrypt(s));
                else if (!encrypt && util.isEncrypted(s)) li.set(util.decrypt(s));
            } else {
                processEntity(v, util, encrypt);
            }
        }
    }

    private static void encryptArrayStrings(Object array, EncryptionUtil util, boolean encrypt) {
        int len = Array.getLength(array);
        for (int i = 0; i < len; i++) {
            Object v = Array.get(array, i);
            if (v instanceof String s) {
                if (encrypt && !util.isEncrypted(s)) Array.set(array, i, util.encrypt(s));
                else if (!encrypt && util.isEncrypted(s)) Array.set(array, i, util.decrypt(s));
            } else {
                processEntity(v, util, encrypt);
            }
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void encryptMapStringValues(Map<?, ?> map, EncryptionUtil util, boolean encrypt) {
        for (Map.Entry<?, ?> e : map.entrySet()) {
            Object v = e.getValue();
            if (v instanceof String s) {
                Map.Entry rawEntry = (Map.Entry) e; // raw type to bypass wildcard
                if (encrypt && !util.isEncrypted(s)) {
                    rawEntry.setValue(util.encrypt(s));
                } else if (!encrypt && util.isEncrypted(s)) {
                    rawEntry.setValue(util.decrypt(s));
                }
            } else {
                processEntity(v, util, encrypt); // recurse
            }
        }
    }



    private static boolean isJavaLangOrPrimitive(Class<?> type) {
        return type.isPrimitive() || type.getName().startsWith("java.");
    }

    // ======== HASH PATH COLLECTION (from entity shape) ========

    public static void collectHashablePaths(Object entity, String basePath, Map<String, String> out) {
        if (entity == null) return;
        Class<?> clazz = entity.getClass();

        for (Field f : clazz.getDeclaredFields()) {
            f.setAccessible(true);
            final String name = f.getName();
            final String path = basePath.isEmpty() ? name : basePath + "." + name;

            try {
                Object value = f.get(entity);

                if (f.isAnnotationPresent(Encrypted.class)) {
                    Encrypted ann = f.getAnnotation(Encrypted.class);
                    if (ann.hash()) {
                        String hashField = ann.hashFieldName().isBlank() ? (name + "_hash") : ann.hashFieldName();
                        // Only add once per path (LinkedHashMap in caller keeps last)
                        out.put(path, hashField);
                    }
                }

                if (value == null) continue;

                if (!isJavaLangOrPrimitive(f.getType())) {
                    collectHashablePaths(value, path, out);
                } else if (value instanceof Iterable<?> it) {
                    for (Object item : it) collectHashablePaths(item, path, out); // no indices; resolver will fan-out
                } else if (value.getClass().isArray()) {
                    int n = Array.getLength(value);
                    for (int i = 0; i < n; i++) collectHashablePaths(Array.get(value, i), path, out);
                } else if (value instanceof Map<?, ?> m) {
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) m).entrySet()) {
                        collectHashablePaths(entry.getValue(), path, out); // resolver will iterate all map values
                    }
                }

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error collecting hashable paths", e);
            }
        }
    }

    // ======== DOCUMENT HASH RESOLUTION (handles nested Docs, Lists, Arrays, Maps) ========

    public static void resolveAndHash(Document root,
                                      String dottedPath,
                                      String hashFieldName,
                                      EncryptionUtil encryptionUtil,
                                      Function<String, String> hashFn) {
        if (root == null) return;
        String[] parts = dottedPath.split("\\.");
        // recursive fan-out traversal across Document/List/Map
        resolveRecurse(root, parts, 0, hashFieldName, encryptionUtil, hashFn);
    }

    @SuppressWarnings("unchecked")
    private static void resolveRecurse(Object node,
                                       String[] parts,
                                       int idx,
                                       String hashFieldName,
                                       EncryptionUtil util,
                                       Function<String, String> hashFn) {
        if (node == null) return;

        // If we encounter a List or Map before consuming 'parts[idx]', we need to fan-out
        if (node instanceof List<?> list) {
            for (Object el : list) resolveRecurse(el, parts, idx, hashFieldName, util, hashFn);
            return;
        }
        if (node instanceof Map<?, ?> map && !(node instanceof Document)) {
            for (Object v : map.values()) resolveRecurse(v, parts, idx, hashFieldName, util, hashFn);
            return;
        }

        if (!(node instanceof Document doc)) return;

        if (idx == parts.length - 1) {
            // Reached the leaf field name inside this Document (and/or inside list/map elements)
            String leaf = parts[idx];
            Object fieldVal = doc.get(leaf);

            // If the leaf itself is a List or Map, fan-out and try to find strings inside child Documents
            if (fieldVal instanceof List<?> lfList) {
                for (Object el : lfList) {
                    if (el instanceof Document child) {
                        hashInDocument(child, hashFieldName, util, hashFn, leaf /*unused here*/);
                    } else if (el instanceof String s) {
                        // List<String>: cannot place sibling '<leaf>_hash' per element without changing schema → skip
                    }
                }
                return;
            }
            if (fieldVal instanceof Map<?, ?> lfMap && !(fieldVal instanceof Document)) {
                for (Object v : ((Map<?, ?>) lfMap).values()) {
                    if (v instanceof Document child) {
                        hashInDocument(child, hashFieldName, util, hashFn, leaf /*unused*/);
                    }
                }
                return;
            }

            // Normal case: the leaf is a String within this Document
            if (fieldVal instanceof String s) {
                String plaintext = util.isEncrypted(s) ? safeDecrypt(util, s) : s;
                String computed = hashFn.apply(plaintext);
                Object existing = doc.get(hashFieldName);
                if (!(existing instanceof String ex && ex.equals(computed))) {
                    doc.put(hashFieldName, computed); // idempotent and updates if value changed
                }
            }
            return;
        }

        String key = parts[idx];
        Object child = doc.get(key);

        if (child == null) return;

        if (child instanceof Document cdoc) {
            resolveRecurse(cdoc, parts, idx + 1, hashFieldName, util, hashFn);
        } else if (child instanceof List<?> list) {
            for (Object el : list) resolveRecurse(el, parts, idx + 1, hashFieldName, util, hashFn);
        } else if (child instanceof Map<?, ?> cmap && !(child instanceof Document)) {
            for (Object v : ((Map<?, ?>) cmap).values()) resolveRecurse(v, parts, idx + 1, hashFieldName, util, hashFn);
        }
    }

    private static void hashInDocument(Document doc,
                                       String hashFieldName,
                                       EncryptionUtil util,
                                       Function<String, String> hashFn,
                                       String leafName) {
        // helper for exotic structures; here we expect the string already in this doc under some key
        // Not used to look up by leafName; reserved for fan-out cases if needed later.
        for (Map.Entry<String, Object> e : doc.entrySet()) {
            if (e.getValue() instanceof String s) {
                String plaintext = util.isEncrypted(s) ? safeDecrypt(util, s) : s;
                String computed = hashFn.apply(plaintext);
                Object existing = doc.get(hashFieldName);
                if (!(existing instanceof String ex && ex.equals(computed))) {
                    doc.put(hashFieldName, computed);
                }
                return;
            }
        }
    }

    private static String safeDecrypt(EncryptionUtil util, String enc) {
        try {
            return util.decrypt(enc);
        } catch (Exception ex) {
            return enc; // fallback
        }
    }
}
