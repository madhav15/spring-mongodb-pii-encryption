package com.madhav.poc.spring.mongo.repository;

import com.madhav.poc.spring.mongo.model.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;


@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    @Query("{ 'agent.lead.user.email': ?0 }")
    List<Order> findByEncryptedEmail(String encryptedEmail);
}
