package com.rcw.util;

import java.util.Date;

/**
 * @author Pnoker
 * @description 打印工具类
 */
public class PrintUtil {
	private DateUtil date;

	public PrintUtil() {
		this.date = new DateUtil();
	}

	public void printDetail(String content) {
		System.out.println(content);
	}

	public void printMessage(String message) {
		System.out.println("<---- " + date.getCompleteTime(new Date()) + " ----> " + message);
	}
}
	