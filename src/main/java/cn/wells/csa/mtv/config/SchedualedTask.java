package cn.wells.csa.mtv.config;

import cn.wells.csa.mtv.dto.DTO_Page;
import cn.wells.csa.mtv.repository.DAO_Page;
import cn.wells.csa.mtv.service.CSA_Context;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

@EnableScheduling
@Configuration
@Slf4j
public class SchedualedTask {
	
	@Autowired
	DAO_Page dao_page;
	
	@Autowired
	CSA_Context csa_context;
	
	@Scheduled(cron = "0/10 * * * * ?")
	private void configureTasks() {
		//初始化上下文
		this.csa_context.init();
		
		//从就绪任务中拉取，提交异步线程执行
		List<DTO_Page> readyTasks = this.dao_page.getReadyTasks();
		for (DTO_Page itask : readyTasks) {
			if (itask.getRetry() <= 0) {
				continue;
			}
			
			//扣减retry次数
			itask.setRetry(itask.getRetry() - 1);
			//改为COMMIT状态
			itask.setStatus(EnumCSATaskStatus.COMMIT);
			//保存状态
			this.dao_page.save(itask);
			
			//提交任务
			this.csa_context.asProcPage(itask);
		}
		
		log.info("Commit tasks: {}", readyTasks.size());
	}
	
}

