package noah.sink;

import java.io.IOException;
import java.net.ConnectException;

import org.apache.flume.Channel;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDeliveryException;
import org.apache.flume.Transaction;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import com.aerospike.client.AerospikeException;
import com.aerospike.client.Bin;
import com.aerospike.client.Key;
import com.aerospike.client.Record;
import com.aerospike.client.async.AsyncClient;
import com.aerospike.client.listener.RecordListener;
import com.aerospike.client.listener.WriteListener;
import com.aerospike.client.policy.WritePolicy;
import com.aerospike.client.async.AsyncClientPolicy;
import com.aerospike.client.policy.Policy;
import com.aerospike.client.Host;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * a1.sinks.k1_1.type = kafka_sink.asd.Asdsink
 * a1.sinks.k1_1.asd_host1 = 127.0.0.1
 * a1.sinks.k1_1.asd_host2 = 192.168.0.1
 * a1.sinks.k1_1.asd_port = 3000
 * a1.sinks.k1_1.set_name = test_set_name
 * a1.sinks.k1_1.bin_name = test_bin_name
 * a1.sinks.k1_1.batchSize =  10000
 * from:
 * http://blog.csdn.net/yanshu2012/article/details/53391070
 */
public class Asdsink extends AbstractSink implements Configurable {
	//private String myProp;
	public static final String TOPIC_HDR = "topic";
	public static final String KEY_HDR = "key";
	//private String mz_tag_topic;
	//private AerospikeClient asd_client;
	private String ASD_HOST1;
	private String ASD_HOST2;
	private int ASD_PORT;
	private String ASD_NAME_SPACE = "cm";
	private String SET_NAME;
	private String BIN_NAME;
	private int batchSize;// 一次事务的event数量，整体提交
	private WritePolicy write_policy;
	private Policy policy;
	//Async Read and Write
	private AsyncClient asd_async_client;
	private AsyncClientPolicy async_client_policy;
	private boolean completed;


	@Override
	public void configure(Context context) {
		//String myProp = context.getString("myProp", "defaultValue");

		// Process the myProp value (e.g. validation)

		// Store myProp for later retrieval by process() method
		//this.myProp = myProp;
		ASD_HOST1 = context.getString("asd_host1", "127.0.0.1");
		ASD_HOST2 = context.getString("asd_host2", "127.0.0.1");
		ASD_PORT = context.getInteger("asd_port", 3000);
		SET_NAME = context.getString("set_name", "xxx");
		BIN_NAME = context.getString("bin_name", "xxx");
		batchSize = context.getInteger("batchSize", 1000);
		System.out.printf("ASD_HOST1:%s\n", ASD_HOST1);
		System.out.printf("ASD_HOST2:%s\n", ASD_HOST2);
		System.out.printf("ASD_PORT:%d\n", ASD_PORT);
		System.out.printf("SET_NAME:%s\n", SET_NAME);
		System.out.printf("BIN_NAME:%s\n", BIN_NAME);
		System.out.printf("batchSize:%d\n", batchSize);

	}

	@Override
	public void start() {
		// Initialize the connection to the external repository (e.g. HDFS) that
		// this Sink will forward Events to ..

		Host[] hosts = new Host[]{new Host(ASD_HOST1, 3000),
				new Host(ASD_HOST2, 3000)};

		async_client_policy = new AsyncClientPolicy();
		async_client_policy.asyncMaxCommands = 300;
		async_client_policy.failIfNotConnected = true;
		asd_async_client = new AsyncClient(async_client_policy, hosts);
		policy = new Policy();

		// jdk 8
		//policy.timeout = 20;
		write_policy = new WritePolicy();
		// jdk 8
		//write_policy.timeout = 20;
	}

	@Override
	public void stop() {
		// Disconnect from the external respository and do any
		// additional cleanup (e.g. releasing resources or nulling-out
		// field values) ..
		asd_async_client.close();
	}

	@Override
	public Status process() throws EventDeliveryException {
		Status status = null;
		// Start transaction

		Channel ch = getChannel();
		Transaction txn = ch.getTransaction();
		txn.begin();
		try {
			// This try clause includes whatever Channel operations you want to do
			long processedEvent = 0;
			for (; processedEvent < batchSize; processedEvent++) {
				Event event = ch.take();

				byte[] eventBody;

				if (event != null) {
					eventBody = event.getBody();
					String line = new String(eventBody, "UTF-8");
					if (line.length() > 0) {
						String[] key_tag = line.split("\t");
						if (key_tag.length == 2) {
							String tmp_key = key_tag[0];
							String tmp_tag = key_tag[1];
							Key as_key = new Key(ASD_NAME_SPACE, SET_NAME, tmp_key);
							Bin ad_bin = new Bin(BIN_NAME, tmp_tag);
							try {
								completed = false;
								asd_async_client.get(policy, new ReadHandler(asd_async_client, policy, write_policy, as_key, ad_bin), as_key);
								waitTillComplete();
							} catch (Throwable t) {
								System.out.println("[ERROR][process]" + t.toString());
							}
						}
					}
				}
			}


			// Send the Event to the external repository.
			// storeSomeData(e);
			status = Status.READY;
			txn.commit();
		} catch (Throwable t) {

			txn.rollback();
			// Log exception, handle individual exceptions as needed

			status = Status.BACKOFF;
			// re-throw all Errors
			if (t instanceof Error) {
				System.out.println("[ERROR][process]" + t.toString());
				throw (Error) t;
			}
		}

		txn.close();
		return status;
	}


	private class WriteHandler implements WriteListener {
		private final AsyncClient client;
		private final WritePolicy policy;
		private final Key key;
		private final Bin bin;
		private int failCount = 0;

		public WriteHandler(AsyncClient client, WritePolicy policy, Key key, Bin bin) {
			this.client = client;
			this.policy = policy;
			this.key = key;
			this.bin = bin;
		}

		// Write success callback.
		public void onSuccess(Key key) {
			try {
				// Write succeeded.
			} catch (Exception e) {
				System.out.printf("[ERROR][WriteHandler]Failed to put: namespace=%s set=%s key=%s exception=%s\n", key.namespace, key.setName, key.userKey, e.getMessage());
			}

			notifyCompleted();
		}

		public void onFailure(AerospikeException e) {
			// Retry up to 2 more times.
			if (++failCount <= 2) {
				Throwable t = e.getCause();

				// Check for common socket errors.
				if (t != null && (t instanceof ConnectException || t instanceof IOException)) {
					//console.info("Retrying put: " + key.userKey);
					try {
						client.put(policy, this, key, bin);
						return;
					} catch (Exception ex) {
						// Fall through to error case.
						System.out.printf("[ERROR][WriteHandler]Failed to put: namespace=%s set=%s key=%s bin_name=% bin_value=%s exception=%s\n", key.namespace, key.setName, key.userKey, bin.name, bin.value.toString(), e.getMessage());
					}
				}
			}

			notifyCompleted();
		}
	}

	private class ReadHandler implements RecordListener {
		private final AsyncClient client;
		private final Policy policy;
		private final WritePolicy write_policy;
		private final Key key;
		private final Bin bin;
		private int failCount = 0;

		public ReadHandler(AsyncClient client, Policy policy, WritePolicy write_policy, Key key, Bin bin) {
			this.client = client;
			this.policy = policy;
			this.write_policy = write_policy;
			this.key = key;
			this.bin = bin;
		}

		// Read success callback.
		public void onSuccess(Key key, Record record) {

			try {
				// Read succeeded.  Now call write.
				if (record != null) {
					String str = record.getString("mz_tag");


					if (str != null && str.length() > 0) {
						Pattern p101 = Pattern.compile("(101\\d{4})");
						Pattern p102 = Pattern.compile("(102\\d{4})");
						Pattern p103 = Pattern.compile("(103\\d{4})");
						String tags = "";
						Matcher m101 = p101.matcher(str);
						while (m101.find()) {
							tags += ("," + m101.group(1));
						}

						Matcher m102 = p102.matcher(str);
						while (m102.find()) {
							tags += ("," + m102.group(1));
						}

						Matcher m103 = p103.matcher(str);
						while (m103.find()) {
							tags += ("," + m103.group(1));
						}

						if (tags.length() > 0) {
							String value_new = (bin.value.toString() + tags);
							Bin new_bin = new Bin("mz_tag", value_new);
							client.put(write_policy, new WriteHandler(client, write_policy, key, new_bin), key, new_bin);
						} else {
							client.put(write_policy, new WriteHandler(client, write_policy, key, bin), key, bin);
						}

					} else {
						client.put(write_policy, new WriteHandler(client, write_policy, key, bin), key, bin);
					}
				} else {
					client.put(write_policy, new WriteHandler(client, write_policy, key, bin), key, bin);
				}

			} catch (Exception e) {
				System.out.printf("[ERROR][ReadHandler]Failed to get: namespace=%s set=%s key=%s exception=%s\n", key.namespace, key.setName, key.userKey, e.getMessage());
			}

		}

		// Error callback.
		public void onFailure(AerospikeException e) {
			// Retry up to 2 more times.
			if (++failCount <= 2) {
				Throwable t = e.getCause();

				// Check for common socket errors.
				if (t != null && (t instanceof ConnectException || t instanceof IOException)) {
					//console.info("Retrying get: " + key.userKey);
					try {
						client.get(policy, this, key);
						return;
					} catch (Exception ex) {
						// Fall through to error case.
						System.out.printf("[ERROR][ReadHandler]Failed to get: namespace=%s set=%s key=%s exception=%s\n", key.namespace, key.setName, key.userKey, e.getMessage());
					}
				}
			}
			notifyCompleted();
		}
	}


	private synchronized void waitTillComplete() {
		while (!completed) {
			try {
				super.wait();
			} catch (InterruptedException ie) {
			}
		}
	}

	private synchronized void notifyCompleted() {
		completed = true;
		super.notify();
	}
}