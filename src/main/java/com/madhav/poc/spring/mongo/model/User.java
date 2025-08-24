package com.madhav.poc.spring.mongo.model;


import com.madhav.poc.spring.mongo.util.Encrypted;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
public class User {

    private String name;

    @Encrypted
    private String mobileNumber;

    @Encrypted
    private String email;

    @Encrypted
    private String bankAccountNumber;

    private String address;


}
