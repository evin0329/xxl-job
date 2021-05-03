package com.xxl.job.core.thread;

import com.xxl.job.common.model.HandleCallbackParam;
import com.xxl.job.common.model.ReturnT;
import com.xxl.job.common.model.TriggerParam;
import com.xxl.job.core.executor.XxlJobExecutor;
import com.xxl.job.common.handler.IJobHandler;
import com.xxl.job.core.log.XxlJobFileAppender;
import com.xxl.job.core.log.XxlJobLogger;
import com.xxl.job.core.util.ShardingUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;


/**
 * handler thread
 *
 * @author xuxueli 2016-1-16 19:52:47
 */
public class JobThread extends Thread {
    private static Logger logger = LoggerFactory.getLogger(JobThread.class);

    private int jobId;
    private IJobHandler handler;
    private LinkedBlockingQueue<TriggerParam> triggerQueue;
    private Set<Long> triggerLogIdSet;        // avoid repeat trigger for the same TRIGGER_LOG_ID

    private volatile boolean toStop = false;
    private String stopReason;

    private boolean running = false;    // if running job
    private int idleTimes = 0;            // idel times (空闲时间)


    public JobThread(int jobId, IJobHandler handler) {
        this.jobId = jobId;
        this.handler = handler;
        this.triggerQueue = new LinkedBlockingQueue<TriggerParam>();
        this.triggerLogIdSet = Collections.synchronizedSet(new HashSet<Long>());
    }

    public IJobHandler getHandler() {
        return handler;
    }

    /**
     * new trigger to queue
     *
     * @param triggerParam
     * @return
     */
    public ReturnT<String> pushTriggerQueue(TriggerParam triggerParam) {
        // avoid repeat
        if (triggerLogIdSet.contains(triggerParam.getLogId())) {
            logger.info(">>>>>>>>>>> repeate trigger job, logId:{}", triggerParam.getLogId());
            return new ReturnT<String>(ReturnT.FAIL_CODE, "repeate trigger job, logId:" + triggerParam.getLogId());
        }

        triggerLogIdSet.add(triggerParam.getLogId());
        triggerQueue.add(triggerParam);
        return ReturnT.SUCCESS;
    }

    /**
     * kill job thread
     *
     * @param stopReason
     */
    public void toStop(String stopReason) {
        /**
         * Thread.interrupt只支持终止线程的阻塞状态(wait、join、sleep)，
         * 在阻塞出抛出InterruptedException异常,但是并不会终止运行的线程本身；
         * 所以需要注意，此处彻底销毁本线程，需要通过共享变量方式；
         */
        this.toStop = true;
        this.stopReason = stopReason;
    }

    /**
     * is running job
     *
     * @return
     */
    public boolean isRunningOrHasQueue() {
        return running || triggerQueue.size() > 0;
    }

    @Override
    public void run() {

        // init
        // 调用handle初始化方法
        try {
            handler.init();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        // execute
        // 执行
        while (!toStop) {
            //
            running = false;
            idleTimes++;

            TriggerParam triggerParam = null;
            ReturnT<String> executeResult = null;
            try {
                // to check toStop signal, we need cycle, so wo cannot use queue.take(), instand of poll(timeout)
                // 轮询是否有新的任务
                triggerParam = triggerQueue.poll(3L, TimeUnit.SECONDS);
                // 是否有新任务
                if (triggerParam != null) {
                    running = true;                                         // 标记正在运行
                    idleTimes = 0;                                          // 重置空闲时间
                    triggerLogIdSet.remove(triggerParam.getLogId());

                    // log filename, like "logPath/yyyy-MM-dd/9999.log"
                    // 日志文件路径
                    String logFileName = XxlJobFileAppender.makeLogFileName(new Date(triggerParam.getLogDateTime()), triggerParam.getLogId());
                    XxlJobFileAppender.contextHolder.set(logFileName);
                    // 分片contextHolder
                    ShardingUtil.setShardingVo(new ShardingUtil.ShardingVO(triggerParam.getBroadcastIndex(), triggerParam.getBroadcastTotal()));

                    // execute
                    XxlJobLogger.log("<br>----------- xxl-job job execute start -----------<br>----------- Param:" + triggerParam.getExecutorParams());

                    if (triggerParam.getExecutorTimeout() > 0) {
                        // limit timeout
                        Thread futureThread = null;
                        try {
                            final TriggerParam triggerParamTmp = triggerParam;
                            FutureTask<ReturnT<String>> futureTask = new FutureTask<ReturnT<String>>(new Callable<ReturnT<String>>() {
                                @Override
                                public ReturnT<String> call() throws Exception {
                                    return handler.execute(triggerParamTmp.getExecutorParams());
                                }
                            });
                            futureThread = new Thread(futureTask);
                            futureThread.start();

                            // 超时返回
                            executeResult = futureTask.get(triggerParam.getExecutorTimeout(), TimeUnit.SECONDS);
                        } catch (TimeoutException e) {

                            XxlJobLogger.log("<br>----------- xxl-job job execute timeout");
                            XxlJobLogger.log(e);

                            executeResult = new ReturnT<String>(IJobHandler.FAIL_TIMEOUT.getCode(), "job execute timeout ");
                        } finally {
                            futureThread.interrupt();
                        }
                    }
                    // 只是执行任务 不做超时处理
                    else {
                        // just execute
                        executeResult = handler.execute(triggerParam.getExecutorParams());
                    }

                    // 执行结果如果为空，则返回失败
                    if (executeResult == null) {
                        executeResult = IJobHandler.FAIL;
                    } else {
                        // 设置执行结果的消息
                        executeResult.setMsg(
                                (executeResult != null && executeResult.getMsg() != null && executeResult.getMsg().length() > 50000)
                                        ? executeResult.getMsg().substring(0, 50000).concat("...")
                                        : executeResult.getMsg());
                        executeResult.setContent(null);    // limit obj size
                    }
                    XxlJobLogger.log("<br>----------- xxl-job job execute end(finish) -----------<br>----------- ReturnT:" + executeResult);

                }
                else {
                    // 如果空闲次数超过30并且队列中又没有新任务，则删除当前任务线程
                    if (idleTimes > 30) {
                        if (triggerQueue.size() == 0) {    // avoid concurrent trigger causes jobId-lost
                            XxlJobExecutor.removeJobThread(jobId, "excutor idel times over limit.");
                        }
                    }
                }
            } catch (Throwable e) {
                if (toStop) {
                    XxlJobLogger.log("<br>----------- JobThread toStop, stopReason:" + stopReason);
                }

                StringWriter stringWriter = new StringWriter();
                e.printStackTrace(new PrintWriter(stringWriter));
                String errorMsg = stringWriter.toString();
                executeResult = new ReturnT<String>(ReturnT.FAIL_CODE, errorMsg);

                XxlJobLogger.log("<br>----------- JobThread Exception:" + errorMsg + "<br>----------- xxl-job job execute end(error) -----------");
            } finally {
                if (triggerParam != null) {
                    // callback handler info
                    if (!toStop) {
                        // commonm
                        // 推送执行结果
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(), triggerParam.getLogDateTime(), executeResult));
                    } else {
                        // is killed
                        // 线程停止
                        ReturnT<String> stopResult = new ReturnT<String>(ReturnT.FAIL_CODE, stopReason + " [job running, killed]");
                        TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(), triggerParam.getLogDateTime(), stopResult));
                    }
                }
            }
        }

        // ------------------------ 任务停止 ------------------------

        // callback trigger request in queue
        while (triggerQueue != null && triggerQueue.size() > 0) {
            TriggerParam triggerParam = triggerQueue.poll();
            if (triggerParam != null) {
                // is killed
                ReturnT<String> stopResult = new ReturnT<String>(ReturnT.FAIL_CODE, stopReason + " [job not executed, in the job queue, killed.]");
                TriggerCallbackThread.pushCallBack(new HandleCallbackParam(triggerParam.getLogId(), triggerParam.getLogDateTime(), stopResult));
            }
        }

        // destroy
        // 调用handle销毁方法
        try {
            handler.destroy();
        } catch (Throwable e) {
            logger.error(e.getMessage(), e);
        }

        logger.info(">>>>>>>>>>> xxl-job JobThread stoped, hashCode:{}", Thread.currentThread());
    }
}
