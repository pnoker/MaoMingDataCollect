package com.rcw.util;

import java.sql.ResultSet;
import java.sql.SQLException;

public class AddDatas {

	public static int getTimes(String serial, int totalTimes) {
		Sqlserver sqlserver = new Sqlserver();
		int times = 0;
		String sql = "select * from add_data where serial = '" + serial + "'";
		try {
			ResultSet rs = sqlserver.executeQuery(sql);
			while (rs.next()) {
				times = rs.getInt("times");
			}
			sqlserver.free();
		} catch (SQLException e) {
			e.printStackTrace();
		}
		if (totalTimes >= times) {
			addTimes(serial, totalTimes);// 如果大于等于总次数，就一直保持该次数
		} else {
			addTimes(serial, times + 1);// 否则次数+1
		}

		return times;
	}

	public static void addTimes(String serial, int times) {
		Sqlserver sqlserver = new Sqlserver();
		String sql = "update add_data set times = " + times + " where serial = '" + serial + "'";
		try {
			sqlserver.executeUpdate(sql);
			sqlserver.free();
		} catch (SQLException e) {
			e.printStackTrace();
		}
	}
}
