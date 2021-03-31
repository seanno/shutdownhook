/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.text.*;
import java.util.*;
import com.sun.jdi.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

public class WatchMethodsAction extends BaseAction
{
	private final static int ARGS_DEPTH = 1;
	private final static int VARS_DEPTH = 1;

	public WatchMethodsAction(VirtualMachine vm, List<String> args) {
		super(vm, args);

		df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
	}

	public void whatever() {

		String classFilter = args.get(0);
		String methodName = (args.size() < 2 ? null : args.get(1));
		
		System.out.println(String.format("Waiting for events (class=%s, method=%s); ^C to exit...",
										 classFilter, methodName));

		MethodEntryRequest req = vm.eventRequestManager().createMethodEntryRequest();
		req.addClassFilter(classFilter);
		req.enable();

		EventSet eventSet = null;
		
		try {
			while ((eventSet = vm.eventQueue().remove()) != null) {
				for (Event event : eventSet) {
					if (event instanceof MethodEntryEvent) {

						MethodEntryEvent entryEvent = (MethodEntryEvent) event;
						ThreadReference tr = entryEvent.thread();
						Method method = entryEvent.method();
						
						if (methodName == null || methodName.equals(method.name())) {

							String hdr = String.format("===== %s\n      Method Entry: %s\n      %s",
													   df.format(new Date()),
													   method.name(),
													   renderThreadSummary(tr));
								
							System.out.println(hdr);
							System.out.println("");

							System.out.println(renderStackSummary(tr, 1));
							System.out.println("");

							try {
								StackFrame sf = tr.frame(0);

								String args = renderStackFrameArgs(sf, 1, ARGS_DEPTH);
								if (args.length() > 0) System.out.println(args + "\n");
							}
							catch (Exception e) {
								System.out.println("\tException rendering top frame details: " + e.toString());
							}
						}
					}
				}
					
				eventSet.resume();
				eventSet = null;
			}
		}
		catch (InterruptedException eInterrupt) {
			// we done
		}
		catch (Exception e) {
			System.out.println("Exception watching for events; exiting: " + e.toString());
		}
		finally {
			if (eventSet != null) eventSet.resume();
		}
	}

	private SimpleDateFormat df;
}
