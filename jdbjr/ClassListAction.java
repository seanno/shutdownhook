/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

import java.util.*;
import com.sun.jdi.*;

public class ClassListAction extends BaseAction
{
	public ClassListAction(VirtualMachine vm, List<String> args) {
		super(vm, args);
	}

	public void whatever() throws Exception {
		for (ReferenceType rt : vm.allClasses()) {
			System.out.println(rt.name());
		}
	}
}
