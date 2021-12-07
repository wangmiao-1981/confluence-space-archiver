package cn.sunline.wells.toys;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.List;

/**
 * 归档conflunce空间到本地
 *
 * @author wangmiao
 * @date 2021-12-3
 */
public class ConfluenceApplication {
	private static final Logger log = LoggerFactory.getLogger(ConfluenceApplication.class);
	
	public static void main(String[] args) throws IOException {
		
		ConfluenceSpaceArchiver confluenceSpaceArchiver = new ConfluenceSpaceArchiver("wangmiao" , "12345678" , "http://www.heatie.cn:8090");
		
		//登录
		confluenceSpaceArchiver.login();
		
		//从空间列表页面获构建根
		ConfluencePage confluencePageTree = new ConfluencePage("根" , "0" , "");
		//		confluencePageTree.childrens.add(new ConfluencePage("测试页面" , "75011828" , confluenceSpaceArchiver.VIEW_PAGE + "75011828"));
		List<ConfluencePage> spacelist = confluenceSpaceArchiver.getSpaceList();
		confluencePageTree.getChildrens().addAll(spacelist);
		
		//递归获取页面树结构
		confluenceSpaceArchiver.recursiveChildrenSubPages(confluencePageTree);
		
		//整理页面下载队列
		List<ConfluencePage> confluencePages = confluenceSpaceArchiver.recursiveTree2Queue(confluencePageTree);
		
		//下载页面内容，保存到本地目录
		for (ConfluencePage confluencePage : confluencePages) {
			log.info("Archiving {}" , confluencePage);
			confluenceSpaceArchiver.downloadPageHtml(confluencePage);
			confluenceSpaceArchiver.downloadPageAttachment(confluencePage);
		}
		
	}
}
