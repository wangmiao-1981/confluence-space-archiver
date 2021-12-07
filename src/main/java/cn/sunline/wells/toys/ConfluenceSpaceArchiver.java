package cn.sunline.wells.toys;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.util.StreamUtils;

import java.io.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class ConfluenceSpaceArchiver {
	// 地址常量
	public String ROOT_LOCAL_PATH = "/Users/mmao/mmVolume/Documents/work-长亮科技/DEP-解决方案&产品管理部/UUU-知识库Confluence";
	public String ROOT_CONFLUENCE;
	public String PAGES_VIEWPAGE_ACTION_PAGE_ID = "/pages/viewpage.action?pageId=";
	public String PAGES_VIEWINFO_ACTION_PAGE_ID = "/pages/viewinfo.action?pageId=";
	public String PAGES_VIEWATTACHMENT_ACTION_PAGE_ID = "/pages/viewpageattachments.action?pageId=";
	public String VIEW_PAGE = ROOT_CONFLUENCE + PAGES_VIEWPAGE_ACTION_PAGE_ID;
	public String VIEW_INFO = ROOT_CONFLUENCE + PAGES_VIEWINFO_ACTION_PAGE_ID;
	public String VIEW_ATTACHMENT = ROOT_CONFLUENCE + PAGES_VIEWATTACHMENT_ACTION_PAGE_ID;
	public String VIEW_SPACE_LIST = ROOT_CONFLUENCE + "/spacedirectory/view.action?os_username=" + this.username + "&os_password=" + this.password + "&login=%E7%99%BB%E5%BD%95&os_destination=";
	public String DO_LOGIN_PARAM;
	// 用户名
	private String username;
	private String password;
	// 其他
	private ThreadLocal<String> tlSessionCookie = new ThreadLocal<>();
	private CloseableHttpClient httpclient = HttpClients.createDefault();
	
	/**
	 * 空间的用户名、密码、根URL
	 *
	 * @param username
	 * @param password
	 */
	public ConfluenceSpaceArchiver(String username , String password , String rootUrl) {
		this.username = username;
		this.password = password;
		this.ROOT_CONFLUENCE = rootUrl;
		this.DO_LOGIN_PARAM = "/dologin.action?os_username=" + this.username + "&os_password=" + this.password + "&login=%E7%99%BB%E5%BD%95&os_destination=";
	}
	
	/**
	 * 登录上去，保存session到cookie
	 */
	public void login() {
		//访问首页，未登录，获取所需session内容
		getSession(ROOT_CONFLUENCE , httpclient);
		//尝试登录
		login(ROOT_CONFLUENCE + DO_LOGIN_PARAM , httpclient);
	}
	
	//使用threadLocal来保存线程应该有的cookie用以保持session
	private void getSession(String url , CloseableHttpClient httpclient) {
		HttpGet httpget = getHttpGet(url);
		try (CloseableHttpResponse response = httpclient.execute(httpget)) {
			for (Header obj : response.getAllHeaders()) {
				if ("Set-Cookie".equals(obj.getName())) {
					if (tlSessionCookie.get() == null || "".equals(tlSessionCookie.get())) {
						tlSessionCookie.set(obj.getValue());
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	//获取httpget，包含登录状态
	private HttpGet getHttpGet(String url) {
		HttpGet httpget = new HttpGet(url);
		generateDefaultHttpHeader().entrySet().forEach(e -> httpget.addHeader(e.getKey() , e.getValue()));
		if (tlSessionCookie.get() != null && !"".equals(tlSessionCookie.get())) {
			httpget.addHeader("Cookie" , tlSessionCookie.get());
		}
		//设置超时控制相关时间参数
		httpget.setConfig(RequestConfig.custom() //
				.setConnectionRequestTimeout(10000) //
				.setConnectTimeout(10000) //
				.setSocketTimeout(10000) //
				.build());
		return httpget;
	}
	
	//默认头填充
	private static Map<String, String> generateDefaultHttpHeader() {
		Map<String, String> map = new HashMap<>();
		map.put("accept" , "text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3");
		map.put("accept-language" , "zh-CN,zh;q=0.9");
		map.put("connection" , "keep-alive");
		//这里先不用 map.put("content-length","87");
		map.put("content-type" , "application/x-www-form-urlencoded");
		map.put("user-agent" , "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/77.0.3865.120 Safari/537.36");
		return map;
	}
	
	//登录过程
	private void login(String url , CloseableHttpClient httpclient) {
		HttpGet httpget = getHttpGet(url);
		try (CloseableHttpResponse response = httpclient.execute(httpget)) {
			HttpEntity entity = response.getEntity();
			for (Header obj : response.getAllHeaders()) {
				//System.out.println("url = [" + obj.getName() + "]"+obj.getValue());
				if ("X-AUSERNAME".equals(obj.getName())) {
					if (this.username.equals(obj.getValue())) {
						log.info(this.username + " 登录成功");
					} else {
						log.error(obj.getValue());
						throw new Exception(this.username + " 登录失败");
					}
				}
			}
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 获取页面的下级结构，这里不是递归，就抓一层
	 *
	 * @param pageid
	 * @return
	 */
	private List<ConfluencePage> getSubPages(String pageid) {
		List<ConfluencePage> ret = new ArrayList<ConfluencePage>();
		
		//获取"页面信息"，里面有"子页面"，从这里解析包含的页面
		Document doc = getHtml(this.VIEW_INFO + pageid , httpclient);
		if (doc == null || doc.title().contains("页面未找到")) {
			log.error("Get nothing from url:{}" , this.VIEW_INFO + pageid);
			return ret;
		}
		log.debug("Response doc = " + doc);
		
		//抓子页面
		Elements subPages = doc.select("span:contains(父页面)");
		if (subPages.size() > 0) {
			//那个讨厌的列表上面有个父页面，要排除了
			subPages = doc.select("span:contains(子页面)");
			//有些页面没了子页面
			if (subPages.size() > 0) {
				subPages = subPages.first().parent().select("A:not([onclick])");
			}
		} else {
			//初级页面，没有父页面干扰
			subPages = doc.select("#content .pageInfoTable").get(1).select("A");
		}
		
		//解析pageInfoTable - 取所有子页面
		for (int i = 0; i < subPages.size(); i++) {
			Element iElement = subPages.get(i);
			String name = iElement.text();
			String url = this.ROOT_CONFLUENCE + iElement.attr("href");
			String id = url.substring(url.indexOf("pageId") + 7);
			log.debug("Subpage name: {} , id: {} , url: {}" , name , id , url);
			ConfluencePage subPage = new ConfluencePage(name , id , url);
			ret.add(subPage);
		}
		
		return ret;
	}
	
	//获取页面html，返回Document
	private Document getHtml(String url , CloseableHttpClient httpclient) {
		HttpGet httpget = getHttpGet(url);
		try (CloseableHttpResponse response = httpclient.execute(httpget)) {
			HttpEntity entity = response.getEntity();
			return Jsoup.parse(entity.getContent() , "UTF-8" , ROOT_CONFLUENCE);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return null;
	}
	
	/**
	 * 下载指定文件
	 *
	 * @param attachmentUrl
	 * @param fileName
	 * @param filePatch
	 * @throws Throwable
	 */
	private void downloadAttachment(String attachmentUrl , String fileName , String filePatch) throws Throwable {
		File desc = new File(filePatch + File.separator + fileName);
		File folder = desc.getParentFile();
		if (desc.exists()) {
			return;
		}
		folder.mkdirs();
		HttpGet httpget = getHttpGet(attachmentUrl);
		try (CloseableHttpResponse response = httpclient.execute(httpget)) {
			HttpEntity entity = response.getEntity();
			try (InputStream is = entity.getContent(); //
			     OutputStream os = new FileOutputStream(desc)) {
				StreamUtils.copy(is , os);
			}
			log.info("下载：" + fileName + " 成功");
		} catch (Throwable e) {
			e.printStackTrace();
		}
	}
	
	/**
	 * 递归方式，抓取subpages树型结构
	 *
	 * @param page
	 */
	public void recursiveChildrenSubPages(ConfluencePage page) {
		log.info("Recursive {}" , page);
		for (int i = 0; i < page.childrens.size(); i++) {
			ConfluencePage iPage = page.childrens.get(i);
			//获取附件列表，注意分页
			List<String[]> attachments = getAttachments(iPage.getId());
			if (attachments.size() > 0) {
				log.info("Got attachments cnt:{} from pageid:{}" , attachments.size() , iPage.getId());
				iPage.getAttachments().addAll(attachments);
				for (String[] attachment : attachments) {
					log.debug("attachment:{} from pageid:{}" , attachment , iPage.getId());
				}
			}
			
			//每个子节点都刷一下，如果抓回来的子页面，继续递归
			log.debug("Request subpages of child: name: {} , id: {} , url: {}" , iPage.getName() , iPage.getId() , iPage.getUrl());
			List<ConfluencePage> subPages = getSubPages(iPage.getId());
			if (subPages.size() > 0) {
				log.info("Got subpages cnt:{} from pageid:{}" , subPages.size() , iPage.getId());
				for (ConfluencePage subPage : subPages) {
					log.debug("subpag:{}" , subPage);
				}
				//设置父页面ID
				for (ConfluencePage subPage : subPages) {
					subPage.setParentID(iPage.getId());
				}
				//将抓回来的页面都加到当前面面的children中
				iPage.childrens.addAll(subPages);
				//递归
				recursiveChildrenSubPages(iPage);
			}
		}
	}
	
	/**
	 * 从指定页面获取附件链接
	 *
	 * @param pageid
	 * @return
	 */
	private List<String[]> getAttachments(String pageid) {
		List<String[]> ret = new ArrayList<>();
		
		//获取"附件信息"
		Document doc = getHtml(this.VIEW_ATTACHMENT + pageid , httpclient);
		if (doc == null || doc.title().contains("页面未找到")) {
			log.error("Get nothing from url:{}" , this.VIEW_INFO + pageid);
			return ret;
		}
		log.debug("Response doc = " + doc);
		
		//解析 - 取所有附件
		Elements attachmentsA = doc.select("A.filename:not(:contains(版本))");
		for (int i = 0; i < attachmentsA.size(); i++) {
			Element iElement = attachmentsA.get(i);
			String filename = iElement.text();
			String href = this.ROOT_CONFLUENCE + iElement.attr("href");
			String[] oneAttachment = {filename , href};
			ret.add(oneAttachment);
		}
		
		//如果存在分页，需要解析抓取
		Elements attachmentPageLinks = doc.select("ol.aui-nav A");
		for (Element ilink : attachmentPageLinks) {
			if (ilink.text().contains("上一个")) continue;
			if (ilink.text().contains("下一个")) continue;
			
			//取分页面，解析其中附件
			Document doc1 = getHtml(ilink.attr("abs:href") , httpclient);
			if (doc1 != null) {
				log.debug("Response doc = " + doc);
				//解析 - 取所有附件
				Elements attachmentsB = doc1.select("A.filename:not(:contains(版本))");
				for (int i = 0; i < attachmentsB.size(); i++) {
					Element iElement = attachmentsB.get(i);
					String filename = iElement.text();
					String href = iElement.attr("abs:href");
					String[] oneAttachment = {filename , href};
					ret.add(oneAttachment);
				}
			}
		}
		return ret;
	}
	
	/**
	 * 从树型结构提取队列
	 *
	 * @param confluencePageTree
	 */
	public List<ConfluencePage> recursiveTree2Queue(ConfluencePage confluencePageTree) {
		List<ConfluencePage> confluencePages = new ArrayList<ConfluencePage>();
		if (confluencePageTree.getChildrens().size() > 0) {
			confluencePages.addAll(confluencePageTree.getChildrens());
			for (ConfluencePage children : confluencePageTree.getChildrens()) {
				confluencePages.addAll(recursiveTree2Queue(children));
			}
		}
		return confluencePages;
	}
	
	/**
	 * 下载页面
	 * TODO:将页面中的链接替换过程、附件下载方法提取出来
	 *
	 * @param page
	 */
	public void downloadPageHtml(ConfluencePage page) throws IOException {
		//为每个页面单独准备目录
		FileUtils.forceMkdir(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId()));
		
		//获取页面
		Document doc = getHtml(page.getUrl() , httpclient);
		
		//下载页面中的css、js、image，并调整doc中的href
		String postfix = "";// 文件后缀名作为文件前缀
		int index = 0; // 用于文件名前缀，防止重名文件覆盖
		Elements importcss = doc.select("link[href]");// 找到document中带有link标签的元素
		for (Element link : importcss) {
			postfix = "css";
			if (link.attr("rel").equals("stylesheet")) {// 如果rel属性为HtmlFileLink
				String href = link.attr("abs:href");// 得到css样式的href的绝对路径
				String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
				link.attr("href" , filename);
				downloadFileFromUrl(href , filename);
				index++;
			}
		}
		Elements media = doc.select("[src]");
		for (Element link : media) {
			if (link.tagName().equals("img")) {
				String src = link.attr("abs:src");
				postfix = getPostfix(src);
				String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
				link.attr("src" , filename);
				downloadFileFromUrl(src , filename);
				index++;
			}
			if (link.tagName().equals("input")) {
				if (link.attr("type").equals("Image")) {
					String src = link.attr("abs:src");
					postfix = getPostfix(src);
					String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
					link.attr("src" , filename);
					downloadFileFromUrl(src , filename);
					index++;
				}
			}
			if (link.tagName().equals("javascript") || link.tagName().equals("script")) {
				String src = link.attr("abs:src");
				postfix = getPostfix(src);
				String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
				link.attr("src" , filename);
				downloadFileFromUrl(src , filename);
				index++;
			}
			if (link.tagName().equals("iframe")) {
				String src = link.attr("abs:src");
				postfix = getPostfix(src);
				String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
				link.attr("src" , filename);
				downloadFileFromUrl(src , filename);
				index++;
			}
			if (link.tagName().equals("embed")) {
				String src = link.attr("abs:src");
				postfix = getPostfix(src);
				String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/" + postfix + index + "." + postfix;
				link.attr("src" , filename);
				downloadFileFromUrl(src , filename);
				index++;
			}
		}
		
		//替换目录：父页面、子页面
		Element pagetree = doc.select("div.plugin_pagetree").first();
		try {
			pagetree.children().remove();
		} catch (Exception e) {
		}
		if (page.getParentID() != null) {
			pagetree.append("<a href='../page-" + page.getParentID() + "/index.html'>返回上级页面</a><br/>");
		}
		for (ConfluencePage children : page.getChildrens()) {
			pagetree.append("<a href='../page-" + children.getId() + "/index.html'>" + children.getName() + "</a><br/>");
		}
		
		//调整A标签，重定向pagid的目录
		Elements pagelinks = doc.select("a[href^=/pages/viewpage.action?pageId=]");
		for (Element apagelink : pagelinks) {
			String ahref = apagelink.attr("href");
			if (ahref.indexOf("&") > 0) {
				ahref = ahref.substring(ahref.indexOf("=") + 1 , ahref.indexOf("&"));
			} else {
				ahref = ahref.substring(ahref.indexOf("=") + 1);
			}
			apagelink.attr("href" , "../page-" + ahref + "/index.html");
		}
		
		//调整附件标签，重定向pagid的目录
		Elements attachmentlinks = doc.select("a[href^=/download/attachments]");
		for (Element attachmentLink : attachmentlinks) {
			String ahref = attachmentLink.attr("href");
			ahref = ahref.substring(ahref.indexOf(page.getId()) + page.getId().length() + 1);
			attachmentLink.attr("href" , "attachments/" + ahref);
		}
		
		//保存页面
		String html = doc.html();
		html = html.replaceAll("\n" , "\r\n");
		FileUtils.writeStringToFile(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/" + "index.html") , html , "utf8");
		
	}
	
	/**
	 * 下载指定页面的所有附件
	 *
	 * @param page
	 */
	public void downloadPageAttachment(ConfluencePage page) {
		for (String[] attachment : page.getAttachments()) {
			String filename = attachment[0];
			String fileurl = attachment[1];
			
			//这个下载失败了，都是37K的文件，不知道是啥问题，回头再研究
			//			try {
			//				FileUtils.copyURLToFile(new URL(fileurl) , new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/" + filename));
			//				log.info("Attachment downloaded：" , fileurl);
			//			} catch (IOException e) {
			//				log.error("Attachment downloading..：" , fileurl);
			//				e.printStackTrace();
			//			}
			
			downloadFileFromUrl(fileurl , this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/" + filename);
		}
	}
	
	/**
	 * 从指定url下载文件，如果文件存在会覆盖
	 *
	 * @param url
	 * @param fileFullPathAndName
	 * @throws Throwable
	 */
	private void downloadFileFromUrl(String url , String fileFullPathAndName) {
		File desc = new File(fileFullPathAndName);
		try {
			FileUtils.forceMkdir(desc.getParentFile());
			
			HttpGet httpget = getHttpGet(url);
			try (CloseableHttpResponse response = httpclient.execute(httpget)) {
				HttpEntity entity = response.getEntity();
				try (InputStream is = entity.getContent(); //
				     OutputStream os = new FileOutputStream(desc)) {
					StreamUtils.copy(is , os);
					log.info("File downloaded：" + url);
				} catch (Throwable e) {
					log.error("File download fail：" + url);
					e.printStackTrace();
				}
			} catch (Throwable e) {
				log.error("File download fail：" + url);
				e.printStackTrace();
			}
		} catch (IOException e) {
			log.error("File download fail：" + url);
			e.printStackTrace();
		}
	}
	
	private String getPostfix(String filename) {
		filename = StringUtils.substringAfterLast(filename , ".");
		filename = StringUtils.substringBefore(filename , "?");
		filename = StringUtils.substringBefore(filename , "/");
		filename = StringUtils.substringBefore(filename , "\\");
		filename = StringUtils.substringBefore(filename , "&");
		filename = StringUtils.substringBefore(filename , "$");
		filename = StringUtils.substringBefore(filename , "%");
		filename = StringUtils.substringBefore(filename , "#");
		filename = StringUtils.substringBefore(filename , "@");
		return filename;
	}
	
	/**
	 * 从空间列表页面获取空间的首页，用于构建最初的树结构，再用子页面方式扩充
	 *
	 * @return
	 */
	public List<ConfluencePage> getSpaceList() {
		List<ConfluencePage> ret = new ArrayList<>();
		
		getHtml(this.ROOT_CONFLUENCE , httpclient);
		Document doc = getHtml(this.VIEW_SPACE_LIST , httpclient);
		Elements tbsl = doc.select("*.space-list-item");
		Element spaceSearchResult = doc.select("#space-search-result").first();
		Elements project = doc.select("*:contains(W-)");
		
		log.info("{}" , doc.html());
		
		return ret;
	}
}
