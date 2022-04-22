package cn.wells.csa.stv;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.*;
import java.util.*;

/**
 * 单线程版本
 */
@Slf4j
@Data
public class ConfluenceSpaceArchiverST {
	// 地址常量
	public String ROOT_LOCAL_PATH = "/Users/mmao/mmVolume/Documents/work-长亮科技/DEP-解决方案&产品管理部/UUU-知识库Confluence";
	public String ROOT_CONFLUENCE;
	private static final String PAGES_VIEWPAGE_ACTION_PAGE_ID = "/pages/viewpage.action?pageId=";
	private static final String PAGES_VIEWINFO_ACTION_PAGE_ID = "/pages/viewinfo.action?pageId=";
	private static final String PAGES_VIEWATTACHMENT_ACTION_PAGE_ID = "/pages/viewpageattachments.action?pageId=";
	private static final String SPACES_SEARCH_ACTION = "/rest/spacedirectory/1/search?query=&type=global&status=current&pageSize=1&startIndex=0";
	private static final String LOGIN_ACTION = "/dologin.action";
	private String VIEW_PAGE;
	private String VIEW_INFO;
	private String VIEW_ATTACHMENT;
	private String VIEW_SPACE_LIST;
	private String DO_LOGIN_PARAM;
	
	// 用户名
	private final String username;
	private final String password;
	
	// 其他
	private final ArrayList<String> uniPageIdQueue = new ArrayList<>();
	private final Map<String, String> uniResourceQueue = new HashMap<>();
	private final ThreadLocal<Map<String, String>> sessionCookies = new ThreadLocal<Map<String, String>>();
	
	/**
	 * 拼装URL
	 *
	 * @param username
	 * @param password
	 */
	public ConfluenceSpaceArchiverST(String username, String password, String rootUrl) {
		this.username = username;
		this.password = password;
		this.ROOT_CONFLUENCE = rootUrl;
		
		this.VIEW_PAGE = this.ROOT_CONFLUENCE + PAGES_VIEWPAGE_ACTION_PAGE_ID;
		this.VIEW_INFO = this.ROOT_CONFLUENCE + PAGES_VIEWINFO_ACTION_PAGE_ID;
		this.VIEW_ATTACHMENT = this.ROOT_CONFLUENCE + PAGES_VIEWATTACHMENT_ACTION_PAGE_ID;
		this.VIEW_SPACE_LIST = this.ROOT_CONFLUENCE + SPACES_SEARCH_ACTION;
		this.DO_LOGIN_PARAM = this.ROOT_CONFLUENCE + LOGIN_ACTION;
	}
	
	/**
	 * 登录上去，保存session到cookie
	 */
	public void login() {
		try {
			Connection.Response response = Jsoup.connect(this.DO_LOGIN_PARAM)
					.data("os_username", this.getUsername())
					.data("os_password", this.getPassword())
					.execute();
			if (response.headers().get("X-AUSERNAME").equals(this.getUsername())) {
				log.info(this.getUsername() + " Login success.");
				
				//保存sessionCookies
				this.sessionCookies.set(response.cookies());
			} else {
				log.error(this.getUsername() + " Login failed.");
				return;
			}
			
		} catch (IOException e) {
			log.error("Jsoup login error \n {}", e);
		}
		
	}
	
	/**
	 * 获取页面的下级结构，这里不是递归，就抓一层
	 *
	 * @param pageid
	 * @return
	 */
	public List<ConfluencePageST> getSubPages(String pageid) {
		List<ConfluencePageST> ret = new ArrayList<ConfluencePageST>();
		
		//获取"页面信息"，里面有"子页面"，从这里解析包含的页面
		String urlPageInfo = this.VIEW_INFO + pageid;
		Document doc = null;
		try {
			doc = Jsoup.connect(urlPageInfo)
					.cookies(this.sessionCookies.get())
					.execute()
					.parse();
		} catch (IOException e) {
			log.error("Jsoup getSubPages from {} \n {}", urlPageInfo, e);
		}
		
		if (doc == null || doc.title().contains("页面未找到")) {
			log.error("Get nothing from {}", urlPageInfo);
			return ret;
		}
		
		//抓子页面
		Elements subPages = doc.select("div:contains(层级) + div.basicPanelBody > table.pageInfoTable > tbody:contains(页面) > tr:not(:contains(父页面)) A:not([onclick])");
		
		//解析pageInfoTable - 取所有子页面
		for (int i = 0; i < subPages.size(); i++) {
			Element iElement = subPages.get(i);
			String name = iElement.text();
			String urlhref = this.ROOT_CONFLUENCE + iElement.attr("href");
			String id = urlhref.substring(urlhref.indexOf("pageId") + 7);
			if (urlhref.contains("/display/")) {
				try {
					urlhref = this.ROOT_CONFLUENCE + Jsoup.connect(urlhref)
							.cookies(this.sessionCookies.get())
							.execute()
							.parse()
							.select("A#view-page-info-link")
							.attr("href");
				} catch (IOException e) {
					log.error("Jsoup connect {} error \n {}", urlhref, e);
				}
				id = urlhref.substring(urlhref.indexOf("pageId") + 7);
			}
			log.debug("Subpage name: {} , id: {} , url: {}", name, id, urlhref);
			ConfluencePageST subPage = new ConfluencePageST(name, id, urlhref);
			ret.add(subPage);
		}
		
		return ret;
	}
	
	/**
	 * 递归方式，抓取subpages树型结构
	 *
	 * @param page
	 */
	public void recursiveChildrenSubPages(ConfluencePageST page) {
		try {
			log.info("Recursive {} {} {}", page.getName(), page.getId(), page.getUrl());
			//防重、防回环检查
			if (this.uniPageIdQueue.contains(page.getId())) {
				log.info("Duplicated pageid found : {}", page.getId());
				return;
			}
			this.uniPageIdQueue.add(page.getId());
			
			//对该页下的children进行分析，找出下一级子页面
			for (int i = 0; i < page.childrens.size(); i++) {
				ConfluencePageST iPage = page.childrens.get(i);
				
				//获取附件列表，注意分页
				List<String[]> attachments = this.getAttachments(iPage.getId());
				log.info("Pageid:{} attachments cnt:{}", iPage.getId(), attachments.size());
				if (attachments.size() > 0) {
					iPage.getAttachments().addAll(attachments);
					for (String[] attachment : attachments) {
						log.debug("attachment:{} from pageid:{}", attachment, iPage.getId());
					}
				}
				
				//每个子节点都刷一下，如果抓回来的子页面，继续递归
				List<ConfluencePageST> subPages = this.getSubPages(iPage.getId());
				log.info("Pageid:{} subpages cnt:{}", iPage.getId(), subPages.size());
				if (subPages.size() > 0) {
					for (ConfluencePageST subPage : subPages) {
						log.debug("subpag:{}", subPage);
					}
					//设置父页面ID
					for (ConfluencePageST subPage : subPages) {
						subPage.setParentID(iPage.getId());
					}
					//将抓回来的页面都加到当前面面的children中
					iPage.childrens.addAll(subPages);
					//递归
					this.recursiveChildrenSubPages(iPage);
				}
			}
		} catch (Exception e) {
			log.error("Recursive {} {} {} \n {}", page.getName(), page.getId(), page.getUrl(), e);
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
		String url = this.VIEW_ATTACHMENT + pageid;
		Document doc = null;
		try {
			doc = Jsoup.connect(url)
					.cookies(this.sessionCookies.get())
					.execute()
					.parse();
		} catch (IOException e) {
			log.error("Jsoup getAttachments from {} \n {}", url, e);
			return ret;
		}
		if (doc.title().contains("页面未找到")) {
			log.error("Get nothing from  {}", url);
			return ret;
		}
		
		//解析 - 取所有附件
		Elements attachmentsA = doc.select("A.filename:not(:contains(版本))");
		for (int i = 0; i < attachmentsA.size(); i++) {
			Element iElement = attachmentsA.get(i);
			String filename = iElement.text();
			String href = this.ROOT_CONFLUENCE + iElement.attr("href");
			
			//从tag判断是不是drawio，这种文件要加后缀名
			String dataLinkedResourceID = iElement.attr("data-linked-resource-id");
			Element tag = doc.select("tr#attachment-" + dataLinkedResourceID).first();
			if (tag.text().contains("drawio")) {
				filename = filename + ".drawio";
			}
			
			String[] oneAttachment = {filename, href};
			ret.add(oneAttachment);
		}
		
		//如果存在分页，需要解析抓取
		Elements attachmentPageLinks = doc.select("ol.aui-nav A");
		for (Element ilink : attachmentPageLinks) {
			if (ilink.text().contains("上一个")) {
				continue;
			}
			if (ilink.text().contains("下一个")) {
				continue;
			}
			
			//取分页面，解析其中附件
			Document doc1 = null;
			try {
				doc1 = Jsoup.connect(ilink.attr("abs:href"))
						.cookies(this.sessionCookies.get())
						.execute()
						.parse();
			} catch (IOException e) {
				log.error("Jsoup getAttachments from {} \n {}", url, e.getMessage());
				continue;
			}
			if (doc1.title().contains("页面未找到")) {
				log.error("Get nothing from {}", url);
				continue;
			}
			
			//解析 - 取所有附件
			Elements attachmentsB = doc1.select("A.filename:not(:contains(版本))");
			for (int i = 0; i < attachmentsB.size(); i++) {
				Element iElement = attachmentsB.get(i);
				String filename = iElement.text();
				String href = iElement.attr("abs:href");
				String[] oneAttachment = {filename, href};
				ret.add(oneAttachment);
			}
		}
		return ret;
	}
	
	/**
	 * 从树型结构提取队列
	 *
	 * @param confluencePageTree
	 */
	public List<ConfluencePageST> recursiveTree2Queue(ConfluencePageST confluencePageTree) {
		List<ConfluencePageST> confluencePages = new ArrayList<ConfluencePageST>();
		if (confluencePageTree.getChildrens().size() > 0) {
			confluencePages.addAll(confluencePageTree.getChildrens());
			for (ConfluencePageST children : confluencePageTree.getChildrens()) {
				confluencePages.addAll(this.recursiveTree2Queue(children));
			}
		}
		return confluencePages;
	}
	
	/**
	 * 构建树型的列表
	 *
	 * @param confluencePageTree
	 * @return
	 */
	private List<String[]> recursiveTree2Menu(ConfluencePageST confluencePageTree, String head) {
		List<String[]> ret = new ArrayList<>();
		if (confluencePageTree.getChildrens().size() > 0) {
			for (ConfluencePageST children : confluencePageTree.getChildrens()) {
				ret.add(new String[]{head + children.getName(), children.getId()});
				ret.addAll(this.recursiveTree2Menu(children, "&nbsp&nbsp&nbsp&nbsp" + head));
			}
		}
		return ret;
	}
	
	/**
	 * 下载页面
	 *
	 * @param page
	 */
	public void downloadPageHtml(ConfluencePageST page) {
		try {
			//为每个页面单独准备目录
			FileUtils.forceMkdir(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId()));
			
			//获取页面
			Document doc = Jsoup.connect(page.getUrl())
					.cookies(this.sessionCookies.get())
					.execute()
					.parse();
			
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
						link.attr("href", filename);
						this.downloadFileFromUrl(href, filename);
						this.uniResourceQueue.put(href, filename);
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
						link.attr("src", filename);
						this.downloadFileFromUrl(src, filename);
						this.uniResourceQueue.put(src, filename);
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
						link.attr("src", filename);
						this.downloadFileFromUrl(src, filename);
						this.uniResourceQueue.put(src, filename);
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
						this.downloadFileFromUrl(src, filename);
						this.uniResourceQueue.put(src, filename);
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
							link.attr("src", filename);
							this.downloadFileFromUrl(src, filename);
							this.uniResourceQueue.put(src, filename);
						}
					}
				}
				//图片要特殊处理，排除掉附件，剩下的应该就是不同cdn的公共图片资源
				if (link.tagName().equals("img")) {
					String src = link.attr("abs:src");
					postfix = this.getPostfix(src);
					
					//附件的引用特殊处理
					List<String[]> attachmentset = page.getAttachments();
					boolean containedByAttachments = false;
					for (String[] attachmentItem : attachmentset) {
						String fileurl = attachmentItem[1];
						fileurl = StringUtils.substringBefore(fileurl, "?");
						String compareSrc = StringUtils.substringBefore(src, "?");
						if (fileurl.equals(compareSrc)) {
							containedByAttachments = true;
							String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/img-" + UUID.randomUUID() + "." + postfix;
							link.attr("src", filename);
							break;
						}
					}
					if (!containedByAttachments) {
						String filename = this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/elements/img-" + UUID.randomUUID() + "." + postfix;
						link.attr("src", filename);
						this.downloadFileFromUrl(src, filename);
					}
				}
			}
			
			//替换目录：父页面、子页面
			List<String[]> newPageTree = this.recursiveTree2Menu(page, " ");
			Element pagetree = doc.select("div.plugin_pagetree").first();
			try {
				pagetree.children().remove();
				//看不到左侧的列表，要补个stlye和元素
				Element sidebar = doc.select("div.ia-fixed-sidebar").first();
				sidebar.attr("style", "width: 285px; visibility: visible; top: 40px; left: 0px;");
				sidebar.append("<div class=\"ia-splitter-handle tipsy-enabled\" data-tooltip=\"收起侧边栏 ( [ )\" original-title=\" ([)\"><div class=\"ia-splitter-handle-highlight confluence-icon-grab-handle\"></div></div>");
				
				if (page.getParentID() != null) {
					pagetree.append("<a href='../page-" + page.getParentID() + "/index.html'>返回上级页面</a><br/>");
				}
				for (String[] pagecon : newPageTree) {
					pagetree.append("<a href='../page-" + pagecon[1] + "/index.html'>" + pagecon[0] + "</a><br/>");
				}
				
			} catch (Exception e) {
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
			
			//补充附件列表进去
			List<String[]> attachmentset = page.getAttachments();
			if (attachmentset.size() > 0) {
				Element attachmentsSection = doc.select("div#content").first();
				attachmentsSection.append("<br/>Attachments:<hr><br/>");
				for (String[] attachmentItem : attachmentset) {
					String filename = attachmentItem[0];
					String fileurl = attachmentItem[1];
					fileurl = fileurl.substring(fileurl.indexOf("/download/attachments"));
					attachmentsSection.append("<a href=\"" + fileurl + "\">" + filename + "<a><br/>");
				}
			}
			
			//调整附件标签，重定向到attachments目录
			Elements attachmentlinks = doc.select("a[href^=/download/attachments]");
			for (Element attachmentLink : attachmentlinks) {
				String ahref = attachmentLink.attr("href");
				ahref = ahref.substring(ahref.indexOf(page.getId()) + page.getId().length() + 1);
				attachmentLink.attr("href", "attachments/" + ahref);
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
			
			//保存页面
			String html = doc.html();
			html = html.replaceAll("\n", "\r\n");
			FileUtils.writeStringToFile(new File(this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/" + "index.html"), html, "utf8");
			
		} catch (Exception e) {
			log.error("DownloadPageHtml {} \n {}", page.getUrl(), e);
		}

	}
	
	/**
	 * 下载指定页面的所有附件
	 *
	 * @param page
	 */
	public void downloadPageAttachment(ConfluencePageST page) {
		for (String[] attachment : page.getAttachments()) {
			String filename = attachment[0];
			String fileurl = attachment[1];
			
			this.downloadFileFromUrl(fileurl, this.ROOT_LOCAL_PATH + "/page-" + page.getId() + "/attachments/" + filename);
		}
	}
	
	/**
	 * 从指定url下载文件，如果文件存在会覆盖
	 *
	 * @param url
	 * @param fileFullPathAndName
	 * @throws Throwable
	 */
	private void downloadFileFromUrl(String url, String fileFullPathAndName) {
		try {
			log.debug("File downloading：" + url);
			
			//准备父目录
			File desc = new File(fileFullPathAndName);
			FileUtils.forceMkdir(desc.getParentFile());
			
			//获取文件，ignoreContentType
			Connection.Response response = Jsoup.connect(url)
					.method(Connection.Method.GET)
					.ignoreContentType(true)
					.cookies(this.getSessionCookies().get())
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
		} catch (Exception e) {
			log.error("File download fail：{} {}", url, e.getMessage());
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
	 * 从空间列表页面获取空间的首页，用于构建最初的树结构，再用子页面方式扩充
	 *
	 * @return
	 */
	public List<ConfluencePageST> getSpaceList() {
		List<ConfluencePageST> ret = new ArrayList<>();
		
		try {
			log.info("Requiring spacelist..");
			log.debug("Jsoup require spacelist: {}", this.VIEW_SPACE_LIST);
			//获取spaces总数
			Document doc = Jsoup.connect(this.VIEW_SPACE_LIST)
					.cookies(this.sessionCookies.get())
					.execute()
					.parse();
			
			//循环获取spaces
			int totalSpaces = Integer.parseInt(doc.select("totalsize").first().text());
			String newVIEW_SPACE_LIST = this.VIEW_SPACE_LIST.replaceAll("pageSize=1", "pageSize=10");
			for (int i = 0; i < totalSpaces / 10 + 1; i++) {
				String url = newVIEW_SPACE_LIST.replaceAll("startIndex=0", "startIndex=" + i * 10);
				
				Document doc1 = Jsoup.connect(url)
						.cookies(this.sessionCookies.get())
						.execute()
						.parse();
				
				Elements spaces = doc1.select("spaces");
				for (Element space : spaces) {
					Element alink = space.select("link[rel=alternate]").first();
					String spacename = space.attr("name");
					String spaceurl = alink.attr("href");
					String spaceid = spaceurl.substring(spaceurl.indexOf("pageId=") + 7);
					
					if (spaceurl.contains("/display/")) {
						try {
							spaceurl = this.ROOT_CONFLUENCE + Jsoup.connect(spaceurl)
									.cookies(this.sessionCookies.get())
									.execute()
									.parse()
									.select("A#view-page-info-link")
									.attr("href");
						} catch (IOException e) {
							log.error("Jsoup connect {} error \n {}", spaceurl, e);
						}
						spaceid = spaceurl.substring(spaceurl.indexOf("pageId") + 7);
					}
					
					ConfluencePageST newSpace = new ConfluencePageST(spacename, spaceid, spaceurl);
					ret.add(newSpace);
					log.debug("Recognized a spaces: {}", newSpace);
				}
			}
		} catch (IOException e) {
			log.error("GetSpaceList from {} \n {}", this.VIEW_SPACE_LIST, e);
		}
		
		return ret;
	}
	
	/**
	 * 将空间列表保存到索引文件，作为入口
	 *
	 * @param page
	 */
	public void saveIndex(String filename, ConfluencePageST page) {
		String content = "";
		for (ConfluencePageST aspace : page.getChildrens()) {
			content += "<a href=\"page-" + aspace.getId() + "/index.html\">" + aspace.getName() + "</a>\r\n";
			content += "<br/>\r\n";
		}
		
		try {
			FileUtils.writeStringToFile(new File(filename), content, "utf8", true);
		} catch (IOException e) {
			log.error("IOException when write spacelist", e);
		}
	}
	
}
