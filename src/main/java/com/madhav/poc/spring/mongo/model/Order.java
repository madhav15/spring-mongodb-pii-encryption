package com.madhav.poc.spring.mongo.model;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;


@Data
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Document(collection = "star-group-order")  // 👈 this maps to your collection
public class Order {

    @Id
    private String id;

    private String orderId;

    private Agent agent;

}
