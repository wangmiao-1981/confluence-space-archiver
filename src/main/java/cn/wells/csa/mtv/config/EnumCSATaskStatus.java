package cn.wells.csa.mtv.config;

/**
 * 任务状态 READY -> COMMIT -> PROC -> ERROR -> DONE
 */
public enum EnumCSATaskStatus {
	READY(0),
	COMMIT(1),
	PROC(2),
	ERROR(3),
	DONE(4);
	
	private int id;
	
	// 构造方法
	private EnumCSATaskStatus(int index) {
		this.id = index;
	}
}
