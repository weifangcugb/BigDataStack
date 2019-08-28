package org.training.hadoop.hbase;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.hbase.Cell;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.*;
import org.apache.hadoop.hbase.filter.*;
import org.apache.hadoop.hbase.util.Bytes;

import java.io.IOException;


/**
 * HBase过滤器：行键、列族、列、单列值、正则表达式
 */

public class Filter {

    public static final String TABLE_NAME = "train";
    public static final String COLUMN_NAME_1 = "on";
    public static final String COLUMN_NAME_2 = "off";
    public static final String OFF_COLUMN_MERCHAMT = "merchant";
    public static final String OFF_COLUMN_COUPON = "coupon";
    public static final String OFF_COLUMN_DATE = "traindata";

    //  查询User_id=5365894在20160510到20160520之间使用优惠券发生的线下购买行为
    public static void get(Configuration conf) throws IOException {
        Connection connection = null;

        connection = ConnectionFactory.createConnection(conf);

        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));

        SingleColumnValueFilter filter1 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_1), Bytes.toBytes(OFF_COLUMN_DATE),
                CompareFilter.CompareOp.LESS, new SubstringComparator("20160520"));

        SingleColumnValueFilter filter2 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_1), Bytes.toBytes(OFF_COLUMN_DATE),
                CompareFilter.CompareOp.GREATER, Bytes.toBytes("20160510"));

        SingleColumnValueFilter filter3 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_1), Bytes.toBytes(OFF_COLUMN_MERCHAMT),
                CompareFilter.CompareOp.NOT_EQUAL, Bytes.toBytes("null"));

         FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        filterList.addFilter(filter1);
        filterList.addFilter(filter2);
        filterList.addFilter(filter3);

        Scan scan = new Scan();
        scan.setStartRow(Bytes.toBytes("5365894"));
        scan.setStopRow(Bytes.toBytes("5365894"));
        scan.setFilter(filterList);

        ResultScanner results = table.getScanner(scan);

        for(Result result : results) {
            for (Cell cell :result.rawCells()){
                System.out.println("Cell: "+cell+",Value:" + Bytes.toString(cell.getValueArray(),cell.getValueOffset(), cell.getValueLength()));
            }
        }

    }

    //  查询Merchant_id=41306在2016年发放的线上优惠券总数
    public static void count(Configuration conf) throws IOException {
        Connection connection = null;

        connection = ConnectionFactory.createConnection(conf);

        Table table = connection.getTable(TableName.valueOf(TABLE_NAME));

        SingleColumnValueFilter filter1 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_2), Bytes.toBytes(OFF_COLUMN_DATE),
                CompareFilter.CompareOp.LESS, new BinaryPrefixComparator(Bytes.toBytes("2016")));

        SingleColumnValueFilter filter2 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_2), Bytes.toBytes(OFF_COLUMN_COUPON),
                CompareFilter.CompareOp.NOT_EQUAL, Bytes.toBytes("null"));

        SingleColumnValueFilter filter3 = new SingleColumnValueFilter(Bytes.toBytes(COLUMN_NAME_2), Bytes.toBytes(OFF_COLUMN_MERCHAMT),
                CompareFilter.CompareOp.EQUAL, new BinaryComparator(Bytes.toBytes("41306")));

        FilterList filterList = new FilterList(FilterList.Operator.MUST_PASS_ALL);
        filterList.addFilter(filter1);
        filterList.addFilter(filter2);
        filterList.addFilter(filter3);

        Scan scan = new Scan();
        scan.setFilter(filterList);

        ResultScanner results = table.getScanner(scan);

        int count = 0;
        for(Result result : results) {
            System.out.println(++count);
            for (Cell cell :result.rawCells()){
                System.out.println("Cell: "+cell+",Value:" + Bytes.toString(cell.getValueArray(),cell.getValueOffset(), cell.getValueLength()));
            }
        }

    }

    public static void main(String[] args) throws IOException {
//        Filter.get(TableInformation.getHBaseConfiguration());
        Filter.count(TableInformation.getHBaseConfiguration());
    }
}
