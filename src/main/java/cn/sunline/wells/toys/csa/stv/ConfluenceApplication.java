package cn.sunline.wells.toys.csa.stv;

import org.apache.commons.io.FileUtils;
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
		String passwd = "123-123-123-123";
		String confluenceurl = "http://www.heartie.cn";
		
		//断点续爬
		String continueSpaceid = null;
		String continuePageid = null;
		File checkpoint = new File("checkpoint");
		if (checkpoint.exists()) {
			log.info("Checkpoint found.");
			String content = FileUtils.readFileToString(checkpoint, "utf8");
			String[] ids = content.split(",");
			continueSpaceid = ids[0];
			continuePageid = ids[1];
		}
		
		//登录Confluence
		ConfluenceSpaceArchiver csaSpaces = new ConfluenceSpaceArchiver(username, passwd, confluenceurl);
		csaSpaces.login();
		
		//从空间列表页面获构建根
		List<ConfluencePage> spacelist = csaSpaces.getSpaceList();
		log.info("Total spaces: {}", spacelist.size());
		int total = spacelist.size();
		
		//以空间为单位组织下载过程（其实可以一次下载多个空间，因为单线程版本太慢了，容易超时，所以改成以空间为单位，每个空间new archiver使用独立的连接)
		boolean skipSpace = true;
		if (continueSpaceid == null) {
			skipSpace = false;
		}
		int i = 1;
		for (ConfluencePage ispace : spacelist) {
			log.info("----------------------------------------------------------------------");
			log.info("Download space: {} {}/{}", ispace.name, i, total);
			i++;
			
			//断点续爬，颗粒度：空间
			if (ispace.getId().equals(continueSpaceid)) {
				skipSpace = false;
			}
			if (skipSpace) {
				continue;
			}
			
			//登录Confluence
			ConfluenceSpaceArchiver confluenceSpaceArchiver = new ConfluenceSpaceArchiver(username, passwd, confluenceurl);
			confluenceSpaceArchiver.login();
			
			//待下载的树结构
			ConfluencePage confluencePageTree = new ConfluencePage("根", "0", "");
			confluencePageTree.getChildrens().add(ispace);
			
			//递归获取页面树结构
			confluenceSpaceArchiver.recursiveChildrenSubPages(confluencePageTree);
			
			//整理页面下载队列
			List<ConfluencePage> confluencePages = confluenceSpaceArchiver.recursiveTree2Queue(confluencePageTree);
			
			//下载页面内容，保存到本地目录
			boolean skipPage = true;
			if (continuePageid == null) {
				skipPage = false;
			}
			for (ConfluencePage ipage : confluencePages) {
				//断点续爬，颗粒度：页面
				if (ipage.getId().equals(continuePageid)) {
					//最后一个页面会重爬
					skipPage = false;
				}
				if (skipPage) {
					continue;
				}
				
				//下载页面
				log.info("Archiving {}", ipage);
				confluenceSpaceArchiver.downloadPageHtml(ipage);
				confluenceSpaceArchiver.downloadPageAttachment(ipage);
				
				//保存checkpoint，给断点续爬用
				FileUtils.writeStringToFile(new File("checkpoint"), ispace.getId() + "," + ipage.getId(), "utf8");
			}
			
			//保存个索引页面作为总的入口，后写索引可以避免续爬时出现多条
			confluenceSpaceArchiver.saveIndex(confluenceSpaceArchiver.ROOT_LOCAL_PATH + File.separator + "index.html", confluencePageTree);
			
		}
		
	}
}
