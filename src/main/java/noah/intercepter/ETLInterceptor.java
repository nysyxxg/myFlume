package noah.intercepter;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.interceptor.Interceptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.List;
//clog.sources.source_log3.interceptors.i3.type=com.thextrader.dmp.streaming.flume.BidInfoLogUrlFilter$Builder

/**
 * 拦截器是简单的插件式组件，设置在source和channel之间。
 * source接收到的时间，在写入channel之前，拦截器都可以进行转换或者删除这些事件。
 * 每个拦截器只处理同一个source接收到的事件。
 * flume官方实现了很多拦截器也可以自定义拦截器。通过实现自定义的拦截器可以对日志进行ETL。
 * 自定义拦截器只需要实现Interceptor的继承类。具体步骤如下：
 * <p>
 * 1,将上面的java代码打成jar包。
 * 在flume的安装目录下的plugins.d 目录下新建文件夹ETLInterceptor.文件夹这种新建三个文件夹lib，libext，native。
 * 将jar包放入lib文件夹中。
 * 2,配置flume source的interceptor type为com.test.flume.ETLInterceptor.$Builder
 * 3,启动flume ，自定义的拦截器就生效了。
 * from:
 * http://blog.csdn.net/yanshu2012/article/details/54019034
 */
public class ETLInterceptor implements Interceptor {
	private static Logger logger = LoggerFactory.getLogger(ETLInterceptor.class);

	@Override
	public void initialize() {

	}

	/**
	 * 函数中写你需要的ETL等逻辑。
	 *
	 * @return
	 */
	@Override
	public Event intercept(Event event) {
		String body = new String(event.getBody(), Charsets.UTF_8);
		String newBody = body;
		try {
			//add begin
			String[] splits = body.split("\\^");
			splits[6] = "";
			splits[9] = "";
			splits[10] = "";
			splits[11] = "";
			splits[17] = "";
			splits[25] = "";
			if (splits.length > 28) {
				// java 8
				//newBody = String.join("^", Arrays.copyOfRange(splits,0,28));
			} else {
				//newBody = String.join("^", splits);
			}
			//add end
			event.setBody(newBody.toString().getBytes());
		} catch (Exception e) {
			logger.warn(body, e);
			event = null;
		}
		return event;
	}

	@Override
	public List<Event> intercept(List<Event> events) {
		List<Event> intercepted = Lists.newArrayListWithCapacity(events.size());
		for (Event event : events) {
			Event interceptedEvent = intercept(event);
			if (interceptedEvent != null) {
				intercepted.add(interceptedEvent);
			}
		}
		return intercepted;
	}

	@Override
	public void close() {

	}

	/**
	 * 实现Builder
	 * <p>
	 * Builder implements Interceptor.Builder
	 * 函数中new 出继承类 ETLInterceptor。
	 */
	public static class Builder implements Interceptor.Builder {
		//使用Builder初始化Interceptor
		@Override
		public Interceptor build() {
			//add begin
			return new ETLInterceptor();
			//add end
		}

		@Override
		public void configure(Context context) {

		}
	}
}