package cn.wells.csa.mtv.service;

import cn.wells.csa.mtv.config.EnumCSATaskStatus;
import cn.wells.csa.mtv.dto.DTO_Page;
import cn.wells.csa.mtv.repository.DAO_Page;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.scheduling.annotation.Async;

import java.io.*;
import java.util.*;

@Slf4j
@Data
@ConfigurationProperties(prefix = "confluence")
public class CSA_Context {
	@Autowired
	DAO_Page dao_page;
	
	//登录成功，拼接完成标志，避免反复启动
	private boolean inited;
	
	// 地址常量
	private static final String C_LOGIN_ACTION = "/dologin.action";
	private static final String C_API_SPACES_CNT = "/rest/spacedirectory/1/search?query=&status=current&pageSize=1&startIndex=0";
	private static final String C_API_SPACES_LIST = "/rest/api/space?limit=1&start=0";
	private static final String C_API_SPACE_PAGES = "/rest/api/space/!!!SPACEKEY!!!/content/page?depth=root";
	private static final String C_API_SUB_PAGES = "/rest/api/content/!!!PAGEID!!!/child/page";
	private static final String C_API_ATTACHMENTS = "/rest/api/content/!!!PAGEID!!!/child/attachment";
	private static final String C_API_HISTORY = "/rest/api/content/!!!PAGEID!!!/history";
	private String DO_LOGIN_PARAM;
	private String API_SPACE_CNT;
	private String API_SPACE_LIST;
	private String API_SPACE_PAGES;
	private String API_SUB_PAGES;
	private String API_ATTACHMENTS;
	private String API_HISTORYS;
	
	//登录cookie
	private Map<String, String> sessionCookies = new HashMap<>();
	//防止资源重复的列表，用href作key
	private HashMap<String, String> uniResourceQueue = new HashMap<String, String>();
	
	// 配置内容
	private String username; //登录用户
	private String password; //登录密码
	private String ROOT_CONFLUENCE; //登录地址
	private String ROOT_LOCAL_PATH; //保存目录
	private String ROOT_REPLACE_VHOST; //些地址会在前面加这个开头，这时需要替换过来
	private String ROOT_ALIAS_ROOT; //有些文件会用这个域名而不是IP，这时需要替换过来
	private boolean PROC_SUB_PAGE = true; //是否爬取下级子页面
	private boolean PROC_DOWNLOAD_PAGE = true; //是否下载页面，先爬一个完整的页面树后，再开，可保障目录树的下级是完整的
	
	/**
	 * 拼接url
	 * 登录，保存session
	 *
	 * @return
	 */
	public boolean init() {
		//防止重复初始化，但是并不防并发，调用处要加锁
		if (this.inited) {
			return true;
		}
		
		//拼接真实url
		this.DO_LOGIN_PARAM = this.ROOT_CONFLUENCE + C_LOGIN_ACTION;
		this.API_SPACE_CNT = this.ROOT_CONFLUENCE + C_API_SPACES_CNT;
		this.API_SPACE_LIST = this.ROOT_CONFLUENCE + C_API_SPACES_LIST;
		this.API_SPACE_PAGES = this.ROOT_CONFLUENCE + C_API_SPACE_PAGES;
		this.API_SUB_PAGES = this.ROOT_CONFLUENCE + C_API_SUB_PAGES;
		this.API_ATTACHMENTS = this.ROOT_CONFLUENCE + C_API_ATTACHMENTS;
		this.API_HISTORYS = this.ROOT_CONFLUENCE + C_API_HISTORY;
		
		try {
			//尝试登录
			Connection.Response response = Jsoup.connect(this.DO_LOGIN_PARAM)
					.data("os_username", this.getUsername())
					.data("os_password", this.getPassword())
					.execute();
			
			//检查登录结果
			if (response.headers().get("X-AUSERNAME").equals(this.getUsername())) {
				//保存sessionCookies
				this.sessionCookies.putAll(response.cookies());
				this.inited = true;
				log.info(this.getUsername() + " Login success.");
			} else {
				log.error("{} Login failed.", this.getUsername());
				
				this.inited = false;
				return false;
			}
			
		} catch (IOException e) {
			log.error("{} Login exception. \n {}", this.getUsername(), e);
			return false;
		}
		
		return true;
	}
	
	/**
	 * 获取空间列表
	 *
	 * @return
	 */
	public List<DTO_Page> getSpaceList() throws IOException {
		List<DTO_Page> ret = new ArrayList<>();
		
		log.info("Requiring spacelist..");
		
		//获取spaces cnt
		JSONObject retSpaceCount = JSONObject.parseObject(Jsoup.connect(this.getAPI_SPACE_CNT())
				.ignoreContentType(true)
				.cookies(this.getSessionCookies())
				.header("Accept", "application/json")
				.execute()
				.body());
		int totalSpaces = retSpaceCount.getInteger("totalSize");
		
		//循环获取spaces，分页取空间列表
		String newVIEW_SPACE_LIST = this.API_SPACE_LIST.replaceAll("limit=1", "limit=10");
		for (int i = 0; i < totalSpaces / 10 + 1; i++) {
			String url = newVIEW_SPACE_LIST.replaceAll("start=0", "start=" + i * 10);
			
			JSONArray spaces = JSONObject.parseObject(Jsoup.connect(url)
					.ignoreContentType(true)
					.cookies(this.getSessionCookies())
					.header("Accept", "application/json")
					.execute()
					.body()).getJSONArray("results");
			
			for (JSONObject ispace : spaces.toJavaList(JSONObject.class)) {
				String spaceid = ispace.getString("id");
				String spacename = ispace.getString("name");
				String spacekey = ispace.getString("key");
				String spaceurl = this.ROOT_CONFLUENCE + ispace.getJSONObject("_links").getString("webui");
				
				DTO_Page newSpace = new DTO_Page();
				newSpace.setId(spaceid);
				newSpace.setName(spacename);
				newSpace.setKey(spacekey);
				newSpace.setUrl(spaceurl);
				newSpace.setParent_page_id("ROOT");
				newSpace.setType("SPACE");
				ret.add(newSpace);
				
				log.debug("Recognize a space: {}", newSpace.getName());
			}
		}
		
		return ret;
	}
	
	/**
	 * 取页面的最后更新时间
	 *
	 * @param pageid
	 * @return
	 * @throws IOException
	 */
	public String getPageLastUpdate(String pageid) throws IOException {
		String url = this.API_HISTORYS.replaceAll("!!!PAGEID!!!", pageid);
		String lastupdate = JSONObject.parseObject(Jsoup.connect(url)
						.ignoreContentType(true)
						.cookies(this.getSessionCookies())
						.header("Accept", "application/json")
						.execute()
						.body())
				.getJSONObject("lastUpdated")
				.getString("when");
		
		return lastupdate;
	}
	
	/**
	 * 取sub pages
	 * 可能是空间，也可能是页面
	 * api要求，空间用key
	 * api要求，页面用id
	 *
	 * @return
	 */
	public List<DTO_Page> getSubPages(DTO_Page parentPage) throws IOException {
		List<DTO_Page> ret = new ArrayList<>();
		
		//参数合法性检查
		if (!this.validatePageType(parentPage)) {
			return ret;
		}
		
		String url = null;
		
		//空间，获取root pages
		if (parentPage.getType().equals("SPACE")) {
			url = this.API_SPACE_PAGES.replaceAll("!!!SPACEKEY!!!", parentPage.getKey());
		}
		
		//页面，获取sub pages
		if (parentPage.getType().equals("PAGE")) {
			url = this.API_SUB_PAGES.replaceAll("!!!PAGEID!!!", parentPage.getId());
		}
		
		JSONArray pages = JSONObject.parseObject(Jsoup.connect(url)
				.ignoreContentType(true)
				.cookies(this.getSessionCookies())
				.header("Accept", "application/json")
				.execute()
				.body()).getJSONArray("results");
		
		for (JSONObject o : pages.toJavaList(JSONObject.class)) {
			DTO_Page newFoundPage = new DTO_Page();
			newFoundPage.setId(o.getString("id"));
			newFoundPage.setName(o.getString("title"));
			newFoundPage.setUrl(this.getROOT_CONFLUENCE() + o.getJSONObject("_links").getString("webui"));
			newFoundPage.setType("PAGE");
			newFoundPage.setParent_page_id(parentPage.getId());
			newFoundPage.setUpdate_date(this.getPageLastUpdate(o.getString("id")));
			
			ret.add(newFoundPage);
			log.debug("{} Recognize a page: {}", ret.size(), newFoundPage.getName());
		}
		
		return ret;
	}
	
	/**
	 * 取attachments
	 * 返回String[filename,url]
	 *
	 * @param page
	 * @return
	 */
	public List<String[]> getAttachments(DTO_Page page) throws IOException {
		ArrayList<String[]> ret = new ArrayList<>();
		
		//参数合法性检查
		if (!this.validatePageType(page)) {
			return ret;
		}
		
		if (!page.getType().equalsIgnoreCase("PAGE")) {
			log.debug("Only PAGE has attachments {}", page);
			return ret;
		}
		
		JSONArray attachments = JSONObject.parseObject(Jsoup.connect(this.API_ATTACHMENTS.replaceAll("!!!PAGEID!!!", page.getId()))
				.ignoreContentType(true)
				.cookies(this.getSessionCookies())
				.header("Accept", "application/json")
				.execute()
				.body()).getJSONArray("results");
		
		for (JSONObject o : attachments.toJavaList(JSONObject.class)) {
			String filename = o.getString("title");
			
			String filetype = o.getJSONObject("metadata").getString("mediaType");
			if (filetype.equalsIgnoreCase("application/drawio")) {
				filetype = ".drawio";
				
				if (!filename.contains(filetype)) {
					filename = filename + filetype;
				}
			}
			if (filetype.equalsIgnoreCase("image/png")) {
				filetype = ".png";
				
				if (!filename.contains(filetype)) {
					filename = filename + filetype;
				}
			}
			if (filetype.equalsIgnoreCase("application/gliffy+json")) {
				filetype = ".json";
				
				if (!filename.contains(filetype)) {
					filename = filename + filetype;
				}
			}
			
			String filehref = this.ROOT_CONFLUENCE + o.getJSONObject("_links").getString("download");
			
			ret.add(new String[]{filename, filehref});
			log.debug("Recognize an attachment: {}", filename);
		}
		
		return ret;
	}
	
	/**
	 * 下载页面
	 *
	 * @param page
	 */
	public void downloadPage2Local(DTO_Page page) throws IOException {
		//为每个页面单独准备目录
		FileUtils.forceMkdir(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId()));
		
		//获取页面
		Document doc = Jsoup.connect(page.getUrl())
				.cookies(this.sessionCookies)
				.execute()
				.parse();
		
		//获取attachments
		List<String[]> attachmentset = this.getAttachments(page);
		if (attachmentset.size() > 0) {
			for (String[] attachmentItem : attachmentset) {
				String filename = attachmentItem[0];
				String fileurl = attachmentItem[1];
				this.downloadFileFromUrl(fileurl, this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/" + filename);
			}
		}
		
		//下载页面中的css、js、image，并调整doc中的href
		String postfix = "";// 文件后缀名作为文件前缀
		Elements importcss = doc.select("link[href]");// 找到document中带有link标签的元素
		for (Element link : importcss) {
			postfix = "css";
			if (link.attr("rel").equals("stylesheet")) {// 如果rel属性为HtmlFileLink
				String href = link.attr("abs:href");// 得到css样式的href的绝对路径
				//判断是否属于重复下载的页面css、js等重复的资源文件
				if (this.uniResourceQueue.containsKey(href)) {
					String filename = this.uniResourceQueue.get(href);
					link.attr("href", filename);
				} else {
					String filename = this.ROOT_LOCAL_PATH + "/commons/css-" + UUID.randomUUID() + "." + postfix;
					this.downloadFileFromUrl(href, filename);
					//下载完成，换成相对路径保存，给标签用
					filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
					this.uniResourceQueue.put(href, filename);
					link.attr("href", filename);
				}
			}
		}
		Elements media = doc.select("[src]");
		for (Element link : media) {
			if (link.tagName().equals("javascript") || link.tagName().equals("script")) {
				String src = link.attr("abs:src");
				postfix = this.getPostfix(src);
				//判断是否属于重复下载的页面css、js等重复的资源文件
				if (this.uniResourceQueue.containsKey(src)) {
					String filename = this.uniResourceQueue.get(src);
					link.attr("src", filename);
				} else {
					String filename = this.ROOT_LOCAL_PATH + "/commons/js-" + UUID.randomUUID() + "." + postfix;
					this.downloadFileFromUrl(src, filename);
					//下载完成，换成相对路径保存，给标签用
					filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
					this.uniResourceQueue.put(src, filename);
					link.attr("src", filename);
				}
			}
			if (link.tagName().equals("iframe")) {
				String src = link.attr("abs:src");
				postfix = this.getPostfix(src);
				//判断是否属于重复下载的页面css、js等重复的资源文件
				if (this.uniResourceQueue.containsKey(src)) {
					String filename = this.uniResourceQueue.get(src);
					link.attr("src", filename);
				} else {
					String filename = this.ROOT_LOCAL_PATH + "/commons/ifr-" + UUID.randomUUID() + "." + postfix;
					this.downloadFileFromUrl(src, filename);
					//下载完成，换成相对路径保存，给标签用
					filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
					this.uniResourceQueue.put(src, filename);
					link.attr("src", filename);
				}
			}
			if (link.tagName().equals("embed")) {
				String src = link.attr("abs:src");
				postfix = this.getPostfix(src);
				//判断是否属于重复下载的页面css、js等重复的资源文件
				if (this.uniResourceQueue.containsKey(src)) {
					String filename = this.uniResourceQueue.get(src);
					link.attr("src", filename);
				} else {
					String filename = this.ROOT_LOCAL_PATH + "/commons/emb-" + UUID.randomUUID() + "." + postfix;
					link.attr("src", filename);
					//下载完成，换成相对路径保存，给标签用
					filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
					this.uniResourceQueue.put(src, filename);
					link.attr("src", filename);
				}
			}
			if (link.tagName().equals("input")) {
				if (link.attr("type").equals("Image")) {
					String src = link.attr("abs:src");
					postfix = this.getPostfix(src);
					//判断是否属于重复下载的页面css、js等重复的资源文件
					if (this.uniResourceQueue.containsKey(src)) {
						String filename = this.uniResourceQueue.get(src);
						link.attr("src", filename);
					} else {
						String filename = this.ROOT_LOCAL_PATH + "/commons/inp-" + UUID.randomUUID() + "." + postfix;
						this.downloadFileFromUrl(src, filename);
						//下载完成，换成相对路径保存，给标签用
						filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
						this.uniResourceQueue.put(src, filename);
						link.attr("src", filename);
					}
				}
			}
			
			//图片要特殊处理，避免在elements和attachments目录中下载两遍
			if (link.tagName().equals("img")) {
				String src = link.attr("abs:src");
				postfix = this.getPostfix(src);
				
				//附件的引用特殊处理
				boolean containedByAttachments = false;
				for (String[] attachmentItem : attachmentset) {
					String filename = attachmentItem[0];
					if (src.endsWith(filename)) {
						containedByAttachments = true;
						filename = "./attachments/" + filename;
						link.attr("src", filename);
						break;
					}
				}
				if (!containedByAttachments) {
					String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/img-" + UUID.randomUUID() + "." + postfix;
					this.downloadFileFromUrl(src, filename);
					//下载完成，换成相对路径保存，给标签用
					filename = filename.replaceAll(this.ROOT_LOCAL_PATH, "..");
					link.attr("src", filename);
				}
			}
		}
		
		//调整A标签，重定向pagid的目录
		Elements pagelinks = doc.select("a[href^=/pages/viewpage.action?pageId=]");
		for (Element apagelink : pagelinks) {
			String ahref = apagelink.attr("href");
			if (ahref.indexOf("&") > 0) {
				ahref = ahref.substring(ahref.indexOf("=") + 1, ahref.indexOf("&"));
			} else {
				ahref = ahref.substring(ahref.indexOf("=") + 1);
			}
			apagelink.attr("href", "../page-" + ahref + "/index.html");
		}
		
		//调整附件标签，重定向到attachments目录
		Elements attachmentlinks = doc.select("a[href^=/download/attachments]");
		for (Element attachmentLink : attachmentlinks) {
			String ahref = attachmentLink.attr("href");
			ahref = ahref.substring(ahref.indexOf(page.getId()) + page.getId().length() + 1);
			attachmentLink.attr("href", "./attachments/" + ahref);
		}
		
		//下载页面中的drawio要替换成img
		Elements tagDrawios = doc.select("div[data-macro-name=drawio]");// 找到document中带有link标签的元素
		for (Element tagDrawio : tagDrawios) {
			Element script = tagDrawio.children().get(1);
			String scriptString = script.html();
			String imgfilename = StringUtils.substringAfter(scriptString, "readerOpts.imageUrl");
			imgfilename = StringUtils.substringBefore(imgfilename, "readerOpts.editUrl");
			imgfilename = StringUtils.substringAfter(imgfilename, page.getId());
			imgfilename = StringUtils.substringBefore(imgfilename, "?");
			imgfilename = imgfilename.replaceAll("'", "");
			imgfilename = imgfilename.replaceAll("/", "");
			imgfilename = imgfilename.replaceAll(" ", "");
			imgfilename = imgfilename.replaceAll("\\+", "");
			imgfilename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/" + imgfilename;
			
			tagDrawio.children().remove();
			tagDrawio.append("<img src=\"" + imgfilename + "\" style=\"width:80%;\"/><br/>");
		}
		
		//补充目录：返回上级、子页面
		List<String[]> newTreeMenu = this.recursiveTree2Menu(page, 0);
		Element pagetree = doc.select("ul > div.plugin_pagetree_children").first();
		if (pagetree != null) {
			pagetree.children().remove();
			//看不到左侧的列表，要补个stlye和元素
			Element sidebar = doc.select("div.ia-fixed-sidebar").first();
			sidebar.attr("style", "width: 285px; visibility: visible; top: 40px; left: 0px;");
			sidebar.append("<div class=\"ia-splitter-handle tipsy-enabled\" data-tooltip=\"收起侧边栏 ( [ )\" original-title=\" ([)\"><div class=\"ia-splitter-handle-highlight confluence-icon-grab-handle\"></div></div>");
			//返回上级
			Element ul = new Element("ul").addClass("plugin_pagetree_children");
			ul.appendElement("li")
					.appendElement("div").addClass("plugin_pagetree_children_content")
					.appendElement("span").addClass("plugin_pagetree_children_span")
					.appendElement("a")
					.text("返回上级")
					.attr("href", "../page-" + page.getParent_page_id() + "/index.html");
			//添加子页面
			for (String[] pagecon : newTreeMenu) {
				ul.appendElement("li")
						.appendElement("div").addClass("plugin_pagetree_children_content")
						.appendElement("span").addClass("plugin_pagetree_children_span")
						.appendElement("a")
						.text(pagecon[0])
						.attr("style", "margin-left:" + Integer.parseInt(pagecon[2]) * 10 + "px !important")
						.attr("href", "../page-" + pagecon[1] + "/index.html");
			}
			pagetree.appendChild(ul);
		}
		
		//补充附件列表进去
		if (attachmentset.size() > 0) {
			Element attachmentsSection = doc.select("div#content").first();
			attachmentsSection.append("<br/>Attachments:<hr><br/>");
			for (String[] attachmentItem : attachmentset) {
				String filename = attachmentItem[0];
				String fileurl = "./attachments/" + filename;
				attachmentsSection.append("<a href=\"" + fileurl + "\">" + filename + "<a><br/>");
			}
		}
		
		//保存页面
		String html = doc.html();
		html = html.replaceAll("\n", "\r\n");
		FileUtils.writeStringToFile(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/" + "index.html"), html, "utf8");
		
	}
	
	/**
	 * 递归查询树形结构
	 *
	 * @param page
	 * @param s
	 * @return
	 */
	private List<String[]> recursiveTree2Menu(DTO_Page page, int s) {
		List<String[]> ret = new ArrayList<>();
		
		List<DTO_Page> childrenPages = this.dao_page.getPagesByParentid(page.getId());
		for (DTO_Page ipage : childrenPages) {
			String[] retSubPage = new String[]{ipage.getName(), ipage.getId(), s + ""};
			ret.add(retSubPage);
			
			//递归查询树形结构
			List<String[]> retRecursive = this.recursiveTree2Menu(ipage, s + 1);
			ret.addAll(retRecursive);
		}
		return ret;
	}
	
	/**
	 * 从指定url下载文件，如果文件存在会覆盖
	 *
	 * @param url
	 * @param fileFullPathAndName
	 * @throws Throwable
	 */
	private void downloadFileFromUrl(String url, String fileFullPathAndName) throws IOException {
		log.debug("File downloading：" + url);
		
		//url是alias
		if (url.startsWith(this.ROOT_ALIAS_ROOT)) {
			url = url.replaceAll(this.ROOT_ALIAS_ROOT, this.ROOT_CONFLUENCE);
		}
		
		//url多了vhost
		if (url.startsWith(this.ROOT_CONFLUENCE + this.ROOT_REPLACE_VHOST)) {
			url = url.replaceAll(this.ROOT_CONFLUENCE + this.ROOT_REPLACE_VHOST, this.ROOT_CONFLUENCE);
		}
		
		try {
			//准备父目录
			File desc = new File(fileFullPathAndName);
			FileUtils.forceMkdir(desc.getParentFile());
			
			//获取文件，ignoreContentType
			Connection.Response response = Jsoup.connect(url)
					.method(Connection.Method.GET)
					.ignoreContentType(true)
					.ignoreHttpErrors(true)
					.cookies(this.sessionCookies)
					.execute();
			
			//用stream写入文件
			BufferedInputStream bufferedInputStream = response.bodyStream();
			byte[] buffer = new byte[1024];
			int readLenghth;
			FileOutputStream fileOutputStream = new FileOutputStream(new File(fileFullPathAndName));
			BufferedOutputStream bufferedOutputStream = new BufferedOutputStream(fileOutputStream);
			while ((readLenghth = bufferedInputStream.read(buffer, 0, 1024)) != -1) {//先读出来，保存在buffer数组中
				bufferedOutputStream.write(buffer, 0, readLenghth);//再从buffer中取出来保存到本地
			}
			bufferedOutputStream.close();
			fileOutputStream.close();
			bufferedInputStream.close();
			
			log.debug("File downloaded：" + url);
		} catch (IllegalArgumentException e) {
			//名称不合法，不涉及重试，不抛出去
			log.error("File download Must supply a valid URL:{}", url);
		} catch (IOException ex) {
			throw ex;
		}
	}
	
	private String getPostfix(String filename) {
		filename = StringUtils.substringBefore(filename, "?");
		filename = StringUtils.substringAfterLast(filename, ".");
		filename = StringUtils.substringBefore(filename, "?");
		filename = StringUtils.substringBefore(filename, "/");
		filename = StringUtils.substringBefore(filename, "\\");
		filename = StringUtils.substringBefore(filename, "&");
		filename = StringUtils.substringBefore(filename, "$");
		filename = StringUtils.substringBefore(filename, "%");
		filename = StringUtils.substringBefore(filename, "#");
		filename = StringUtils.substringBefore(filename, "@");
		return filename;
	}
	
	/**
	 * 参数合法性检查
	 */
	private boolean validatePageType(DTO_Page parentPage) {
		//类型不允许为空
		if (parentPage.getType() == null) {
			log.error("Page type can't be null. page-> {}", parentPage);
			return false;
		}
		
		//类型"SPACE" "PAGE"
		if (!(parentPage.getType().equalsIgnoreCase("SPACE") || parentPage.getType().equalsIgnoreCase("PAGE"))) {
			log.error("Page type must in (SPACE||PAGE). page-> {}", parentPage);
			return false;
		}
		
		//"SPACE"的key不能为空
		if (parentPage.getType().equalsIgnoreCase("SPACE") && parentPage.getKey() == null) {
			log.error("Page is a space, and key should not be null. page->{}", parentPage);
			return false;
		}
		
		return true;
	}
	
	/**
	 * 异步执行，页面处理任务
	 *
	 * @param page
	 */
	@Async("async-csa-pagedownload")
	public void asProcPage(DTO_Page page) {
		//初始化上下文
		this.init();
		
		//参数合法性检查
		if (page.getStatus() != EnumCSATaskStatus.COMMIT) {
			log.error("Task status must be COMMIT {}", page);
			return;
		}
		if (page.getRetry() <= 0) {
			log.error("Task retry <=0 {}", page);
			return;
		}
		
		//开始处理
		log.info("Process {} {} \n{}", page.getId(), page.getName(), page.getUrl());
		page.setStatus(EnumCSATaskStatus.PROC);
		this.dao_page.save(page);
		
		try {
			//拓展子页面爬个完整的树出来
			if (this.PROC_SUB_PAGE) {
				log.debug("Fetch subpage for page {}", page.getId());
				List<DTO_Page> subPages = this.getSubPages(page);
				
				for (DTO_Page subPage : subPages) {
					//如果发现新的page（以id为准）保存起来
					if (!this.dao_page.existsById(subPage.getId())) {
						subPage.setStatus(EnumCSATaskStatus.READY);
						subPage.setRetry(3);
						
						log.info("Recognize subpage {} -> {}", page.getName(), subPage.getName());
						this.dao_page.save(subPage);
					}
				}
			}
			
			//下载页面、附件，并作路径变更，保存在本地
			if (this.PROC_DOWNLOAD_PAGE) {
				log.debug("Download page {}", page.getId());
				this.downloadPage2Local(page);
			}
			
		} catch (Exception e) {
			log.error("Exception when proc pageid: {} {}", page.getId(), e.getMessage());
			e.printStackTrace();
			
			page.setStatus(EnumCSATaskStatus.ERROR);
			page.setRetry(page.getRetry() - 1);
			page.setErrlog(e.getMessage());
			this.dao_page.save(page);
			
			return;
		}
		
		//处理完成，过程中没有严重异常
		page.setStatus(EnumCSATaskStatus.DONE);
		this.dao_page.save(page);
	}
}
