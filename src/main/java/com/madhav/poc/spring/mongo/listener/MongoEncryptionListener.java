package com.madhav.poc.spring.mongo.listener;

import com.madhav.poc.spring.mongo.model.Order;
import com.madhav.poc.spring.mongo.util.EncryptionReflectionUtils;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
import com.madhav.poc.spring.mongo.util.HashUtil;
import com.madhav.poc.spring.mongo.util.Encrypted;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.AfterConvertCallback;
import org.springframework.data.mongodb.core.mapping.event.BeforeConvertCallback;
import org.springframework.data.mongodb.core.mapping.event.BeforeSaveCallback;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MongoEncryptionListener implements
        BeforeConvertCallback<Order>,
        AfterConvertCallback<Order>,
        BeforeSaveCallback<Order> {

    private final EncryptionUtil encryptionUtil;

    @Override
    public Order onBeforeConvert(Order order, String collection) {
        // Encrypt annotated fields in the entity graph, idempotently
        EncryptionReflectionUtils.processEntity(order, encryptionUtil, true);
        return order;
    }

    @Override
    public Order onAfterConvert(Order order, Document document, String collection) {
        // Decrypt annotated fields in the entity graph, idempotently
        EncryptionReflectionUtils.processEntity(order, encryptionUtil, false);
        return order;
    }

    @Override
    public Order onBeforeSave(Order order, Document document, String collection) {
        // Build unique set of "<dottedPath> -> <hashFieldName>" from entity shape
        Map<String, String> pathsToHashField = new LinkedHashMap<>();
        EncryptionReflectionUtils.collectHashablePaths(order, "", pathsToHashField);

        // For each path, traverse the BSON Document and add/update sibling hash fields,
        // handling nested Documents, Lists/arrays, and Maps.
        for (Map.Entry<String, String> e : pathsToHashField.entrySet()) {
            EncryptionReflectionUtils.resolveAndHash(
                    document,
                    e.getKey(),
                    e.getValue(),
                    encryptionUtil,
                    HashUtil::sha256Hex
            );
        }
        return order;
    }
}
