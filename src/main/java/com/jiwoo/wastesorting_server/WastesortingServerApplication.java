package com.jiwoo.wastesorting_server;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling // 오탐 사진 보관 기간 정리 (매일 새벽)
public class WastesortingServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WastesortingServerApplication.class, args);
	}

}
