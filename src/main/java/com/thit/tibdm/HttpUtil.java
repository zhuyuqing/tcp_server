package com.thit.tibdm;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.kairosdb.client.HttpClient;
import org.kairosdb.client.builder.MetricBuilder;
import org.kairosdb.client.builder.QueryBuilder;
import org.kairosdb.client.builder.RollupTask;
import org.kairosdb.client.response.QueryResponse;
import org.kairosdb.client.response.RollupResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 发送数据到kairosdb
 *
 * @author wanghaoqiang
 */
public class HttpUtil {

    /**
     * 日志
     */
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpUtil.class);

    //private static AtomicInteger packetCnt = new AtomicInteger(0);

    /**
     * 静态内部类单例
     */
    private static class SingletonHolder {
        private static final HttpUtil INSTANCE = new HttpUtil();
    }

    /**
     * 获取单例对象
     * @return
     */
    public static HttpUtil getInstance() {
        return SingletonHolder.INSTANCE;
    }

    /**
     * 获取客户端列表
     */
    private List<HttpClient> clients = Lists.newArrayList();
    /**
     * 客户端类
     */
    private HttpClient client;
    /**
     * 自增类
     */
    private AtomicInteger index = new AtomicInteger();

    private static Boolean ikrOnly = Config.I.getIkrOnly();

    /**
     * 客户端负载均衡方法
     * @return
     */
    public HttpClient getClient() {
        int listIndex = index.incrementAndGet() % clients.size();
        return clients.get(listIndex);
    }

    /**
     * 获取客户端列表
     */
    private List<HttpClient> ikrClients = Lists.newArrayList();
    /**
     * IKR的客户端
     */
    private HttpClient ikrClient;
    /**
     * 自增类
     */
    private AtomicInteger indexIkr = new AtomicInteger();

    /**
     * 客户端负载均衡方法
     * @return
     */
    public HttpClient getIkrClient(){
        int listIndex = indexIkr.incrementAndGet() % ikrClients.size();
        return ikrClients.get(listIndex);
    }

    /**
     * 私有构造类
     */
    private HttpUtil() {
        LOGGER.info(Config.I.getKairosdbUrlList().toString());
        LOGGER.info(Config.I.getIkrUrl().toString());
        Config.I.getKairosdbUrlList().forEach(http -> {
            try {
                client = new HttpClient(http);
                client.setMaxTotal(128);
                client.setTimeout(Config.I.getTimeout());
            } catch (MalformedURLException e) {
                LOGGER.error("发生异常{}", e);
            }
            clients.add(client);
        });
        Config.I.getIkrUrl().forEach(http -> {
            try {
                ikrClient = new HttpClient(http);
                ikrClient.setMaxTotal(128);
                ikrClient.setTimeout(Config.I.getTimeout());
            } catch (MalformedURLException e) {
                LOGGER.error("发生异常{}", e);
            }
            ikrClients.add(ikrClient);
        });
    }

    /**
     * 发送数据写入请求
     * @param builder
     */
    public static void sendKairosdb(MetricBuilder builder) {
        if (ikrOnly){
            try {
                getInstance().getIkrClient().pushMetrics(builder);
            } catch (Exception e) {
                LOGGER.error("写入Ikr发生异常{}", e);
            }
        }
        else {
            try {
                getInstance().getClient().pushMetrics(builder);
            } catch (Exception e) {
                LOGGER.error("写入kairosDB发生异常{}", e);
            }
            try {
                getInstance().getIkrClient().pushMetrics(builder);
            } catch (Exception e) {
                LOGGER.error("写入Ikr发生异常{}", e);
            }
        }
    }

    /**
     * 发送数据查询请求
     * @param builder
     * @return
     */
    public static QueryResponse sendQuery(QueryBuilder builder) {
        QueryResponse response = null;
        try {
            response = getInstance().getClient().query(builder);
        } catch (IOException e) {
            try {
                response = getInstance().getIkrClient().query(builder);
            } catch (IOException e1) {
                LOGGER.error("发生异常{}", e1);
            }
        }
        return response;
    }

    public static ImmutableList<RollupTask> getrollupTasks(){
        RollupResponse rollupTasks=null;
        ImmutableList<RollupTask> tasks =null;
        try {
             rollupTasks = getInstance().getClient().getRollupTasks();
             tasks = rollupTasks.getRollupTasks();
        } catch (IOException e) {
            try{
                rollupTasks = getInstance().getIkrClient().getRollupTasks();
                tasks = rollupTasks.getRollupTasks();
            } catch (IOException e1) {
                LOGGER.error("{}",e1);
            }
        }
        return tasks;
    }
}
