package cn.sunline.wells.toys.csa.mtv.domain;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConfluencePageMT {
	public ConfluencePageMT(String name, String id, String url) {
		this.name = name == null ? "" : name.trim().replace("\"", "");
		this.id = id;
		this.url = url;
	}
	
	//页面名称
	String name;
	//页面ID
	String id;
	//页面完整url
	String url;
	//状态,
	boolean extended;
	//父页面id
	String parentID;
	//子页面
	List<ConfluencePageMT> childrens = new ArrayList<>();
	//附件
	List<String[]> attachments = new ArrayList<>();
}
