package cn.wells.csa.mtv.utils;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class ProcRelativePath {
	
	public static void main(String[] args) {
		List<File> fileList = (List<File>) FileUtils.listFiles(new File("/Users/mmao/mmVolume/Library/Confluence-邮储新核心/"), new String[]{"html"}, true);
		
		fileList.stream().forEach(file -> modifyFileContent(file, "/Users/mmao/mmVolume/Documents/work-长亮科技/7.平台参考资料/Confluence-邮储新核心/", "../"));
	}
	
	/**
	 * 修改文件内容：字符串逐行替换
	 *
	 * @param file：待处理的文件
	 * @param oldstr：需要替换的旧字符串
	 * @param newStr：用于替换的新字符串
	 */
	public static boolean modifyFileContent(File file, String oldstr, String newStr) {
		
		if (!file.getName().equalsIgnoreCase("index.html")) {
			System.out.println("fail on file : " + file.getAbsoluteFile());
			return false;
		}
		
		System.out.println("proc file : " + file.getAbsoluteFile());
		//		if (true) {
		//			return false;
		//		}
		
		List<String> list = null;
		try {
			list = FileUtils.readLines(file, "UTF-8");
			for (int i = 0; i < list.size(); i++) {
				if (list.get(i).indexOf(oldstr) != -1) {
					//					System.out.println(file.getName());
					//					System.out.println(list.get(i));
					String temp = list.get(i).replaceAll(oldstr, newStr);
					list.remove(i);
					list.add(i, temp);
				}
			}
			FileUtils.writeLines(file, "UTF-8", list, false);
		} catch (IOException e) {
			e.printStackTrace();
		}
		return true;
	}
}
