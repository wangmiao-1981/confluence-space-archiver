package cn.wells.csa.mtv.repository;

import cn.wells.csa.mtv.dto.DTO_Page;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface DAO_Page extends JpaRepository<DTO_Page, String> {
	
	//定时任务，取ready和还有retry的error，提交到async队列去排队执行
	@Query(value = "select * from cspace_page where status=0 union select * from cspace_page where status=3 and retry>0", nativeQuery = true)
	List<DTO_Page> getReadyTasks();
	
	@Query(value = "select * from cspace_page where parent_page_id=?1 order by id", nativeQuery = true)
	List<DTO_Page> getPagesByParentid(String pageid);
}
