package com.example.survey.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Data
@Document("users")
public class User {

    @Id
    private String id;

    /** 登录名，唯一 */
    @Indexed(unique = true)
    private String username;

    /** BCrypt 加密后的密码 */
    private String passwordHash;

    private Instant createdAt = Instant.now();
}
