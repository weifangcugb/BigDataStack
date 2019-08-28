package org.training.spark.core;

import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.broadcast.Broadcast;
import scala.Tuple2;
import scala.Tuple3;

import java.util.*;

/**
 * 年龄段在“18-24”的男性年轻人，最喜欢看哪10部电影
 */
public class JavaPopularMovieAnalyzer {

  public static void main(String[] args) {
    String dataPath = "data/ml-1m";
    SparkConf conf = new SparkConf().setAppName("PopularMovieAnalyzer");
    if (args.length > 0) {
      dataPath = args[0];
    } else {
      conf.setMaster("local[1]");
    }

    JavaSparkContext sc = new JavaSparkContext(conf);

    /**
     * Step 1: Create RDDs
     */
    String DATA_PATH = dataPath;
    String USER_AGE = "18";

    JavaRDD<String> usersRdd = sc.textFile(DATA_PATH + "/users.dat", 2);
    JavaRDD<String> moviesRdd = sc.textFile(DATA_PATH + "/movies.dat", 2);
    JavaRDD<String> ratingsRdd = sc.textFile(DATA_PATH + "/ratings.dat", 2);

    /**
     * Step 2: Extract columns from RDDs
     */
    //users: RDD[(userID, gender, age)]
    JavaRDD<Tuple3<String, String, String>> users = usersRdd
        .map(x -> x.split("::")) // Array[String]
        .map(x -> new Tuple3<String, String, String>(x[0], x[1], x[2]))
        .filter(x -> x._2().equals("M"))
        .filter(x -> {
          int a = Integer.parseInt(x._3());
          return a >= 18 && a <=24;
        });
    //List[String]
    List<String> userlist = users.map(x -> x._1()).collect();

    //broadcast
    Set<String> userSet = new HashSet<>();
    userSet.addAll(userlist); // userIds
    Broadcast<Set<String>> broadcastUserSet = sc.broadcast(userSet);

    /**
     * Step 3: map-side join RDDs
     */
    List<Tuple2<String, Integer>> topKmovies = ratingsRdd
        .map(x -> x.split("::"))
        .mapToPair(x -> new Tuple2<String, String>(x[0], x[1])) // (userId,movieId)
        .filter(x -> broadcastUserSet.getValue().contains(x._1))
        .mapToPair(x -> new Tuple2<String, Integer>(x._2(), 1)) // (movieId, count)
        .reduceByKey((x, y) -> x + y) // (movieId, count)
        .mapToPair(x -> new Tuple2<>(x._2, x._1)) // (count, movieId)
        .sortByKey(false) // sort by count desc
        .mapToPair(x -> new Tuple2<>(x._2, x._1)) // (movieId, count)
        .take(10);

    /**
     * Transfrom filmID to fileName
     */
    List<Tuple2<String, String>> movieID2NameList = moviesRdd
        .map(x -> x.split("::"))
        .map(x -> new Tuple2<>(x[0], x[1]))
        .collect();

    Map<String, String> movieID2Name = new HashMap<>();
    movieID2NameList.stream().forEach(x -> movieID2Name.put(x._1, x._2));

    topKmovies
        .stream()
        .map(x -> movieID2Name.getOrDefault(x._1, null) + "," + x._2)
        .forEach(x -> System.out.println(x));

    sc.stop();
  }
}
