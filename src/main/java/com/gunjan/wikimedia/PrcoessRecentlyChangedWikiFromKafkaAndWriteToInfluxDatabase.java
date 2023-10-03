package com.gunjan.wikimedia;

import com.influxdb.client.domain.WritePrecision;
import com.influxdb.client.write.Point;
import org.apache.flink.api.common.functions.AggregateFunction;
import org.apache.flink.api.common.functions.FlatMapFunction;
import org.apache.flink.api.common.functions.MapFunction;
import org.apache.flink.api.common.functions.RichMapFunction;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.typeinfo.TypeHint;
import org.apache.flink.api.common.typeinfo.TypeInformation;
import org.apache.flink.api.java.functions.KeySelector;
import org.apache.flink.api.java.tuple.Tuple2;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.shaded.jackson2.com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.flink.streaming.api.TimeCharacteristic;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.AssignerWithPeriodicWatermarks;
import org.apache.flink.streaming.api.functions.timestamps.BoundedOutOfOrdernessTimestampExtractor;
import org.apache.flink.streaming.api.functions.windowing.AllWindowFunction;
import org.apache.flink.streaming.api.functions.windowing.ProcessWindowFunction;
import org.apache.flink.streaming.api.watermark.Watermark;
import org.apache.flink.streaming.api.windowing.assigners.GlobalWindows;
import org.apache.flink.streaming.api.windowing.time.Time;
import org.apache.flink.streaming.api.windowing.triggers.ContinuousProcessingTimeTrigger;
import org.apache.flink.streaming.api.windowing.windows.TimeWindow;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.util.Collector;

import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;

public class PrcoessRecentlyChangedWikiFromKafkaAndWriteToInfluxDatabase
{
    public static void main(String[] args) throws Exception
    {
        
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setStreamTimeCharacteristic(TimeCharacteristic.EventTime);
        //env.setStateBackend(new FsStateBackend("file:///data/flink/checkpoints"));
        //CheckpointConfig config = env.getCheckpointConfig();
        //config.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        
        env.setParallelism(1);
        //env.enableCheckpointing(10000);
        
        Properties properties = new Properties();
        properties.setProperty("bootstrap.servers", "localhost:9092");
        properties.setProperty("group.id", "flink");
        
        FlinkKafkaConsumer<String> consumer = new FlinkKafkaConsumer<>("wikimedia.recentchange", new SimpleStringSchema(), properties);
        consumer.setStartFromLatest();
        
        SingleOutputStreamOperator<RecentChangedWikimedia> streamWithWatermark = env
                .addSource(consumer)
                .map(new MapToRecentChangedWikimedia())
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<RecentChangedWikimedia>(Time.seconds(300))
                {
                    @Override
                    public long extractTimestamp(RecentChangedWikimedia element)
                    {
                        System.out.println("element.getTimestamp_ms() = "+element.getTimestamp_ms());
                        return element.getTimestamp_ms() * 1000;
                    }
                })
   ;     
        ProcessWindowFunction<Tuple2<String,Long>,Tuple3<String,Long,Timestamp>,String,TimeWindow> processFucntion =
                new ProcessWindowFunction<Tuple2<String,Long>,Tuple3<String,Long,Timestamp>,String,TimeWindow>()
                {
                    @Override
                    public void process(String s, Context context, Iterable<Tuple2<String,Long>> elements, Collector<Tuple3<String,Long,Timestamp>> out) throws Exception
                    {
                        elements.forEach(stringIntegerLongTuple3 -> out.collect(new Tuple3<>(stringIntegerLongTuple3.f0, stringIntegerLongTuple3.f1, new Timestamp(context.window().getEnd()))));
                    }
                };

        // Trending Hashtag approach 1
        streamWithWatermark.flatMap(new TokenizeRecentChangedWikimediaFlatMap())
                .keyBy((KeySelector<Tuple2<String,Long>,String>)stringIntegerLongTuple3 -> stringIntegerLongTuple3.f0)
                .timeWindow(Time.seconds(30), Time.seconds(5))
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .aggregate(new CustomSumAggregator(), processFucntion)
                .assignTimestampsAndWatermarks(new BoundedOutOfOrdernessTimestampExtractor<Tuple3<String,Long,Timestamp>>(Time.seconds(300))
                {
                    @Override
                    public long extractTimestamp(Tuple3<String,Long,Timestamp> element)
                    {
                        return element.f2.getTime();
                    }
                })
                .timeWindowAll(Time.seconds(5))
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .maxBy(1)
                .map(new MapToMostRecentChangedWikiInfluxDBPoint1())
                .addSink(new InfluxDBSink());

        // Trending Hashtag approach 2
        streamWithWatermark.flatMap(new TokenizeRecentChangedWikimediaFlatMap())
                .timeWindowAll(Time.seconds(30), Time.seconds(5))
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .apply(new AllWindowFunction<Tuple2<String,Long>,Tuple3<String,Long,Timestamp>,TimeWindow>()
                {
                    @Override
                    public void apply(TimeWindow window, Iterable<Tuple2<String,Long>> values, Collector<Tuple3<String,Long,Timestamp>> out) throws Exception
                    {
                        Map<String,Long> map = new HashMap<>();
                        Iterator<Tuple2<String,Long>> iterator = values.iterator();
                        while(iterator.hasNext())
                        {
                            Tuple2<String,Long> current = iterator.next();
                            Long aLong = map.get(current.f0);
                            if(aLong == null)
                            {
                                map.put(current.f0, current.f1);
                            }
                            else
                            {
                                map.put(current.f0, aLong + current.f1);
                            }
                        }


                        Iterator<Map.Entry<String,Long>> iterator1 = map.entrySet().iterator();
                        String currentHashTag = "";
                        Long currentHashTagCount = 0L;

                        while(iterator1.hasNext())
                        {
                            Map.Entry<String,Long> current = iterator1.next();
                            if(current.getValue() > currentHashTagCount)
                            {
                                currentHashTag = current.getKey();
                                currentHashTagCount = current.getValue();
                            }
                        }
                        out.collect(new Tuple3(currentHashTag, currentHashTagCount, new Timestamp(window.getEnd())));
                    }
                })
                .map(new MapToMostRecentChangedWikiInfluxDBPoint2())
                .addSink(new InfluxDBSink());

        // Total Change Wikimedia count - triggered every 5 seconds
        streamWithWatermark
                .map(wikimedia -> 1L)
                .returns(TypeInformation.of(new TypeHint<Long>()
                {
                }))
                .windowAll(GlobalWindows.create())
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .sum(0)
                .map(new TotalChangedWikiCountInfluxDBPoint())
                .addSink(new InfluxDBSink());

        // Change Wikimedia per second - triggered every 5 seconds
        streamWithWatermark
                .timeWindowAll(Time.seconds(1))
                .trigger(ContinuousProcessingTimeTrigger.of(Time.seconds(5)))
                .apply((AllWindowFunction<RecentChangedWikimedia,Tuple2<Timestamp,Long>,TimeWindow>)(window, values, out) -> {
                    long count = 0;
                    Iterator<RecentChangedWikimedia> iterator = values.iterator();
                    while(iterator.hasNext())
                    {
                        iterator.next();
                        count++;
                    }

                    out.collect(new Tuple2<>(new Timestamp(window.getEnd()), count));
                }, TypeInformation.of(new TypeHint<Tuple2<Timestamp,Long>>()
                {
                })).map(new ChangedWikiPerSecondCountInfluxDBPoint())
                .addSink(new InfluxDBSink());



        env.execute("Wikimedia Streaming Example");
    }
    
    public static class TokenizeRecentChangedWikimediaFlatMap implements FlatMapFunction<RecentChangedWikimedia,Tuple2<String,Long>>
    {
        private static final long serialVersionUID = 1L;
        
        private transient ObjectMapper jsonParser;
        
        @Override
        public void flatMap(RecentChangedWikimedia wikimedia, Collector<Tuple2<String,Long>> out)
        {
            out.collect(new Tuple2<>(wikimedia.getServer_name(), 1L));
        }
    }
    
    private static class MapToRecentChangedWikimedia implements MapFunction<String, RecentChangedWikimedia>
    {
        
        @Override
        public RecentChangedWikimedia map(String s) throws Exception
        {
            ObjectMapper mapper = new ObjectMapper();
            try
            {
                final RecentChangedWikimedia recentChangedWikimedia = mapper.readValue(s, RecentChangedWikimedia.class);
                recentChangedWikimedia.setFullJson(s);
                return recentChangedWikimedia;
            }
            catch(Exception e)
            {
                e.printStackTrace();
            }
            return null;
        }
    }
    
    
    private static class MapToMostRecentChangedWikiInfluxDBPoint1 extends RichMapFunction<Tuple3<String,Long,Timestamp>, Point>
    {
        @Override
        public Point map(Tuple3<String,Long,Timestamp> trendingHashTag) throws Exception
        {
            return Point.measurement("MostRecentChangedWiki1").time(trendingHashTag.f2.getTime(), WritePrecision.MS).addField("hashtag", trendingHashTag.f0).addField("count", trendingHashTag.f1);
        }
    }
    
    private static class MapToMostRecentChangedWikiInfluxDBPoint2 extends RichMapFunction<Tuple3<String,Long,Timestamp>,Point>
    {
        @Override
        public Point map(Tuple3<String,Long,Timestamp> trendingHashTag) throws Exception
        {
            return Point.measurement("MostRecentChangedWiki2").time(trendingHashTag.f2.getTime(), WritePrecision.MS) .addField("hashtag", trendingHashTag.f0).addField("count", trendingHashTag.f1);
        }
    }
    
    private static class TotalChangedWikiCountInfluxDBPoint implements MapFunction<Long,Point>
    {
        
        @Override
        public Point map(Long count) throws Exception
        {
            return Point.measurement("ChangedWikimediaCount").time(System.currentTimeMillis(), WritePrecision.MS) .addField("count", count);
        }
    }
    
    private static class ChangedWikiPerSecondCountInfluxDBPoint extends RichMapFunction<Tuple2<Timestamp,Long>,Point>
    {
        
        @Override
        public Point map(Tuple2<Timestamp,Long> changeWikiPerSecondCount) throws Exception
        {
            return Point.measurement("ChangedWikiPerSecondCount").time(changeWikiPerSecondCount.f0.getTime(), WritePrecision.MS) .addField("count", changeWikiPerSecondCount.f1);
        }
    }
    
    public static class TimeLagWatermarkGenerator implements AssignerWithPeriodicWatermarks<RecentChangedWikimedia>
    {
        
        private final long maxTimeLag = 300000;
        
        @Override
        public long extractTimestamp(RecentChangedWikimedia element, long previousElementTimestamp)
        {
            return element.getTimestamp_ms();
        }
        
        @Override
        public Watermark getCurrentWatermark()
        {
            return new Watermark(System.currentTimeMillis() - maxTimeLag);
        }
    }
    
    private static class SumAggregate implements AggregateFunction<Long,Long,Long>
    {
        
        @Override
        public Long createAccumulator()
        {
            return new Long(0);
        }
        
        @Override
        public Long add(Long aLong, Long aLong2)
        {
            return aLong + aLong2;
        }
        
        @Override
        public Long getResult(Long aLong)
        {
            return aLong;
        }
        
        @Override
        public Long merge(Long aLong, Long acc1)
        {
            return aLong + acc1;
        }
    }
    
    private static class TrendingHashTag implements AllWindowFunction<Tuple2<String,Integer>,Tuple2<String,Integer>,TimeWindow>
    {
        @Override
        public void apply(TimeWindow window, Iterable<Tuple2<String,Integer>> values, Collector<Tuple2<String,Integer>> out) throws Exception
        {
            Tuple2<String,Integer> max = new Tuple2<>("", 0);
            Iterator<Tuple2<String,Integer>> iterator = values.iterator();
            while(iterator.hasNext())
            {
                Tuple2<String,Integer> current = iterator.next();
                if(max.f1 < current.f1)
                {
                    max = current;
                }
            }
            out.collect(max);
        }
    }
    
    public static class CustomSumAggregator implements AggregateFunction<Tuple2<String,Long>,Tuple2<String,Long>,Tuple2<String,Long>>
    {
        @Override
        public Tuple2<String,Long> createAccumulator()
        {
            return new Tuple2<String,Long>("", 0L);
        }
        
        @Override
        public Tuple2<String,Long> add(Tuple2<String,Long> stringIntegerLongTuple3, Tuple2<String,Long> stringIntegerLongTuple32)
        {
            return new Tuple2<>(stringIntegerLongTuple3.f0, stringIntegerLongTuple3.f1 + stringIntegerLongTuple32.f1);
        }
        
        @Override
        public Tuple2<String,Long> getResult(Tuple2<String,Long> stringIntegerLongTuple3)
        {
            return stringIntegerLongTuple3;
        }
        
        @Override
        public Tuple2<String,Long> merge(Tuple2<String,Long> stringIntegerLongTuple3, Tuple2<String,Long> acc1)
        {
            return new Tuple2<>(stringIntegerLongTuple3.f0, stringIntegerLongTuple3.f1 + acc1.f1);
        }
    }
}
