package core;

import java.io.EOFException;
import java.io.IOException;
import java.nio.channels.AsynchronousSocketChannel;

import flex.messaging.io.SerializationContext;
import flex.messaging.io.amf.ASObject;
import flex.messaging.io.amf.Amf3Input;
import flex.messaging.io.amf.Amf3Output;

public final class Amf3Session extends Session {

	private Amf3Input amf3Input;
	private Amf3Output amf3Output;
	
	public Amf3Session(AsynchronousSocketChannel client) {
		super(client, null, null);
		
		SerializationContext context = new SerializationContext();
		this.amf3Input = new Amf3Input(context);
		this.amf3Output = new Amf3Output(context);
	}
	
	public boolean init() {
		if(super.init()) {
			this.amf3Input.setInputStream(super.getInputStream());
			this.amf3Output.setOutputStream(super.getOutputStream());
			return true;
		}
		return false;
	}

	public void call(String funcName, Object...params) {
		RPC rpc = new RPC();
		
		rpc.setFunctionName(funcName);
		if (params.length > 0) {
			rpc.setParameters(params);
		}
		
		try {
			amf3Output.writeObject(rpc);
			amf3Output.flush();
		} catch (IOException e) {
			System.err.println(e.getMessage());
		}
	}
	
	@Override
	public void read() {
		RPCManager manager = Context.instance().get(RPCManager.class);
		ByteBufferInputStream input = super.getInputStream();
		
		while (true) {
			input.mark(0);
			try {
				Object o = amf3Input.readObject();
				RPC rpc = decode(o);
				
				if (rpc != null) {
					rpc.setSession(this);
					manager.add(rpc);
				}
				
				input.compact();
			} catch (EOFException e) {
				input.reset();
				break;
			} catch (ClassNotFoundException | IOException e) {
				System.err.println(e.getMessage());
				close();
				break;
			}
		}		
	}
	
	private RPC decode(Object o) {
		d(o);
		if (o instanceof ASObject) {
			ASObject aso = (ASObject)o;
			RPC rpc = new RPC();
			
			if (aso.containsKey("functionName")) {
				String functionName = (String)aso.get("functionName");
				rpc.setFunctionName(functionName);
			} else {
				return null;
			}
			
			if (aso.containsKey("params")) {
				Object[] params = (Object[])aso.get("params");
				rpc.setParameters(params);
			}
			
			return rpc;
		}
		return null;
	}
	
	private void d(Object o) {
		System.out.println(o);
		if (o instanceof ASObject) {
			ASObject aso = (ASObject)o;
			System.out.println(aso.getType());
		}
	}
}
