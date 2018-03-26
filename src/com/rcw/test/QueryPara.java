package com.rcw.test;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.Date;

import com.rcw.main.MainFunction;
import com.rcw.pojo.BaseInfo;
import com.rcw.pojo.Result;
import com.rcw.util.LogWrite;
import com.rcw.util.PackageProcessor;

public class QueryPara {
	private DatagramPacket datagramReceive;
	private byte[] buf = new byte[1024];
	private PackageProcessor p;
	private LogWrite logWrite;
	private int num;

	/**
	 * 初始化构造函数
	 */
	public QueryPara() {
		try {
			this.datagramReceive = new DatagramPacket(buf, 1024);
			this.logWrite = new LogWrite();
		} catch (Exception e) {
			logWrite.write(e.getMessage());
		}
	}

	/**
	 * 打印十六进制的报文，不足两位，前面补零，使用正则表达式格式化报文
	 */
	public String getHexDatagram(byte[] b, int length) {
		StringBuffer sbuf = new StringBuffer();
		for (int i = 0; i < length; i++) {
			String hex = Integer.toHexString(b[i] & 0xFF);
			/* 不足两位前面补零处理 */
			if (hex.length() == 1) {
				hex = '0' + hex;
			}
			String regex = "(.{2})";
			hex = hex.replaceAll(regex, "$1 ");
			sbuf.append(hex.toUpperCase());
		}
		return sbuf.toString();
	}

	/**
	 * 正响应解析
	 */

	public Result data(PackageProcessor p, String typeserial) {
		Result dataResult = new Result();
		dataResult.setSuccess(false);
		String longAddress = MainFunction.item.get(typeserial).split("#")[0];
		if (longAddress.equals("E119000000417A00")) {// 长地址唯一性校验
			String cz = MainFunction.item.get(typeserial).split("#")[4];// 设备的从站地址
			String md = MainFunction.item.get(typeserial).split("#")[5];// Modbus命令号
			if (p.bytesToString(3, 13).toUpperCase().equals(longAddress + "01" + cz + md)) {
				float value1 = (float) (p.bytesToLong(17, 20) * 0.0000001);
				int value2 = (int) (p.bytesToLong(21, 24) * 0.1);
				logWrite.write("IO的瞬时值为:" + value1);
				logWrite.write("IO的累计值为:" + value2);
				dataResult.setSuccess(true);
				dataResult.setInstant(value1);
				dataResult.setTotal(value2);
			}
		} else if (longAddress.toLowerCase().equals(p.bytesToString(3, 10))) {// 长地址唯一性校验
			if (p.bytesToString(11, 12).equals("1505")) {// 水表流量值
				float value = p.bytesToFloatSmall(13, 16);
				logWrite.write("水表流量值为:" + value + "M³");
				dataResult.setSuccess(true);
				dataResult.setTotal(value);
			}
		}
		return dataResult;
	}

	/**
	 * 负响应解析
	 */
	public void fail(PackageProcessor p) {
		if (p.bytesToString(3, 3).equals("01")) {
			logWrite.write("未知错误");
		}
		if (p.bytesToString(3, 3).equals("02")) {
			logWrite.write("输入长度有问题");
		}
		if (p.bytesToString(3, 3).equals("03")) {
			logWrite.write("不支持的命令号");
		}
		if (p.bytesToString(3, 3).equals("04")) {
			logWrite.write("设备不在网");
		}
		if (p.bytesToString(3, 3).equals("05")) {
			logWrite.write("串口号错误");
		}
		if (p.bytesToString(3, 3).equals("06")) {
			logWrite.write("数据不合理");
		}
		if (p.bytesToString(3, 3).equals("07")) {
			logWrite.write("地址不成对");
		}
		if (p.bytesToString(3, 3).equals("08")) {
			logWrite.write("写入Modbus映射表地址索引不连续");
		}
		if (p.bytesToString(3, 3).equals("09")) {
			logWrite.write("写入Modbus地址溢出");
		}
		if (p.bytesToString(3, 3).equals("10")) {
			logWrite.write("UDP端口重复");
		}
		if (p.bytesToString(3, 3).equals("11")) {
			logWrite.write("命令号不存在");
		}
	}

	public Result query(BaseInfo base, byte[] send, String typeserial) {
		Result dataResult = new Result();
		boolean timeOut = false;
		boolean reConnect = true;
		logWrite.write("<---当前网关:" + base.getIpaddress() + "--->");
		DatagramSocket datagramSocket = null;
		long start = 0, end = 0;
		float total = 0, instant = 0;
		dataResult.setSuccess(true);
		DatagramPacket datagramSend = null;
		try {
			datagramSocket = new DatagramSocket(base.getLocalport());
			datagramSend = new DatagramPacket(send, send.length, InetAddress.getByName(base.getIpaddress()),
					base.getPort());
			num = 0;// 计数清零
			start = (new Date()).getTime();
		} catch (IOException e) {
			logWrite.write(e.getMessage());
		}
		while (reConnect) {
			if (num >= 3) {// 3次重连机会，超过3次就退出重连机制
				reConnect = false;
				logWrite.write("本次查询失败");
				dataResult.setSuccess(false);// 接收3次超时
			} else {
				try {
					datagramSocket.send(datagramSend);
					logWrite.write("发送:" + getHexDatagram(send, send.length));
					num++;// 计数+1
					timeOut = false;
				} catch (IOException e) {
					logWrite.write(e.getMessage());
				}
			}
			while (!timeOut) {
				try {
					datagramSocket.setSoTimeout(1000 * 10);
					datagramSocket.receive(datagramReceive);
					byte[] receive = datagramReceive.getData();
					p = new PackageProcessor(receive);
					String hexDatagram = getHexDatagram(datagramReceive.getData(), datagramReceive.getLength());
					String datastart = p.bytesToString(0, 2);
					Result data = new Result();
					switch (datastart) {
					/* 远程读取 水表 返回的正响应 或者是负响应二 */
					case "026a00":
						logWrite.write("水表应用数据，成功:" + hexDatagram);
						data = data(p, typeserial);
						if (data.isSuccess()) {
							total = data.getTotal();// 累计量
							instant = data.getInstant();// 瞬时量
							timeOut = true;
							reConnect = false;
						}
						end = (new Date()).getTime();
						if ((end - start) > 8000) {
							timeOut = true;
							reConnect = false;
							dataResult.setSuccess(false);// 无用数据接收超时
						}
						break;
					/* 远程读取 IO 返回的正响应 */
					case "025500":
						logWrite.write("IO应用数据,成功:" + hexDatagram);
						data = data(p, typeserial);
						if (data.isSuccess()) {
							total = data.getTotal();// 累计量
							instant = data.getInstant();// 瞬时量
							timeOut = true;
							reConnect = false;
						}
						end = (new Date()).getTime();
						if ((end - start) > 8000) {
							timeOut = true;
							reConnect = false;
							dataResult.setSuccess(false);// 无用数据接收超时
						}
						break;
					/* 负响应 一 */
					case "026980":
						logWrite.write("错误:" + hexDatagram);
						dataResult.setSuccess(false);// 操作失败
						fail(p);
						timeOut = true;
						reConnect = false;
						break;
					/* 网关连接成功 */
					case "020f80":
						logWrite.write("网关连接成功:" + hexDatagram);
						timeOut = true;
						reConnect = false;
						break;
					default:
						logWrite.write("其他:" + hexDatagram);
						end = (new Date()).getTime();
						if ((end - start) > 10000) {
							timeOut = true;
							reConnect = false;
						}
					}
				} catch (Exception e) {
					logWrite.write(e.getMessage());
					timeOut = true;
				}
			}
		}
		datagramReceive.setLength(1024);
		datagramSocket.close();
		end = (new Date()).getTime();
		System.out.println("本次操作耗时:" + (end - start) + "ms");
		dataResult.setInstant(instant);
		dataResult.setTotal(total);
		return dataResult;
	}
}
