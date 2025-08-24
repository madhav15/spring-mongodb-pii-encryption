package com.madhav.poc.spring.mongo.util;


import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Encrypted {

    /** Also emit a deterministic hash column alongside (e.g., "email_hash") */
    boolean hash() default false;

    /** Optional custom hash field name; if blank, uses "<fieldName>_hash" */
    String hashFieldName() default "";
}
