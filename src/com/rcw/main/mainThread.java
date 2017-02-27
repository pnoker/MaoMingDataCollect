package com.rcw.main;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

import com.rcw.pojo.BaseInfo;
import com.rcw.pojo.Result;
import com.rcw.test.QueryPara;
import com.rcw.util.ExcutePro;
import com.rcw.util.Generation;
import com.rcw.util.LogWrite;
import com.rcw.util.Sqlserver;

public class mainThread {
	SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
	LogWrite logWrite = new LogWrite();

	/**
	 * 读取配置文件到内存
	 */
	public void initParameter() {
		try {
			MainFunction.code = ExcutePro.getProperties("command.properties");
			MainFunction.item = ExcutePro.getProperties("typeserial.properties");
		} catch (Exception e) {
			logWrite.write(e.getMessage());
		}
	}

	public float query(String typeserial, int serial, String table, String reachtime) {
		Sqlserver connect = new Sqlserver();
		QueryPara queryPara = new QueryPara();
		Generation generation = new Generation();
		Result dataResult = new Result();
		byte[] connectCode = generation.connect();// 连接网关的命令，固定写法
		byte[] sendCode = generation.queryCommand(typeserial, serial);// 查询的命令
		BaseInfo base = new BaseInfo();
		base.setIpaddress(MainFunction.item.get(typeserial).split("#")[1]);
		base.setLocalport(Integer.parseInt(MainFunction.item.get(typeserial).split("#")[2]));
		base.setPort(6001);
		queryPara.query(base, connectCode, typeserial);// 连接网关
		dataResult = queryPara.query(base, sendCode, typeserial);// 开始查询，并返回查询结果
		float total = dataResult.getTotal();// 累计值
		float flow = dataResult.getInstant();// 瞬时值
		String info = MainFunction.item.get(typeserial).split("#")[3];// 数据库中存放的位号，例：sia0001
		if (dataResult.isSuccess()) {
			int tag = 0;
			if (typeserial.equals("wxio001") || typeserial.equals("wxio002")) {
				tag = 1;// 累计
			}
			String sql = "select top 1 * from " + table + " where typeserial = '" + typeserial + "' and tag = " + tag
					+ " order by reachtime desc";
			try {
				ResultSet rs = connect.executeQuery(sql);
				float value = rs.getFloat("value");
				if (total < value) {// 累计值校验，累计值不可能比上一次小
					total = value;
				}
			} catch (SQLException e) {
				System.out.println(e.getMessage());
			}

			insert(table, info, total, reachtime);// 向数据库中只插入累计量信息，web页面显示的瞬时信息是后期计算得出来的
			if (info.contains("sia")) {
				updataOpc(total, info, reachtime);
			}
			if (info.contains("wxio")) {
				updataOpc(total, flow, info, reachtime);
			}
		}
		return total;
	}

	/**
	 * 将查询的数据insert到历史表中
	 */
	public void insert(String table, String typeserial, float value, String reachtime) {
		Sqlserver connect = new Sqlserver();
		int tag = 0;
		if (typeserial.equals("wxio001") || typeserial.equals("wxio002")) {
			tag = 1;// 累计
		}
		String sql = "insert into " + table + "(typeserial,tag, value,reachtime)values('" + typeserial + "'," + tag
				+ "," + value + ",'" + reachtime + "')";
		try {
			connect.executeUpdate(sql);
			logWrite.write(sql);
			connect.free();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * 更新水表的实时累计值，通过计算得出水表的瞬时值
	 */
	public void updataOpc(float total, String shuiInfo, String reachtim) {
		Sqlserver connect = new Sqlserver();
		String sente = "update [shui_opc] set value = " + total + ",reachtime = '" + reachtim + "' where typeserial = '"
				+ shuiInfo + "_bt' and tag = 0";
		try {
			connect.executeUpdate(sente);// 更新表头值
			logWrite.write(sente);
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		if (shuiInfo.equals("sia0001")) {
			total += 42959;
		} else if (shuiInfo.equals("sia0002")) {
			total += 44165;
		} else if (shuiInfo.equals("sia0003")) {
			total += 1696.7;
		} else if (shuiInfo.equals("sia0004")) {
			total += 1821;
		} else if (shuiInfo.equals("sia0005")) {
			total += 357.6;
		} else if (shuiInfo.equals("sia0006")) {
			total += 3280;
		} else if (shuiInfo.equals("sia0007")) {
			total += 608;
		} else if (shuiInfo.equals("sia0008")) {
			total += 0;
		} else if (shuiInfo.equals("sia0009")) {
			total += 0;
		}

		sente = "with table1 as(select DATEDIFF(HOUR,reachtime,'" + reachtim
				+ "') as hours,value from [shui_opc] where typeserial = '" + shuiInfo + "') ";
		sente += "select (" + total + "-table1.value)/table1.hours  from table1";
		float value = 0;// 默认瞬时值为0
		try {
			ResultSet rs = connect.executeQuery(sente);// 计算瞬时值
			logWrite.write(sente);
			value = rs.getFloat(0);
			if (value >= 30 || value < 0) {// 规范化瞬时量
				value = 0;
			}
			sente = "update [shui_opc] set value = " + value + " ,reachtime = '" + reachtim + "' where typeserial = '"
					+ shuiInfo + "_0' and tag = 0";
			connect.executeUpdate(sente);// 更新瞬时量
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}

		sente = "update [shui_opc] set value = " + total + ",reachtime = '" + reachtim + "' where typeserial = '"
				+ shuiInfo + "' and tag = 0";
		try {
			connect.executeUpdate(sente);// 更新累计值，表头值+初始值
			logWrite.write(sente);
			connect.free();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}

	/**
	 * 更新两块IO表的实时累计值和瞬时值
	 */
	public void updataOpc(float total, float flow, String info, String reachtim) {
		Sqlserver connect = new Sqlserver();
		String sente = "update [shui_opc] set value = " + total + ",reachtime = '" + reachtim
				+ "' where typeserial =  '" + info + "_1'";
		try {
			connect.executeUpdate(sente);
			logWrite.write(sente);
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
		sente = "update [shui_opc] set value = " + flow + ",reachtime = '" + reachtim + "' where typeserial =  '" + info
				+ "_0'";
		try {
			connect.executeUpdate(sente);
			logWrite.write(sente);
			connect.free();
		} catch (SQLException e) {
			System.out.println(e.getMessage());
		}
	}

	public void startMainThread() throws Exception {
		System.out.println("<---开始查询数据--->");
		initParameter();
		String reachtime = sdf.format(new Date());
		query("IMTAG.JL-393023", 1, "shui_data", reachtime);
		query("IMTAG.JL-393136", 1, "shui_data", reachtime);
		query("IMTAG.JL-393026", 1, "shui_data", reachtime);
		query("IMTAG.JL-393025", 1, "shui_data", reachtime);
		query("IMTAG.JL-393027", 1, "shui_data", reachtime);
		query("IMTAG.JL-393028", 1, "shui_data", reachtime);
		query("IMTAG.JL-393143", 1, "shui_data", reachtime);
		query("IMTAG.JL-393144", 1, "shui_data", reachtime);
		query("IMTAG.JL-393024", 1, "shui_data", reachtime);
		query("IMTAG.JL-390004", 13, "xiaofangbengzhan_data", reachtime);
		query("IMTAG.JL-390005", 13, "xiaofangbengzhan_data", reachtime);
		System.out.println("<---结束查询数据--->");
	}

	public void tool() throws Exception {
		System.out.println("<---开始查询数据--->");
		initParameter();
		Calendar calendar = Calendar.getInstance();
		calendar.set(2017, 1, 27, 11, 0, 0);//可自定义设置时间
		Date date = calendar.getTime();
		String reachtime = sdf.format(date);
		query("IMTAG.JL-393023", 1, "shui_data", reachtime);
		query("IMTAG.JL-393136", 1, "shui_data", reachtime);
		query("IMTAG.JL-393026", 1, "shui_data", reachtime);
		query("IMTAG.JL-393025", 1, "shui_data", reachtime);
		query("IMTAG.JL-393027", 1, "shui_data", reachtime);
		query("IMTAG.JL-393028", 1, "shui_data", reachtime);
		query("IMTAG.JL-393143", 1, "shui_data", reachtime);
		query("IMTAG.JL-393144", 1, "shui_data", reachtime);
		query("IMTAG.JL-393024", 1, "shui_data", reachtime);
		query("IMTAG.JL-390004", 13, "xiaofangbengzhan_data", reachtime);
		query("IMTAG.JL-390005", 13, "xiaofangbengzhan_data", reachtime);
		System.out.println("<---结束查询数据--->");
	}

	public static void main(String[] args) {
		mainThread mainThread = new mainThread();
		try {
			mainThread.tool();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
