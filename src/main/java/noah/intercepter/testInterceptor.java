package noah.intercepter;

import org.apache.flume.Event;
import org.apache.flume.conf.Configurable;
import org.apache.flume.interceptor.Interceptor;

import java.util.List;

/**
 * Created by noah on 17-9-8.
 */
public class testInterceptor implements Interceptor {

	/* 任何需要拦截器初始化或者启动的操作就可以定义在此，无则为空即可 */
	@Override
	public void initialize() {

	}

	/* 每次只处理一个Event */
	@Override
	public Event intercept(Event event) {
		return null;
	}

	/* 量处理Event */
	@Override
	public List<Event> intercept(List<Event> events) {
		return null;
	}

	/*需要拦截器执行的任何closing/shutdown操作，一般为空 */
	@Override
	public void close() {

	}

	/* 获取配置文件中的信息，必须要有一个无参的构造方法 */
	public interface Builder extends Configurable {
		public Interceptor build();
	}
}
