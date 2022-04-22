package cn.wells.csa.stv;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConfluencePageST {
	public ConfluencePageST(String name, String id, String url) {
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
	List<ConfluencePageST> childrens = new ArrayList<>();
	//附件
	List<String[]> attachments = new ArrayList<>();
}
