package com.madhav.poc.spring.mongo;

import com.madhav.poc.spring.mongo.model.*;
import com.madhav.poc.spring.mongo.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class SpringMongoApplication implements CommandLineRunner {

	@Autowired
	private OrderService orderService;

	public static void main(String[] args) {
		SpringApplication.run(SpringMongoApplication.class, args);
	}

	@Override
	public void run(String... args) throws Exception {
		User user = new User();
		user.setName("Madhav");
		user.setMobileNumber("9999999999");
		user.setEmail("test@example.com");
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

		System.out.println("Finding by email");
		List<Order> finderByMail = orderService.getByEmail("test@example.com");
		finderByMail.forEach(System.out::println);

		System.currentTimeMillis();System.nanoTime()
	}
}
