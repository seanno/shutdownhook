/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.util.*;
import java.io.*;
import java.lang.*;
import java.text.*;
import com.sun.jdi.*;
import com.sun.jdi.connect.*;

public class JdbJr
{
	public static void main(String[] args) throws Exception {

		if (args.length < 3) {
			System.out.println("Usage: java JdbJr HOST PORT ACTION");
			System.out.println("ACTION classes = list all loaded classes");
			System.out.println("ACTION threads = list all running threads");
			System.out.println("ACTION thread ID = list thread ID details");
			System.out.println("ACTION watch CLASS [METHOD] = watch calls in CLASS or METHOD in CLASS");
			return;
		}

		VirtualMachine vm = getDebuggee(args[0], args[1]);

		Runtime.getRuntime().addShutdownHook(new Thread() {
				public void run() {
					vm.dispose();
				}
			});
		
		List<String> actionArgs = new ArrayList<String>();
		for (int i = 3; i < args.length; ++i) actionArgs.add(args[i]);
		
		BaseAction action = null;
		
		switch (args[2].toLowerCase()) {
		    case "classes": action = new ClassListAction(vm, actionArgs);    break;
		    case "threads": action = new ThreadListAction(vm, actionArgs);   break;
		    case "thread":  action = new ThreadDetailAction(vm, actionArgs); break;
		    case "watch":   action = new WatchMethodsAction(vm, actionArgs); break;
		    default: break;
		}

		if (action == null) {
			System.out.println("Action not recognized");
			return;
		}

		action.whatever();
	}

	private static VirtualMachine getDebuggee(String host, String port) throws Exception {

		VirtualMachineManager vmm = Bootstrap.virtualMachineManager();

		for (AttachingConnector connector : vmm.attachingConnectors()) {
			if (connector.transport().name().equals("dt_socket")) {

				Map<String,Connector.Argument> args = connector.defaultArguments();
				args.get("hostname").setValue(host);
				args.get("port").setValue(port);

				return(connector.attach(args));
			}
		}

		throw new Exception("dt_socket connector not found");
	}

}

