package com.madhav.poc.spring.mongo.listener;


import com.madhav.poc.spring.mongo.model.Order;
import com.madhav.poc.spring.mongo.util.Encrypted;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
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
public class MongoEncryptionListener implements BeforeConvertCallback<Order>, AfterConvertCallback<Order> {

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
}

