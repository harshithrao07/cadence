package com.cadence.playlist_service.integration;

import com.cadence.playlist_service.client.CatalogPreviewClient;
import com.cadence.playlist_service.client.UserPreviewClient;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.MySQLContainer;

@ActiveProfiles("test")
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
public abstract class BaseIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.36");

    static {
        MYSQL.start();
    }

    @MockBean
    UserPreviewClient userPreviewClient;

    @MockBean
    CatalogPreviewClient catalogPreviewClient;
}
