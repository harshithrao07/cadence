package com.cadence.playlist_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableFeignClients
public class PlaylistServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(PlaylistServiceApplication.class, args);
	}

}
