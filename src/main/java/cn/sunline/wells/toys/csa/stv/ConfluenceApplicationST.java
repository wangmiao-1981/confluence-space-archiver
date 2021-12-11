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
public class ConfluenceApplicationST {
	private static final Logger log = LoggerFactory.getLogger(ConfluenceApplicationST.class);
	
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
			if (ids.length > 1) {
				continuePageid = ids[1];
			}
		}
		
		//登录Confluence
		ConfluenceSpaceArchiverST csaSpaces = new ConfluenceSpaceArchiverST(username, passwd, confluenceurl);
		csaSpaces.login();
		
		//从空间列表页面获构建根
		List<ConfluencePageST> spacelist = csaSpaces.getSpaceList();
		log.info("Total spaces: {}", spacelist.size());
		int total = spacelist.size();
		
		//以空间为单位组织下载过程（其实可以一次下载多个空间，因为单线程版本太慢了，容易超时，所以改成以空间为单位，每个空间new archiver使用独立的连接)
		boolean skipSpace = true;
		boolean skipPage = true;
		if (continueSpaceid == null) {
			skipSpace = false;
		}
		if (continuePageid == null) {
			skipPage = false;
		}
		int i = 1;
		for (ConfluencePageST ispace : spacelist) {
			log.info("----------------------------------------------------------------------");
			log.info("Download space: {} {} {}/{}", ispace.getName(), ispace.getId(), i, total);
			log.info("----------------------------------------------------------------------");
			i++;
			
			//断点续爬，颗粒度：空间
			if (ispace.getId().equals(continueSpaceid)) {
				skipSpace = false;
			}
			if (skipSpace) {
				continue;
			}
			
			//登录Confluence
			ConfluenceSpaceArchiverST confluenceSpaceArchiver = new ConfluenceSpaceArchiverST(username, passwd, confluenceurl);
			confluenceSpaceArchiver.login();
			
			//待下载的树结构
			ConfluencePageST confluencePageTree = new ConfluencePageST("根", "0", "");
			confluencePageTree.getChildrens().add(ispace);
			
			//递归获取页面树结构
			confluenceSpaceArchiver.recursiveChildrenSubPages(confluencePageTree);
			
			//整理页面下载队列
			List<ConfluencePageST> confluencePages = confluenceSpaceArchiver.recursiveTree2Queue(confluencePageTree);
			log.info("Total pages: {}", confluencePages.size());
			
			//下载页面内容，保存到本地目录
			for (ConfluencePageST ipage : confluencePages) {
				//断点续爬，颗粒度：页面
				if (skipPage) {
					if (ipage.getId().equals(continuePageid)) {
						skipPage = false;
					}
					log.info("Skipping page {} {} {}", ipage.getName(), ipage.getId(), ipage.getUrl());
					continue;
				}
				
				//查缺补漏专用，发现有的页面没下载，可能是迭代过程中出的花样，回头重下一遍验证看看
				if (false) {
					File pageIndex = new File(confluenceSpaceArchiver.ROOT_LOCAL_PATH + File.separator + "page-" + ipage.getId() + File.separator + "index.html");
					if (pageIndex.exists()) {
						log.info("Skipping page {} {} {}", ipage.getName(), ipage.getId(), ipage.getUrl());
						continue;
					}
				}
				
				//下载页面
				log.info("Archiving {} {} {}", ipage.getName(), ipage.getId(), ipage.getUrl());
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
