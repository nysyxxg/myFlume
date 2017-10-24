Source里面定义的两个需要实现方法是getChannelProcessor和setChannelProcessor，
我们大概可以猜到，source就是通过ChannelProcessor将event传输给channel的。

先来了解一下Source的类型，Flume根据数据来源的特性将Source分成两类类，
像Http、netcat和exec等就是属于事件驱动型（EventDrivenSource），
而kafka和Jms等就是属于轮询拉取型（PollableSource）。

在启动流程中了解到的，Application是先启动SourceRunner，再由SourceRunner来启动source，
那么既然source有两种类型，那么Sourcerunner也分为EventDrivenSourceRunner和PollableSourceRunner，
我们来看看它们的start()：

	EventDrivenSourceRunner
		 @Override
		  public void start() {
		    Source source = getSource();
		    ChannelProcessor cp = source.getChannelProcessor();
		    cp.initialize();
		    source.start();
		    lifecycleState = LifecycleState.START;
		  }

	PollableSourceRunner
		 @Override
	      public void start() {
	        PollableSource source = (PollableSource) getSource();
	        ChannelProcessor cp = source.getChannelProcessor();
	        cp.initialize();
	        source.start();

	        runner = new PollingRunner();

	        runner.source = source;
	        runner.counterGroup = counterGroup;
	        runner.shouldStop = shouldStop;

	        runnerThread = new Thread(runner);
	        runnerThread.setName(getClass().getSimpleName() + "-" +
	            source.getClass().getSimpleName() + "-" + source.getName());
	        runnerThread.start();

	        lifecycleState = LifecycleState.START;
	      }
