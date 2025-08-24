package com.madhav.poc.spring.mongo.listener;


import com.madhav.poc.spring.mongo.model.Order;
import com.madhav.poc.spring.mongo.util.Encrypted;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
import com.madhav.poc.spring.mongo.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Component
@RequiredArgsConstructor
public class MongoEncryptionListener implements
        BeforeConvertCallback<Order>,
        AfterConvertCallback<Order>,
        BeforeSaveCallback<Order> {

    private final EncryptionUtil encryptionUtil;

    private void processFields(Object entity, boolean encrypt) {
        if (entity == null) return;

        Class<?> clazz = entity.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            field.setAccessible(true);
            try {
                Object value = field.get(entity);
                if (value == null) continue;

                // Encrypt/Decrypt annotated fields
                if (field.isAnnotationPresent(Encrypted.class)) {
                    if (value instanceof String strVal) {
                        String result = encrypt
                                ? encryptionUtil.encrypt(strVal)
                                : encryptionUtil.decrypt(strVal);
                        field.set(entity, result);

                    } else if (value instanceof LocalDateTime dateTime) {
                        DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
                        String str = dateTime.format(formatter);
                        String processed = encrypt
                                ? encryptionUtil.encrypt(str)
                                : encryptionUtil.decrypt(str);
                        field.set(entity, LocalDateTime.parse(processed, formatter));
                    }
                }
                // Nested custom objects
                else if (!field.getType().isPrimitive()
                        && !field.getType().getName().startsWith("java.")) {
                    processFields(value, encrypt);
                }
                // Handle collections
                else if (value instanceof Iterable<?> iterable) {
                    for (Object item : iterable) processFields(item, encrypt);
                }
                // Handle arrays
                else if (value.getClass().isArray()) {
                    int length = Array.getLength(value);
                    for (int i = 0; i < length; i++) {
                        processFields(Array.get(value, i), encrypt);
                    }
                }
                // Handle maps
                else if (value instanceof Map<?, ?> map) {
                    for (Object entryObj : map.entrySet()) {
                        Map.Entry<?, ?> entry = (Map.Entry<?, ?>) entryObj;
                        processFields(entry.getValue(), encrypt);
                    }
                }

            } catch (IllegalAccessException e) {
                throw new RuntimeException("Error processing encryption", e);
            }
        }
    }

    @Override
    public Order onAfterConvert(Order order, Document document, String collection) {
        processFields(order, false);  // encrypt before saving
        return order;
    }

    @Override
    public Order onBeforeConvert(Order order, String collection) {
        processFields(order, true);  // encrypt before saving
        return order;
    }

    /**
     * Add hash columns (e.g., email_hash) *into the Document* that will be stored.
     * At this point, the Document contains encrypted values (because we encrypted in onBeforeConvert).
     * We decrypt to recover plaintext, compute hash, and write "<field>_hash" (or custom) into the Document.
     */
    @Override
    public Order onBeforeSave(Order order, Document document, String collection) {
        // Build a map of entity "paths" → hash field names for all @EncryptedField(hash=true)
        Map<String, String> pathsToHashField = new LinkedHashMap<>();
        collectHashablePaths(order, "", pathsToHashField);

        // For each path, read encrypted value from Document, decrypt, compute hash, and write sibling "<hashField>"
        for (Map.Entry<String, String> e : pathsToHashField.entrySet()) {
            String path = e.getKey();
            String hashFieldName = e.getValue();

            // Resolve the nested Document and field name
            PathResolution res = resolveParentAndLeaf(document, path);
            if (res == null || !(res.parent instanceof Document parentDoc)) continue;

            Object encryptedVal = parentDoc.get(res.leaf);
            if (encryptedVal instanceof String encText) {
                try {
                    String plaintext = encryptionUtil.decrypt(encText);
                    // String hash = HashUtil.hmacSha256Hex(HMAC_KEY, plaintext); // (keyed)
                    String hash = HashUtil.sha256Hex(plaintext);                  // (plain SHA-256)
                    parentDoc.put(hashFieldName, hash);
                } catch (Exception ex) {
                    // If decrypt fails (e.g., already plaintext), fall back to hashing raw value
                    String hash = HashUtil.sha256Hex(encText);
                    parentDoc.put(hashFieldName, hash);
                }
            }
        }
        return order;
    }

    /**
     * Walk the entity to find @EncryptedField(hash=true) and record their JSON paths and target hash field names.
     */
    private void collectHashablePaths(Object entity, String basePath, Map<String, String> out) {
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

                // Recurse into nested structures to discover deeper @EncryptedField(hash=true)
                if (!field.getType().isPrimitive()
                        && !field.getType().getName().startsWith("java.")) {
                    collectHashablePaths(value, path, out);
                } else if (value instanceof Iterable<?> it) {
                    int idx = 0;
                    for (Object item : it) {
                        // For lists, we can’t precompute position; hashes are usually on scalar fields,
                        // so we skip list indexing; adjust if you have lists of Users.
                        collectHashablePaths(item, path /* maybe path+"["+idx+"]" */, out);
                        idx++;
                    }
                } else if (value != null && value.getClass().isArray()) {
                    int n = Array.getLength(value);
                    for (int i = 0; i < n; i++) {
                        collectHashablePaths(Array.get(value, i), path, out);
                    }
                } else if (value instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> me : map.entrySet()) {
                        collectHashablePaths(me.getValue(), path + "." + String.valueOf(me.getKey()), out);
                    }
                }
            } catch (IllegalAccessException e) {
                throw new RuntimeException("collectHashablePaths error", e);
            }
        }
    }

    /**
     * Resolve a dotted path to the parent Document and leaf key inside a root Document
     */
    private PathResolution resolveParentAndLeaf(Document root, String dottedPath) {
        String[] parts = dottedPath.split("\\.");
        Document parent = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = parent.get(parts[i]);
            if (!(next instanceof Document)) return null;
            parent = (Document) next;
        }
        return new PathResolution(parent, parts[parts.length - 1]);
    }

    private record PathResolution(Object parent, String leaf) {
    }


}

