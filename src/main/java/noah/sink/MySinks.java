package noah.sink;

import org.apache.flume.*;
import org.apache.flume.conf.Configurable;
import org.apache.flume.sink.AbstractSink;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * MySinks
 * Created by noah on 17-4-23.
 */
public class MySinks extends AbstractSink implements Configurable {
	private static final Logger logger = LoggerFactory.getLogger(MySinks.class);
	private static final String PROP_KEY_ROOTPATH = "fileName";
	private String fileName;

	@Override
	public void configure(Context context) {
		// TODO Auto-generated method stub
		fileName = context.getString(PROP_KEY_ROOTPATH);
	}

	@Override
	public Status process() throws EventDeliveryException {
		// TODO Auto-generated method stub
		Channel ch = getChannel();
		Transaction txn = ch.getTransaction();
		Event event = null;
		txn.begin();
		while (true) {
			event = ch.take();
			if (event != null) {
				break;
			}
		}
		try {

			logger.debug("Get event.");

			String body = new String(event.getBody());
			System.out.println("event.getBody()-----" + body);

			String res = body + ":" + System.currentTimeMillis() + "\r\n";
			File file = new File(fileName);
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(file, true);
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				fos.write(res.getBytes());
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			try {
				fos.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			txn.commit();
			return Status.READY;
		} catch (Throwable th) {
			txn.rollback();

			if (th instanceof Error) {
				throw (Error) th;
			} else {
				throw new EventDeliveryException(th);
			}
		} finally {
			txn.close();
		}
	}

}