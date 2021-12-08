package cn.sunline.wells.toys.csa.stv;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
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
		String username = "wangmiao";
		String passwd = "a84-123-321-rtD";
		String confluenceurl = "http://www.heartie.cn";
		
		//登录Confluence
		ConfluenceSpaceArchiver csaSpaces = new ConfluenceSpaceArchiver(username, passwd, confluenceurl);
		csaSpaces.login();
		
		//从空间列表页面获构建根
		List<ConfluencePage> spacelist = csaSpaces.getSpaceList();
		log.info("Total spaces: {}", spacelist.size());
		int total = spacelist.size();
		int i = 1;
		
		//以空间为单位组织下载过程
		for (ConfluencePage ispace : spacelist) {
			log.info("----------------------------------------------------------------------");
			log.info("Download space: {} {}/{}", ispace.name, i, total);
			i++;
			
			//登录Confluence
			ConfluenceSpaceArchiver confluenceSpaceArchiver = new ConfluenceSpaceArchiver(username, passwd, confluenceurl);
			confluenceSpaceArchiver.login();
			
			//待下载的树（其实可以一次下载多个空间，因为单线程版本太慢了存在超时的可能，所以改成以空间为单位)
			ConfluencePage confluencePageTree = new ConfluencePage("根", "0", "");
			confluencePageTree.getChildrens().add(ispace);
			
			//保存个索引页面作为总的入口
			confluenceSpaceArchiver.saveIndex(confluenceSpaceArchiver.ROOT_LOCAL_PATH + File.separator + "index-" + ispace.getName() + ".html", confluencePageTree);
			
			//递归获取页面树结构
			confluenceSpaceArchiver.recursiveChildrenSubPages(confluencePageTree);
			
			//整理页面下载队列
			List<ConfluencePage> confluencePages = confluenceSpaceArchiver.recursiveTree2Queue(confluencePageTree);
			
			//下载页面内容，保存到本地目录
			for (ConfluencePage confluencePage : confluencePages) {
				log.info("Archiving {}", confluencePage);
				confluenceSpaceArchiver.downloadPageHtml(confluencePage);
				confluenceSpaceArchiver.downloadPageAttachment(confluencePage);
			}
		}
		
	}
}
