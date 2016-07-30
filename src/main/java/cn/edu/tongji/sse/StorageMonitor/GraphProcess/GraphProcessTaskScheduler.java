package cn.edu.tongji.sse.StorageMonitor.GraphProcess;

import cn.edu.tongji.sse.StorageMonitor.Config;
import cn.edu.tongji.sse.StorageMonitor.GraphDataSource.GraphDataSet;
import cn.edu.tongji.sse.StorageMonitor.GraphDataSource.Neo4j.Neo4jGraphDataSet;
import cn.edu.tongji.sse.StorageMonitor.Utils;
import cn.edu.tongji.sse.StorageMonitor.model.AlgorithmResultMessage;
import cn.edu.tongji.sse.StorageMonitor.model.AlgorithmTaskMessage;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;

import java.io.*;
import java.util.*;

/**
 * Created by hahong on 2016/7/30.
 */
public class GraphProcessTaskScheduler {
    public static GraphDataSet getGraphDataSource(AlgorithmTaskMessage msg) {
        GraphDataSet dataset = new Neo4jGraphDataSet("http://10.60.45.79:7474", "Basic bmVvNGo6MTIzNDU2");
        return dataset;
    }
    public static AlgorithmTask getAlgorithmTask(AlgorithmTaskMessage msg) {
        return new PageRank();
    }
    static Producer<String, byte[]> resultProducer;
    static KafkaConsumer<String, byte[]> taskConsumer;
    static void init() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, Config.KafkaAddr);

        props.put("acks", "all");
        props.put("retries", 0);
        props.put("batch.size", 1);
        props.put("linger.ms", 1);
        props.put("buffer.memory", 33554432);

        props.put("key.serializer", "org.apache.kafka.common.serialization.StringSerializer");
        props.put("value.serializer", "org.apache.kafka.common.serialization.ByteArraySerializer");
        resultProducer = new KafkaProducer<String, byte[]>(props);

        Properties prop = new Properties();
        prop.put("bootstrap.servers", Config.KafkaAddr);
        prop.put("group.id", "test");
        prop.put("enable.auto.commit", "true");
        prop.put("auto.commit.interval.ms", "1000");
        prop.put("session.timeout.ms", "30000");
        prop.put("key.deserializer", "org.apache.kafka.common.serialization.StringDeserializer");
        prop.put("value.deserializer", "org.apache.kafka.common.serialization.ByteArrayDeserializer");
        taskConsumer = new KafkaConsumer<String, byte[]>(prop);
    }
    static Properties readConfig() {
        Properties config = new Properties();
        InputStream input = null;

        try {
            input = new FileInputStream("config.properties");
            config.load(input);
            return config;

        } catch (IOException ex) {
            ex.printStackTrace();
        } finally {
            if (input != null) {
                try {
                    input.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    public static void main(String args[]) {
        Properties config = readConfig();
        init();
        List<String> algorithms = new ArrayList<>();
        algorithms.addAll(Arrays.asList(config.getProperty("algorithm").split(",")));
        taskConsumer.subscribe(algorithms);


        while (true) {
            ConsumerRecords<String, byte[]> records = taskConsumer.poll(10000);
            for (ConsumerRecord<String, byte[]> record : records) {
                AlgorithmTaskMessage taskMsg = Utils.readKryoObject(AlgorithmTaskMessage.class, record.value());
                GraphDataSet dataset = getGraphDataSource(taskMsg);
                AlgorithmTask task = getAlgorithmTask(taskMsg);
                long startTime = System.currentTimeMillis();
                task.run(dataset);
                long endTime = System.currentTimeMillis();
                AlgorithmResultMessage resultMsg = new AlgorithmResultMessage();
                resultMsg.setTaskId(taskMsg.getTaskId());
                resultMsg.setAlgorithm(taskMsg.getAlgorithm());
                resultMsg.setCreateTime(taskMsg.getCreateTime());
                resultMsg.setStartTime(startTime);
                resultMsg.setEndTime(endTime);
                resultMsg.setParameters(taskMsg.getParameters());
                resultMsg.setStorageEndpoint(taskMsg.getStorageEndpoint());
                byte[] bytes = Utils.writeKryoObject(resultMsg);
                resultProducer.send(new ProducerRecord<String, byte[]>(Config.KafkaAlgorithmResultTopic, "", bytes));
            }
        }

    }
}
