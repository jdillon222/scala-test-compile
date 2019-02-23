//Author: Fei Liu
//Date: 2018-12-07
//Goal: This script is for license counting purpose for Bell
//Output: csv file saving in S3 bucket 
//Final dashboard: http://10.84.3.123:8080/note/4478004549013234/License-Count-Bell-Qsy6ZVQ5ee
import java.net.URI 
import org.apache.hadoop.fs.{FileSystem, LocatedFileStatus, Path, RemoteIterator}
import java.util.Date
import org.apache.spark.sql._
import org.apache.spark.sql.SQLContext;
import org.apache.spark.sql.types._
import org.apache.spark.sql.types.{StructType, StructField, StringType, IntegerType};
import org.apache.spark.sql.{DataFrame, Dataset, Encoders, SparkSession}
import org.apache.spark.sql.functions._
import org.joda.time.DateTime
import org.apache.spark.sql.Row
import org.apache.spark.sql.functions.{to_date, to_timestamp}
import java.util.Calendar
import scala.collection.mutable.ArrayBuffer
import java.util.Date
// S3 path for Bell in delta
val deployment = "delta"
val environment = "prod"
val cloud_bucket = "plume-delta-ca-central-1-cloud"
val s3BasePath =s"s3://${cloud_bucket}/type=datawarehouse-avrodata/environment=${environment}/deployment=${deployment}"
// Get today's date
val cal = Calendar.getInstance()
val Year =cal.get(Calendar.YEAR )
val Month =f"${cal.get(Calendar.MONTH )+1}%02d"
val Day =f"${cal.get(Calendar.DATE )}%02d"
//  Bell node_master_dimension table
val nmd_bell = spark.read.format("com.databricks.spark.avro").load(s"${s3BasePath}/table=node_master_dimension")
nmd_bell.createOrReplaceTempView("node_master_dimension_bell")
nmd_bell.show(5)
//  Bell node_dimension table: get current day's data only
val nd_bell = spark.read.format("com.databricks.spark.avro").load(s"${s3BasePath}/table=node_dimension/year=${Year}/month=${Month}/day=${Day}/hour=00/minute=00")
nd_bell.createOrReplaceTempView("node_dimension_bell")
nd_bell.show(5)
// Bell Provisioned table
val prov_lc_bell = spark.sql("select current_timestamp as curr_date, 'delta' as deployment, 'provisioned' as tabletype, idtype, count(distinct(id)) as num_nodes from node_master_dimension_bell where is_device = false group by 1,2,3,4")
prov_lc_bell.show(5)
// Bell Provisioned and Seen table
val prov_seen_bell = spark.sql("select current_timestamp as curr_date, 'delta' as deployment, 'provisioned_seen' as tabletype, idtype, count(distinct(id)) as num_nodes from node_master_dimension_bell inner join node_dimension_bell on node_pk = node_master_fk where is_device = false group by 1,2,3,4")
prov_seen_bell.show(5)
// Bell Online_node table
val online_nodes_bell = spark.sql("select current_timestamp as curr_date, 'delta' as deployment, 'online_nodes' as tabletype, idtype, count(distinct(id)) as num_nodes from node_master_dimension_bell inner join node_dimension_bell on node_pk = node_master_fk where state = 'connected' and is_device = false group by 1,2,3,4")
online_nodes_bell.show(5)
// Combine 3 tables: prov_lc_bell, prov_seen_bell, online_nodes_bell
val merge_df_1 = prov_lc_bell.unionAll(prov_seen_bell) 
val merge_df_2 = merge_df_1.unionAll(online_nodes_bell) 
import java.util.Date
val time = new Date().getTime
merge_df_2.coalesce(1).write.option("header", true).mode("overwrite").csv(s"s3://siam-data/LicensingSourceData/LicenseCount_Dashboard/environment=prod/output/bell/${Year.toString}${Month.toString}${Day.toString}")
