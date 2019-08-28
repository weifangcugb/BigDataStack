package com.aura.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FSDataInputStream;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.Connection;
import org.apache.hadoop.hbase.client.ConnectionFactory;
import org.apache.hadoop.hbase.client.Put;
import org.apache.hadoop.hbase.client.Table;
import org.apache.hadoop.hbase.util.Bytes;


import java.io.*;

import static com.aura.hbase.HistoryIngest.QUALIFIER_NAME_SHOPID;
import static com.aura.hbase.Ingest.column_family_cf1;

/**
 * 将HDFS上的原始数据文件转换成HBase能直接importtsv的文件格式
 */
public class HBaseFile {

    //优化rowkey后的hbase文件格式
    public void exportHBaseFile(String user_pay, String hbase_file) {
        FileSystem fs = null;
        BufferedReader in = null;
        FileOutputStream outputStream = null;
        Configuration conf = new Configuration();
        Path myPath = new Path(user_pay);
        try {
            fs = myPath.getFileSystem(conf);

            FSDataInputStream hdfsInStream = fs.open(new Path(user_pay));
            outputStream = new FileOutputStream(hbase_file);
            in = new BufferedReader(new InputStreamReader(hdfsInStream));
            String line = null;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(",", -1);
                String rowkey = HistoryIngest.userIdCompletion(parts[0]) + HistoryIngest.removeLineAndSpace(parts[2].substring(0,13));
                String shopId = parts[1];
                String hbase_line =  rowkey+","+shopId+"\n";
                byte []str = hbase_line.getBytes("utf-8");
                outputStream.write(str);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //未优化的Hbase数据文件
    //rowkey：userid，qualifier：timestamp，value：shopid
    public void exportHBaseFileOutdated(String user_pay, String hbase_file) {
        FileSystem fs = null;
        BufferedReader in = null;
        FileOutputStream outputStream = null;
        Configuration conf = new Configuration();
        Path myPath = new Path(user_pay);
        Connection connection = null;
        try {
            connection  = ConnectionFactory.createConnection(Ingest.getHbaseConf());
            Table table = connection.getTable(TableName.valueOf("history-outdated"));

            fs = myPath.getFileSystem(conf);

            FSDataInputStream hdfsInStream = fs.open(new Path(user_pay));
            outputStream = new FileOutputStream(hbase_file);
            in = new BufferedReader(new InputStreamReader(hdfsInStream));
            String line = null;
            while ((line = in.readLine()) != null) {
                String[] parts = line.split(",", -1);
                String rowkey = parts[0];
                String shopId = parts[1];
                String qualifier = parts[2];
                Put put = new Put(Bytes.toBytes(rowkey));
                put.addColumn(Bytes.toBytes(column_family_cf1), Bytes.toBytes(qualifier), Bytes.toBytes(shopId));
                table.put(put);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (in != null) {
                try {
                    in.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (outputStream != null) {
                try {
                    outputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public static void main(String[] args) {
        HBaseFile file = new HBaseFile();
        file.exportHBaseFileOutdated("E:\\2019光环大数据\\百度网盘\\结业项目\\阿里巴巴口碑商家客流分析系统\\数据\\IJCAI17_original\\dataset\\user_pay.txt",
                "E:\\2019光环大数据\\百度网盘\\结业项目\\阿里巴巴口碑商家客流分析系统\\数据\\IJCAI17_original\\dataset\\user_pay_hbase_outdated.txt");
    }
}