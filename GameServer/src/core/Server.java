package core;

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.channels.AsynchronousChannelGroup;
import java.nio.channels.AsynchronousServerSocketChannel;
import java.nio.channels.AsynchronousSocketChannel;
import java.nio.channels.CompletionHandler;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.Executors;

public final class Server {
	private final int MAX_THREAD_NUM = 1 + 2;
	
	private Set<Session> sessions = new HashSet<>();
	
	private int port;
	private Constructor<? extends Session> sessionFactory;
	private AsynchronousServerSocketChannel server;
	private AcceptHandler acceptHandler;
	
	public boolean init(Class<? extends Session> sessionClass, int port) {
		this.port = port;
		this.acceptHandler = new AcceptHandler();
		
		try {
			this.sessionFactory = sessionClass.getConstructor(AsynchronousSocketChannel.class);
		} catch (NoSuchMethodException | SecurityException e) {
			throw new IllegalStateException(e);
		}
		
		try {
			AsynchronousChannelGroup threadPool = AsynchronousChannelGroup.withFixedThreadPool(
					MAX_THREAD_NUM, Executors.defaultThreadFactory());
			server = AsynchronousServerSocketChannel.open(threadPool);
			SocketAddress address = new InetSocketAddress(this.port);
			server.bind(address);
			return true;
		} catch (IOException e) {
			if (server != null) {
				try {
					server.close();
				} catch (IOException ee) {
					// swallowed.
				}
			}
			throw new IllegalStateException(e);
		}
	}
	
	public void start() {
		this.accept();
	}
	
	public void stop() {
		for (Session session : sessions) {
			session.close();
		}
		try {
			this.server.close();
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}
	
	private void accept() {
		if (this.server.isOpen()) {
			this.server.accept(this, acceptHandler);
		}
	}
	
	public void registerSession(Session session) {
		if (session != null) {
			synchronized(this.sessions) {
				this.sessions.add(session);
			}
		}
	}
	
	public void removeSession(Session session) {
		if (session != null && this.sessions.contains(session)) {
			synchronized(this.sessions) {
				this.sessions.remove(session);
			}
		}
	}
	
	private class AcceptHandler implements CompletionHandler<AsynchronousSocketChannel, Server> {

		@Override
		public void completed(AsynchronousSocketChannel client, Server controller) {
			controller.accept();
			
			Session session = null;
			try {
				session = controller.sessionFactory.newInstance(client);
			} catch (InstantiationException | IllegalAccessException
					| IllegalArgumentException | InvocationTargetException e) {
				throw new IllegalStateException(e);
			}
			
			if (session.init()) {
				System.out.println("> session connected.");
				controller.registerSession(session);
				session.pendingRead();
			} else {
				System.out.println("> session initialize failed.");
			}
		}

		@Override
		public void failed(Throwable exc, Server controller) {
			System.err.println(exc.getMessage());
			controller.accept();
		}		
	}
}
