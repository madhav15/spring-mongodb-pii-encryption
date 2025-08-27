package com.madhav.poc.spring.mongo.listener;

import com.madhav.poc.spring.mongo.util.Encrypted;
import com.madhav.poc.spring.mongo.util.EncryptionReflectionUtils;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
import com.madhav.poc.spring.mongo.util.HashUtil;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.*;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MongoEncryptionListener implements
        BeforeConvertCallback<Object>,
        AfterConvertCallback<Object>,
        BeforeSaveCallback<Object> {

    private final EncryptionUtil encryptionUtil;

    // Cache whether a class has @Encrypted fields (direct or nested)
    private static final Map<Class<?>, Boolean> ENCRYPTED_CLASS_CACHE = new ConcurrentHashMap<>();

    public MongoEncryptionListener(EncryptionUtil encryptionUtil) {
        this.encryptionUtil = encryptionUtil;
    }

    @Override
    public Object onBeforeConvert(Object entity, String collection) {
        if (!hasEncryptedFieldsCached(entity)) return entity;
        EncryptionReflectionUtils.processEntity(entity, encryptionUtil, true);
        return entity;
    }

    @Override
    public Object onAfterConvert(Object entity, Document document, String collection) {
        if (!hasEncryptedFieldsCached(entity)) return entity;
        EncryptionReflectionUtils.processEntity(entity, encryptionUtil, false);
        return entity;
    }

    @Override
    public Object onBeforeSave(Object entity, Document document, String collection) {
        if (!hasEncryptedFieldsCached(entity)) return entity;

        Map<String, String> hashablePaths = new HashMap<>();
        EncryptionReflectionUtils.collectHashablePaths(entity, "", hashablePaths);

        if (!hashablePaths.isEmpty()) {
            for (Map.Entry<String, String> e : hashablePaths.entrySet()) {
                EncryptionReflectionUtils.resolveAndHash(
                        document,
                        e.getKey(),
                        e.getValue(),
                        encryptionUtil,
                        HashUtil::sha256Hex
                );
            }
        }

        return entity;
    }

    /**
     * Cached check for @Encrypted fields (direct or nested).
     */
    private boolean hasEncryptedFieldsCached(Object entity) {
        if (entity == null) return false;
        return ENCRYPTED_CLASS_CACHE.computeIfAbsent(entity.getClass(), this::hasEncryptedFieldsRecursiveClass);
    }

    /**
     * Recursive scan of a class and its nested fields to detect @Encrypted
     */
    private boolean hasEncryptedFieldsRecursiveClass(Class<?> clazz) {
        try {
            for (Field field : clazz.getDeclaredFields()) {
                field.setAccessible(true);

                // Direct annotation
                if (field.isAnnotationPresent(Encrypted.class)) {
                    return true;
                }

                // Nested types (skip JDK classes & primitives)
                if (!field.getType().isPrimitive() && !field.getType().getName().startsWith("java.")) {
                    if (hasEncryptedFieldsRecursiveClass(field.getType())) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }
}
