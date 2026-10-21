package com.cadence.streaming_service.integration;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MySQLContainer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static {
        MYSQL.start();
    }

    @DynamicPropertySource
    static void cloudFrontKey(DynamicPropertyRegistry registry) {
        registry.add("cloud.aws.cloudfront.private-key-path", BaseIntegrationTest::generatePrivateKeyFile);
    }

    private static String generatePrivateKeyFile() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(2048);
            String body = Base64.getMimeEncoder().encodeToString(generator.generateKeyPair().getPrivate().getEncoded());
            Path keyFile = Files.createTempFile("cloudfront-test-key", ".pem");
            keyFile.toFile().deleteOnExit();
            Files.writeString(keyFile, "-----BEGIN PRIVATE KEY-----\n" + body + "\n-----END PRIVATE KEY-----\n");
            return keyFile.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
