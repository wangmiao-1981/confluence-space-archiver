package cn.wells.csa.mtv.dto;

import cn.wells.csa.mtv.config.EnumCSATaskStatus;
import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.Id;

@Data
@Entity(name = "cspace_page")
public class DTO_Page {
	
	@Id
	@Column(length = 32)
	private String id;
	
	@Column(length = 8)
	private String type;
	
	@Column(length = 32)
	private String key;
	
	@Column(length = 512)
	private String name;
	
	@Column(length = 32)
	private String parent_page_id;
	
	private int retry;
	
	private EnumCSATaskStatus status;
	
	@Column(length = 1024)
	private String url;
	
	@Column(length = 1024)
	private String errlog;
	
	@Column(length = 64)
	private String update_date;
	
}
