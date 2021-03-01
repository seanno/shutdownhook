
import java.util.*;
import com.sun.jdi.*;

public class ThreadListAction extends BaseAction
{
	public ThreadListAction(VirtualMachine vm, List<String> args) {
		super(vm, args);
	}

	public void whatever() throws Exception {

		vm.suspend(); // auto-resumed on shutdownhook
		
		for (ThreadReference tr : vm.allThreads()) {

			System.out.println("----------------------------------------");
			System.out.println(renderThreadSummary(tr));
			System.out.println("");
			System.out.println(renderStackSummary(tr, 1));

			System.out.println("");
		}
	}
}
