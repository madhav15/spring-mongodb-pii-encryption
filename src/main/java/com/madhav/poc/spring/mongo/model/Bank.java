package com.madhav.poc.spring.mongo.model;

import com.madhav.poc.spring.mongo.util.Encrypted;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@AllArgsConstructor
@NoArgsConstructor
@ToString
public class Bank {

    private String bankName;

    private String ifscCode;

    @Encrypted(hash = true, hashFieldName = "phoneHash")
    private String phone;

    private String phoneHash;
}
