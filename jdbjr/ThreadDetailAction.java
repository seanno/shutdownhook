/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.util.*;
import com.sun.jdi.*;

public class ThreadDetailAction extends BaseAction
{
	private final static int ARGS_DEPTH = 1;
	private final static int VARS_DEPTH = 1;
	
	public ThreadDetailAction(VirtualMachine vm, List<String> args) {
		super(vm, args);
	}

	public void whatever() throws Exception {

		vm.suspend(); // auto-resumed on shutdownhook

		int threadId = Integer.parseInt(args.get(0));
		ThreadReference tr = findThreadById(threadId);
		if (tr == null) {
			System.out.println(String.format("Can't find thread id: %d", threadId));
			return;
		}

		System.out.println(renderThreadSummary(tr));
		System.out.println("");

		for (StackFrame sf : tr.frames()) {

			System.out.println(renderStackFrameSummary(sf));
			System.out.println("");

			String args = renderStackFrameArgs(sf, 1, ARGS_DEPTH);
			if (args.length() > 0) System.out.println(args);
			
			String vars = renderStackFrameVars(sf, 1, VARS_DEPTH);
			if (vars.length() > 0) System.out.println(vars);

			System.out.println("");
		}
	}
}
