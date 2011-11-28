package core;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;

public class RPCManager {
	
	private class Pair {
		Object host;
		Method method;
	}
	
	private Map<String, Pair> rpcs = new HashMap<>();
	
	private ConcurrentLinkedQueue<RPC> rpcQueue = new ConcurrentLinkedQueue<>(); 
	
	public <T> T registerRPC(Class<T> rpcClaz) {
		T host = null;
		for (Method method : rpcClaz.getMethods()) {
			RemoteCall rc = method.getAnnotation(RemoteCall.class);
			if (rc != null) {
				String rpcName = method.getName();
				if (rc.name() != null && rc.name().length() > 0) {
					rpcName = rc.name();
				}
				// if method of the same name is already registered, throw an exception
				if (rpcs.containsKey(rpcName)) {
					throw new IllegalStateException("rpc [" + rpcName + "] is already registered.");
				}
				// constructor with no params is used to instance the rpc host.
				if (host == null) {
					try {
						host = rpcClaz.newInstance();
					} catch (InstantiationException | IllegalAccessException e) {
						throw new IllegalStateException(e);
					}
				}
				
				Pair p = new Pair();
				p.host = host;
				p.method = method;
				rpcs.put(rpcName, p);
			}
		}
		return host;
	}
	
	public void add(RPC rpc) {
		rpcQueue.offer(rpc);
	}
	
	public void invokeRPC(RPC rpc) {
		String funcName = rpc.getFunctionName();
		
		if (funcName == null || funcName.length() == 0) {
			System.err.println("> remote call function name is empty");
			return;
		}
		
		if (!this.rpcs.containsKey(funcName)) {
			System.err.println("> remote call function " + funcName + " is not registered");
			return;
		}
		
		Pair p = this.rpcs.get(funcName);
		try {
			System.out.println("> remote call " + funcName + ".");
			
			Session session = rpc.getSession();
			
			if (rpc.getParameters() != null && rpc.getParameters().length > 0) {
				Object[] params = new Object[1 + rpc.getParameters().length];
				params[0] = rpc.getSession();
				System.arraycopy(rpc.getParameters(), 0, params, 1, params.length - 1);
				p.method.invoke(p.host, params);
			} else {
				p.method.invoke(p.host, rpc.getSession());
			}
			
			session.flush();
		} catch (IllegalAccessException | IllegalArgumentException
				| InvocationTargetException e) {
			System.err.println("> remote call function " + funcName + " generates error.");
			System.err.println(e.getMessage());
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	public void start() {
		// only one thread is handling the rpc.
		Executors.defaultThreadFactory().newThread(new RPCHandler(this)).start();
	}
	
	private class RPCHandler implements Runnable {
		
		private final int WAIT_TIME = 1000 * 2;
		
		private RPCManager manager;
		
		public RPCHandler(RPCManager manager) {
			this.manager = manager;
		}
		
		@Override
		public void run() {
			while (true) {
				RPC rpc = rpcQueue.poll();
				if (rpc == null) {
					try {
						synchronized(manager) {
							manager.wait(WAIT_TIME);
						}
					} catch (InterruptedException e) {
						// swallowed
					}
				} else {
					invokeRPC(rpc);
				}
			}
		}
	}
}
