package com.rcw.main;

import org.quartz.CronScheduleBuilder;
import org.quartz.CronTrigger;
import org.quartz.JobBuilder;
import org.quartz.JobDetail;
import org.quartz.Scheduler;
import org.quartz.SchedulerFactory;
import org.quartz.TriggerBuilder;
import org.quartz.impl.StdSchedulerFactory;

public class mainSchedule {

	public static void main(String[] args) {
		init();
	}

	public static void init() {
		System.out.println("开始茂名数据采集定时任务");
		try {
			JobDetail jobDetail = JobBuilder.newJob(mainJob.class).withIdentity("mainJob", "job-group").build();
			CronTrigger cronTrigger = TriggerBuilder.newTrigger().withIdentity("cronTrigger", "trigger-group").withSchedule(CronScheduleBuilder.cronSchedule("0 0 * * * ?")).build();
			SchedulerFactory sFactory = new StdSchedulerFactory();
			Scheduler scheduler = sFactory.getScheduler();
			scheduler.scheduleJob(jobDetail, cronTrigger);
			scheduler.start();
		} catch (Exception e) {
			System.out.println(e.getMessage());
		}
	}
}