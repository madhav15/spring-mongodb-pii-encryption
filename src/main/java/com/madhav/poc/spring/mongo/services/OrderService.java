package com.madhav.poc.spring.mongo.services;


import com.madhav.poc.spring.mongo.model.Order;
import com.madhav.poc.spring.mongo.repository.OrderRepository;
import com.madhav.poc.spring.mongo.util.EncryptionUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository repo;
    private final EncryptionUtil encryptionUtil;


    public Order save(Order order) {
        return repo.save(order); // Will encrypt automatically
    }

    public Optional<Order> getById(String id) {
        return repo.findById(id); // Will decrypt automatically
    }

    public List<Order> getByEmail(String email) {
        String encrypted = encryptionUtil.encrypt(email);
        return repo.findByEncryptedEmail(encrypted);
    }

    public Optional<Order> findByAgentLeadUserEmailHash(String emailHash) {
        return repo.findByAgent_Lead_User_EmailHash(emailHash);
    }
}
