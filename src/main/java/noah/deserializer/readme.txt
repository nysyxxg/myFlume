http://blog.csdn.net/yanghua_kobe/article/details/46595401
Deserializer简介
flume将一条日志抽象成一个event。
这里我们从日志文件中收集日志采用的是定制版的SpoolDirectorySource（我们对当日日志文件追加写入收集提供了支持）。
从日志源中将每条日志转换成event需要Deserializer（反序列化器）。
flume的每一个source对应的deserializer必须实现接口EventDeserializer，该接口定义了readEvent/readEvents方法从各种日志源读取Event。
flume主要支持两种反序列化器：
	（1）AvroEventDeserializer：
		解析Avro容器文件的反序列化器。
		对Avro文件的每条记录生成一个flume Event，并将基于avro编码的二进制记录存入event body中。
	（2）LineDeserializer：它是基于日志文件的反序列化器，以“\n”行结束符将每行区分为一条日志记录。

LineDeserializer的缺陷
	大部分情况下SpoolDictionarySource配合LineDeserializer工作起来都没问题。
	但当日志记录本身被分割成多行时，比如异常日志的堆栈或日志中包含“\n”换行符时，问题就来了：
	原先的按行界定日志记录的方式不能满足这种要求。形如这样的格式：
	    [2015-06-22 13:14:28,780] [ERROR] [sysName] [subSys or component] [Thread-9] [com.messagebus.client.handler.common.CommonLoopHandler] -*- stacktrace -*- : com.rabbitmq.client.ShutdownSignalException: clean channel shutdown; protocol method: #method<channel.close>(reply-code=200, reply-text=OK, class-id=0, method-id=0)
	        at com.rabbitmq.client.QueueingConsumer.handle(QueueingConsumer.java:203)
	        at com.rabbitmq.client.QueueingConsumer.nextDelivery(QueueingConsumer.java:220)
	        at com.messagebus.client.handler.common.CommonLoopHandler.handle(CommonLoopHandler.java:34)
	        at com.messagebus.client.handler.consume.ConsumerDispatchHandler.handle(ConsumerDispatchHandler.java:17)
	        at com.messagebus.client.handler.MessageCarryHandlerChain.handle(MessageCarryHandlerChain.java:72)
	        at com.messagebus.client.handler.consume.RealConsumer.handle(RealConsumer.java:44)
	        at com.messagebus.client.handler.MessageCarryHandlerChain.handle(MessageCarryHandlerChain.java:72)
	        at com.messagebus.client.handler.consume.ConsumerTagGenerator.handle(ConsumerTagGenerator.java:22)
	        at com.messagebus.client.handler.MessageCarryHandlerChain.handle(MessageCarryHandlerChain.java:72)
	        at com.messagebus.client.handler.consume.ConsumePermission.handle(ConsumePermission.java:37)
	        at com.messagebus.client.handler.MessageCarryHandlerChain.handle(MessageCarryHandlerChain.java:72)
	        at com.messagebus.client.handler.consume.ConsumeParamValidator.handle(ConsumeParamValidator.java:17)
	        at com.messagebus.client.handler.MessageCarryHandlerChain.handle(MessageCarryHandlerChain.java:72)
	        at com.messagebus.client.carry.GenericConsumer.run(GenericConsumer.java:50)
	        at java.lang.Thread.run(Thread.java:744)
	    Caused by: com.rabbitmq.client.ShutdownSignalException: clean channel shutdown; protocol method: #method<channel.close>(reply-code=200, reply-text=OK, class-id=0, method-id=0)

当然你也可以对日志内容进行特殊处理，让一条日志的所有内容以一行输出，但这样需要对日志框架进行定制，有时这并不受你控制。
因此这里最好的选择是定制日志收集器。

了解一下Flume源码中LineDeserializer的核心实现：