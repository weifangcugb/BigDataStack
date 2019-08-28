package org.training.spark.streaming;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.function.Function;
import org.apache.spark.api.java.function.VoidFunction;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka.HasOffsetRanges;
import org.apache.spark.streaming.kafka.KafkaCluster;
import org.apache.spark.streaming.kafka.KafkaUtils;
import org.apache.spark.streaming.kafka.OffsetRange;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import kafka.common.TopicAndPartition;
import kafka.message.MessageAndMetadata;
import kafka.serializer.StringDecoder;
import scala.Predef;
import scala.Tuple2;
import scala.collection.JavaConversions;

/**
 * spark streaming使用direct方式读取kafka数据，并存储每个partition读取的offset
 */
public final class JavaDirectKafkaWordCount {

    private static final Logger LOG = LoggerFactory.getLogger(JavaDirectKafkaWordCount.class);

    public static void main(String[] args) throws InterruptedException {

        if (args.length < 2) {
            System.err.println("Usage: JavaDirectKafkaWordCount <brokers> <topics>\n" +
                    "  <brokers> is a list of one or more Kafka brokers\n" +
                    "  <topics> is a list of one or more kafka topics to consume from\n\n");
            System.exit(1);
        }

        //StreamingExamples.setStreamingLogLevels();

        String brokers = args[0]; // kafka brokers
        String topics = args[1]; // 主题
        long seconds = 10; // 批次时间（单位：秒）

        // Create context with a 2 seconds batch interval
        SparkConf sparkConf = new SparkConf().setAppName("JavaDirectKafkaWordCount").setMaster("local[2]");
        JavaStreamingContext jssc = new JavaStreamingContext(sparkConf, Durations.seconds(seconds));
        jssc.sparkContext().setLogLevel("warn");

        // 设置kafkaParams
        HashSet<String> topicsSet = new HashSet<>(Arrays.asList(topics.split(",")));
        HashMap<String, String> kafkaParams = new HashMap<>();
        kafkaParams.put("metadata.broker.list", brokers);
        final String groupId = kafkaParams.get("group.id");

        // 创建kafka管理对象
        final KafkaCluster kafkaCluster = getKafkaCluster(kafkaParams);

        // 初始化offsets
        Map<TopicAndPartition, Long> fromOffsets = fromOffsets(topicsSet, kafkaParams, groupId, kafkaCluster, null);

        // 创建kafkaStream
        JavaInputDStream<String> stream = KafkaUtils.createDirectStream(jssc,
                String.class, String.class, StringDecoder.class,
                StringDecoder.class, String.class, kafkaParams,
                fromOffsets,
                new Function<MessageAndMetadata<String, String>, String>() {
                    /**
                     *
                     */
                    private static final long serialVersionUID = 1L;

                    public String call(MessageAndMetadata<String, String> v1)
                            throws Exception {
                        return v1.message();
                    }
                });


        // print
        stream.print();

        // 存储offsets
        storeConsumerOffsets(groupId, kafkaCluster, stream);

        // Start the computation
        jssc.start();
        jssc.awaitTermination();
    }

    /**
     * @param groupId      消费者 组id
     * @param kafkaCluster kafka管理对象
     * @param stream       kafkaStreamRdd
     */
    private static <T> void storeConsumerOffsets(final String groupId, final KafkaCluster kafkaCluster, JavaInputDStream<T> stream) {

        long l = System.currentTimeMillis();

        stream.foreachRDD(new VoidFunction<JavaRDD<T>>() {
            /**
             *
             */
            private static final long serialVersionUID = 1L;

            @Override
            public void call(JavaRDD<T> javaRDD) throws Exception {

                // 根据group.id 存储每个partition消费的位置
                OffsetRange[] offsets = ((HasOffsetRanges) javaRDD.rdd()).offsetRanges();
                for (OffsetRange o : offsets) {
                    // 封装topic.partition 与 offset对应关系 java Map
                    TopicAndPartition topicAndPartition = new TopicAndPartition(o.topic(), o.partition());
                    Map<TopicAndPartition, Object> topicAndPartitionObjectMap = new HashMap<>();
                    topicAndPartitionObjectMap.put(topicAndPartition, o.untilOffset());

                    // 转换java map to scala immutable.map
                    scala.collection.immutable.Map<TopicAndPartition, Object> scalaTopicAndPartitionObjectMap =
                            JavaConversions.mapAsScalaMap(topicAndPartitionObjectMap).toMap(new Predef.$less$colon$less<Tuple2<TopicAndPartition, Object>, Tuple2<TopicAndPartition, Object>>() {
                                /**
                                 *
                                 */
                                private static final long serialVersionUID = 1L;

                                public Tuple2<TopicAndPartition, Object> apply(Tuple2<TopicAndPartition, Object> v1) {
                                    return v1;
                                }
                            });

                    // 更新offset到kafkaCluster
                    kafkaCluster.setConsumerOffsets(groupId, scalaTopicAndPartitionObjectMap);
                }
            }
        });

        // 记录处理时间
        LOG.info("storeConsumerOffsets time:" + (System.currentTimeMillis() - l));
    }

    /**
     * 获取partition信息，并设置各分区的offsets
     *
     * @param topicsSet    所有topic
     * @param kafkaParams  kafka参数配置
     * @param groupId      消费者 组id
     * @param kafkaCluster kafka管理对象
     * @param offset       自定义offset
     * @return offsets
     */
    private static Map<TopicAndPartition, Long> fromOffsets(HashSet<String> topicsSet, HashMap<String, String> kafkaParams, String groupId, KafkaCluster kafkaCluster, Long offset) {

        long l = System.currentTimeMillis();

        // 所有partition offset
        Map<TopicAndPartition, Long> fromOffsets = new HashMap<>();

        // util.set 转 scala.set
        scala.collection.immutable.Set<String> immutableTopics = JavaConversions
                .asScalaSet(topicsSet)
                .toSet();

        // 获取topic分区信息
        scala.collection.immutable.Set<TopicAndPartition> scalaTopicAndPartitionSet = kafkaCluster
                .getPartitions(immutableTopics)
                .right()
                .get();

        if (offset != null || kafkaCluster.getConsumerOffsets(kafkaParams.get("group.id"),
                scalaTopicAndPartitionSet).isLeft()) {

            // 等于空则设置为0
            offset = (offset == null ? 0L : offset);

            // 设置每个分区的offset
            scala.collection.Iterator<TopicAndPartition> iterator = scalaTopicAndPartitionSet.iterator();
            while (iterator.hasNext()) {
                fromOffsets.put(iterator.next(), offset);
            }
        } else {
            // 往后继续读取
            scala.collection.Map<TopicAndPartition, Object> consumerOffsets = kafkaCluster
                    .getConsumerOffsets(groupId,
                            scalaTopicAndPartitionSet).right().get();

            scala.collection.Iterator<Tuple2<TopicAndPartition, Object>> iterator = consumerOffsets.iterator();
            while (iterator.hasNext()) {
                Tuple2<TopicAndPartition, Object> next = iterator.next();
                offset = (long) next._2();
                fromOffsets.put(next._1(), offset);
            }
        }

        // 记录处理时间
        LOG.info("fromOffsets time:" + (System.currentTimeMillis() - l));

        return fromOffsets;
    }

    /**
     * 将kafkaParams转换成scala map，用于创建kafkaCluster
     *
     * @param kafkaParams kafka参数配置
     * @return kafkaCluster管理工具类
     */
    private static KafkaCluster getKafkaCluster(HashMap<String, String> kafkaParams) {
        // 类型转换
        scala.collection.immutable.Map<String, String> immutableKafkaParam = JavaConversions
                .mapAsScalaMap(kafkaParams)
                .toMap(new Predef.$less$colon$less<Tuple2<String, String>, Tuple2<String, String>>() {
                    /**
                     *
                     */
                    private static final long serialVersionUID = 1L;

                    public Tuple2<String, String> apply(
                            Tuple2<String, String> v1) {
                        return v1;
                    }
                });

        return new KafkaCluster(immutableKafkaParam);
    }
}
