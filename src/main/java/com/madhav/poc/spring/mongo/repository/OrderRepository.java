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

    @Query("{ 'agent.lead.user.mobile_hash': ?0 }")
    Optional<Order> findByMobileHash(String mobileHash);

    @Query("{ 'agent.lead.user.email_hash': ?0 }")
    Optional<Order> findByEmailHash(String emailHash);

    // Search by email (compare against emailHash)
    Optional<Order> findByAgent_Lead_User_EmailHash(String emailHash);

    // Search by mobile
    Optional<Order> findByAgent_Lead_User_MobileNumberHash(String mobileHash);

    // Search by combination
    Optional<Order> findByAgent_Lead_User_NameAndAgent_Lead_User_EmailHash(String name, String emailHash);

    Optional<Order> findByAgent_Lead_User_NameAndAgent_Lead_User_EmailHashAndAgent_Lead_User_MobileNumberHash(
            String name, String emailHash, String mobileHash
    );

    @Query("{ 'agent.agentCode': ?0, 'agent.lead.user.name': ?1 }")
    List<Order> findOrdersByAgentCodeAndUserName(String agentCode, String nameHash);
}
