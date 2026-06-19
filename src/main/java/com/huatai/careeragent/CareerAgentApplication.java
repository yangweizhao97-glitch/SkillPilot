package com.huatai.careeragent;

import com.huatai.careeragent.file.FileStorageProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties(FileStorageProperties.class)
public class CareerAgentApplication {

	public static void main(String[] args) {
		SpringApplication.run(CareerAgentApplication.class, args);
	}

}
