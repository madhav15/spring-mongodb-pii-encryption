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

    @Encrypted(hash = true, hashFieldName = "mobileNumberHash")
    private String mobileNumber;

    private String mobileNumberHash;

    @Encrypted(hash = true, hashFieldName = "emailHash")
    private String email;

    private String emailHash;

    @Encrypted
    private String bankAccountNumber;

    private String address;


}
