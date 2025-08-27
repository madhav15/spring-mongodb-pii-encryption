package com.madhav.poc.spring.mongo.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.madhav.poc.spring.mongo.model.*;
import com.madhav.poc.spring.mongo.services.OrderService;
import com.madhav.poc.spring.mongo.util.HashUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/order")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public String create() {

        String randomUuid = UUID.randomUUID().toString();
        String mail = "mk" + randomUuid.replaceAll("-", "") + "@test.com";
        User user = new User();
        user.setName("Madhav");
        user.setMobileNumber("9999999999");
        user.setEmail(mail);
        user.setBankAccountNumber("1234567890");

        Bank b1 = new Bank("IDFC", "IF1", "999977777","");

        Bank b2 = new Bank("IDFC", "IF1", "986876875","");

        Lead lead = new Lead();
        lead.setUser(user);
        lead.setBankList(List.of(b1, b2));

        Agent agent = new Agent();
        agent.setLead(lead);

        Order order = new Order();
        order.setAgent(agent);

        // Save
        Order saved = orderService.save(order);

        System.out.println("Saved ID: " + saved.getId());

        // Fetch
        orderService.getById(saved.getId()).ifPresent(o -> {
            System.out.println("Decrypted Email: " + o.getAgent().getLead().getUser().getEmail());
        });

        System.out.println("*********************** Finding by email *********************** ");
        List<Order> orderList = orderService.getByEmail(mail);
        orderList.forEach(System.out::println);

        System.out.println("***********************  Now verifying with Hashed Email *********************** ");
        String emailHash = HashUtil.sha256Hex(mail);
        System.out.println("Generated Hash is " + emailHash);
        Optional<Order> optionalOrder = orderService.findByAgentLeadUserEmailHash(emailHash);
        optionalOrder.ifPresent( o -> {
            try {
                System.out.println(new ObjectMapper().writeValueAsString(o));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }
        });

        return "success";
    }

    @GetMapping
    public void update() {
        orderService.update();
    }
}
