package com.madhav.poc.spring.mongo.listener;

import com.madhav.poc.spring.mongo.model.Order;
import com.madhav.poc.spring.mongo.util.EncryptionReflectionUtils;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
import com.madhav.poc.spring.mongo.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.mongodb.core.mapping.event.*;
import org.springframework.stereotype.Component;

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
        // Encrypt all annotated fields before converting
        EncryptionReflectionUtils.processEntity(order, encryptionUtil, true);
        return order;
    }

    @Override
    public Order onAfterConvert(Order order, Document document, String collection) {
        // Decrypt all annotated fields after fetching
        EncryptionReflectionUtils.processEntity(order, encryptionUtil, false);
        return order;
    }

    @Override
    public Order onBeforeSave(Order order, Document document, String collection) {
        // Collect hashable paths from @Encrypted(hash = true)
        Map<String, String> pathsToHashField = new LinkedHashMap<>();
        EncryptionReflectionUtils.collectHashablePaths(order, "", pathsToHashField);

        // For each path, resolve value in document, decrypt, compute hash, and write "<field>_hash"
        for (Map.Entry<String, String> e : pathsToHashField.entrySet()) {
            String path = e.getKey();
            String hashFieldName = e.getValue();

            EncryptionReflectionUtils.resolveAndHash(document, path, hashFieldName, encryptionUtil, HashUtil::sha256Hex);
        }
        return order;
    }
}
