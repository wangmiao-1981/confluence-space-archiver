package cn.sunline.wells.toys.csa.mtv.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping(value = "/csa")
public class CSAController {
	
	@PostMapping(value = "/start")
	public String lockService() {
		
		log.debug("接收到start");
		
		return "ok";
		
	}
}
