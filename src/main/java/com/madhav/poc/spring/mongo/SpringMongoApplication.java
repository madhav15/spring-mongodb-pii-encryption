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
public class SpringMongoApplication {

	public static void main(String[] args) {
		SpringApplication.run(SpringMongoApplication.class, args);
	}

}
