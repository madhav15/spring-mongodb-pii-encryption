package com.madhav.poc.spring.mongo.util;

import org.bson.Document;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EncryptionReflectionUtils {

    private EncryptionReflectionUtils() {}

    // Cache: Class -> list of encrypted fields (with hash info)
    private static final Map<Class<?>, List<FieldMetadata>> FIELD_CACHE = new ConcurrentHashMap<>();

    private record FieldMetadata(String name, boolean encrypted, boolean hash, String hashFieldName, Field field) {}

    /**
     * Encrypts or decrypts all @Encrypted fields in an entity.
     */
    public static void processEntity(Object entity, EncryptionUtil encryptionUtil, boolean encrypt) {
        if (entity == null) return;

        List<FieldMetadata> fields = getFieldMetadata(entity.getClass());
        for (FieldMetadata meta : fields) {
            Field field = meta.field();
            try {
                Object value = field.get(entity);
                if (value == null) continue;

                if (meta.encrypted) {
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
     * Uses cached field metadata for performance.
     */
    public static void collectHashablePaths(Object entity, String basePath, Map<String, String> out) {
        if (entity == null) return;

        List<FieldMetadata> fields = getFieldMetadata(entity.getClass());
        for (FieldMetadata meta : fields) {
            String name = meta.name();
            String path = basePath.isEmpty() ? name : basePath + "." + name;

            try {
                Object value = meta.field().get(entity);

                if (meta.hash) {
                    String hashFieldName = meta.hashFieldName().isBlank()
                            ? (name + "_hash")
                            : meta.hashFieldName();
                    out.put(path, hashFieldName);
                }

                if (value == null) continue;

                if (!meta.field().getType().isPrimitive() && !meta.field().getType().getName().startsWith("java.")) {
                    collectHashablePaths(value, path, out);
                } else if (value instanceof Iterable<?> it) {
                    int idx = 0;
                    for (Object item : it) {
                        collectHashablePaths(item, path + "[" + idx + "]", out);
                        idx++;
                    }
                } else if (value.getClass().isArray()) {
                    int n = Array.getLength(value);
                    for (int i = 0; i < n; i++) {
                        collectHashablePaths(Array.get(value, i), path + "[" + i + "]", out);
                    }
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
     * Handles both nested documents and lists/arrays of documents.
     */
    public static void resolveAndHash(Document root,
                                      String dottedPath,
                                      String hashFieldName,
                                      EncryptionUtil encryptionUtil,
                                      java.util.function.Function<String, String> hashFn) {
        PathResolution res = resolveParentAndLeaf(root, dottedPath);
        if (res == null) return;

        if (res.parent instanceof Document parentDoc) {
            Object encryptedVal = parentDoc.get(res.leaf);
            if (encryptedVal instanceof String encText) {
                try {
                    String plaintext = encryptionUtil.decrypt(encText);
                    parentDoc.put(hashFieldName, hashFn.apply(plaintext));
                } catch (Exception ex) {
                    parentDoc.put(hashFieldName, hashFn.apply(encText));
                }
            }
        } else if (res.parent instanceof List<?> list) {
            for (Object obj : list) {
                if (obj instanceof Document element) {
                    Object encryptedVal = element.get(res.leaf);
                    if (encryptedVal instanceof String encText) {
                        try {
                            String plaintext = encryptionUtil.decrypt(encText);
                            element.put(hashFieldName, hashFn.apply(plaintext));
                        } catch (Exception ex) {
                            element.put(hashFieldName, hashFn.apply(encText));
                        }
                    }
                }
            }
        }
    }

    /**
     * Resolve a dotted path to the parent (Document or List) and the final leaf key.
     * Supports array indices like "users[0].email".
     */
    private static PathResolution resolveParentAndLeaf(Document root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        Object parent = root;

        for (int i = 0; i < parts.length - 1; i++) {
            String part = parts[i];

            // Handle array indices like "users[0]"
            if (part.contains("[") && part.endsWith("]")) {
                String fieldName = part.substring(0, part.indexOf("["));
                int idx = Integer.parseInt(part.substring(part.indexOf("[") + 1, part.indexOf("]")));
                if (!(parent instanceof Document doc)) return null;
                Object arrObj = doc.get(fieldName);
                if (arrObj instanceof List<?> list && idx < list.size()) {
                    parent = list.get(idx);
                } else {
                    return null;
                }
            } else {
                if (!(parent instanceof Document doc)) return null;
                Object next = doc.get(part);
                parent = next;
            }
        }

        return new PathResolution(parent, parts[parts.length - 1]);
    }

    private record PathResolution(Object parent, String leaf) {}

    /**
     * Build or retrieve cached metadata for a class.
     */
    private static List<FieldMetadata> getFieldMetadata(Class<?> clazz) {
        return FIELD_CACHE.computeIfAbsent(clazz, c -> {
            List<FieldMetadata> metas = new ArrayList<>();
            for (Field field : c.getDeclaredFields()) {
                field.setAccessible(true);
                boolean encrypted = field.isAnnotationPresent(Encrypted.class);
                boolean hash = false;
                String hashFieldName = "";
                if (encrypted) {
                    Encrypted ann = field.getAnnotation(Encrypted.class);
                    hash = ann.hash();
                    hashFieldName = ann.hashFieldName();
                }
                metas.add(new FieldMetadata(field.getName(), encrypted, hash, hashFieldName, field));
            }
            return metas;
        });
    }
}
