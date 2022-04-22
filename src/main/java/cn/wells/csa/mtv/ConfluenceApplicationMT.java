package cn.wells.csa.mtv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

@Slf4j
@EnableAsync
@ConfigurationPropertiesScan
@SpringBootApplication
public class ConfluenceApplicationMT {
	
	public static void main(String[] args) {
		
		SpringApplication.run(ConfluenceApplicationMT.class, args);
	}
}
