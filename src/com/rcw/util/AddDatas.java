package com.rcw.util;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AddDatas {

	public static int getTimes(String serial, int totalTimes, LogWrite logWrite) {
		Sqlserver sqlserver = new Sqlserver();
		int times = 0;
		String sql = "select * from add_data where serial = '" + serial + "'";
		try {
			System.out.println(sql);
			ResultSet rs = sqlserver.executeQuery(sql);
			while (rs.next()) {
				times = rs.getInt("times");
			}
			sqlserver.free();
		} catch (SQLException e) {
			logWrite.write(e.getMessage());
		}
		if (times >= totalTimes) {
			addTimes(serial, totalTimes, logWrite);// 如果大于等于总次数，就一直保持该次数
		} else {
			addTimes(serial, times + 1, logWrite);// 否则次数+1
		}
		logWrite.write(times + "");
		return times;
	}

	public static void addTimes(String serial, int times, LogWrite logWrite) {
		Sqlserver sqlserver = new Sqlserver();
		String sql = "update add_data set times = " + times + " where serial = '" + serial + "'";
		try {
			sqlserver.executeUpdate(sql);
			logWrite.write(sql);
			sqlserver.free();
		} catch (SQLException e) {
			logWrite.write(e.getMessage());
		}
	}
}
