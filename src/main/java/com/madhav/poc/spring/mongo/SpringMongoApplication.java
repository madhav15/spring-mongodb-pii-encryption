package com.madhav.poc.spring.mongo;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.madhav.poc.spring.mongo.model.*;
import com.madhav.poc.spring.mongo.services.OrderService;
import com.madhav.poc.spring.mongo.util.HashUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.data.mongodb.util.encryption.EncryptionUtils;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@SpringBootApplication
public class SpringMongoApplication implements CommandLineRunner {

	@Autowired
	private OrderService orderService;

	public static void main(String[] args) {
		SpringApplication.run(SpringMongoApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		String randomUuid = UUID.randomUUID().toString();
		String mail = "mk" + randomUuid.replaceAll("-", "") + "@test.com";
		User user = new User();
		user.setName("Madhav");
		user.setMobileNumber("9999999999");
		user.setEmail(mail);
		user.setBankAccountNumber("1234567890");


		Lead lead = new Lead();
		lead.setUser(user);

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
	}
}
