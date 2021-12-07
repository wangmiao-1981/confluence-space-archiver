package cn.sunline.wells.toys;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class ConfluencePage {
	public ConfluencePage(String name , String id , String url) {
		this.name = name == null ? "" : name.trim().replace("\"" , "");
		this.id = id;
		this.url = url;
	}
	
	//页面名称
	String name;
	//页面ID
	String id;
	//页面完整url
	String url;
	//父页面id
	String parentID;
	//子页面
	List<ConfluencePage> childrens = new ArrayList<>();
	//附件
	List<String[]> attachments = new ArrayList<>();
}
