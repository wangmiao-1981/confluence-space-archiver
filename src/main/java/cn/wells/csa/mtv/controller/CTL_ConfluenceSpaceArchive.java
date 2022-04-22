package cn.wells.csa.mtv.controller;

import cn.wells.csa.mtv.config.EnumCSATaskStatus;
import cn.wells.csa.mtv.dto.DTO_Page;
import cn.wells.csa.mtv.repository.DAO_Page;
import cn.wells.csa.mtv.service.CSA_Context;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.io.IOException;
import java.util.List;

@Slf4j
@RestController
@RequestMapping(value = "/csa")
public class CTL_ConfluenceSpaceArchive {
	
	@Autowired
	CSA_Context csa_context;
	
	@Autowired
	DAO_Page dao_page;
	
	/**
	 * 初始化并启动
	 *
	 * @return
	 */
	@PostMapping(value = "/initStart")
	public String initStart() throws IOException {
		//初始化上下文
		this.csa_context.init();
		
		//获取空间列表
		List<DTO_Page> spaceList = this.csa_context.getSpaceList();
		
		//保存进待处理就绪状态
		for (int i = 0; i < spaceList.size(); i++) {
			DTO_Page ipage = spaceList.get(i);
			//添加的都是READY，手工commit
			ipage.setStatus(EnumCSATaskStatus.READY);
			//默认重试次数
			ipage.setRetry(3);
			
			log.debug("Save page {}", ipage.getId());
			this.dao_page.save(ipage);
		}
		
		log.info("Total fetch {} spaces.", spaceList.size());
		
		return "ok";
		
	}
	
	/**
	 * 保存空间列表到index.html
	 *
	 * @return
	 */
	@PostMapping(value = "/saveIndex")
	public String saveIndex() throws IOException {
		//初始化上下文
		this.csa_context.init();
		
		//获取空间列表
		List<DTO_Page> spaceList = this.csa_context.getSpaceList();
		
		//保存索引页
		File pageIndex = new File(this.csa_context.getROOT_LOCAL_PATH() + File.separator + "page-ROOT" + File.separator + "index.html");
		FileUtils.writeStringToFile(pageIndex, "Total " + spaceList.size() + " spaces</br>", "utf8", false);
		for (DTO_Page ispace : spaceList) {
			String aAnchor = String.format("<a href='../page-%s/index.html'>%s</a></br>", ispace.getId(), ispace.getName());
			FileUtils.writeStringToFile(pageIndex, aAnchor, "utf8", true);
		}
		log.info("Index page saved, total {} spaces.", spaceList.size());
		
		return "ok";
	}
}
