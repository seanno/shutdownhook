/*
** Read about this code at http://shutdownhook.com.
** No restrictions on use; no assurances or warranties either!
*/

import java.util.*;
import com.sun.jdi.*;

public abstract class BaseAction
{
	// +--------------+
	// | Implement Me |
	// +--------------+

	public abstract void whatever() throws Exception;
	
	// +--------+
	// | Use Me |
	// +--------+
	
	// findThreadById

	protected ThreadReference findThreadById(int threadId) {
		for (ThreadReference tr : vm.allThreads()) {
			if (tr.uniqueID() == threadId) return(tr);
		}

		return(null);
	}

	// renderThreadSummary
	// renderStackFrameSummary
	// renderSourceSummary
	// renderMethodSummary
	// (simple ones)

	protected static String renderThreadSummary(ThreadReference tr) {
		return(String.format("Thread %d (%s)", tr.uniqueID(), tr.name()));
	}

	protected static String renderStackSummary(ThreadReference tr, int baseIndents) {

		StringBuilder sb = new StringBuilder();
		
		try {
			for (StackFrame sf : tr.frames()) {
				if (sb.length() > 0) sb.append("\n");
				indent(sb, baseIndents);
				sb.append(renderStackFrameSummary(sf));
			}
		}
		catch (IncompatibleThreadStateException e) {
			if (sb.length() > 0) sb.append("\n");
			indent(sb, baseIndents);
			sb.append("Exception walking stack: " + e.toString());
		}
		
		return(sb.toString());
	}

	protected static String renderStackFrameSummary(StackFrame sf) {
		return(String.format("%s (%s)",
							 renderMethodSummary(sf.location().method()),
							 renderSourceSummary(sf.location())));
	}

	protected static String renderSourceSummary(Location loc) {
		String sourceName = "MissingSourceName";
		try { sourceName = loc.sourceName(); } catch (AbsentInformationException e) { }
		return(String.format("%s:%d", sourceName, loc.lineNumber()));
	}

	protected static String renderMethodSummary(Method method) {
		return(String.format("%s.%s", method.declaringType().name(), method.name()));
	}

	// renderStackFrameArgs
	// renderStackFrameVars

	protected String renderStackFrameArgs(StackFrame sf, int baseIndents, int maxObjectDepth) {

		Map<String,Value> valuesMap = new LinkedHashMap<String,Value>();

		try {
			int iarg = 0;
			for (Value arg : sf.getArgumentValues()) {
				valuesMap.put(Integer.toString(iarg++), arg);
			}
		}
		catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			indent(sb, baseIndents);
			sb.append("Exception reading args: " + e.toString());
			return(sb.toString());
		}

		return(renderValuesMap(valuesMap, "ARG", baseIndents, maxObjectDepth));
	}

	protected String renderStackFrameVars(StackFrame sf, int baseIndents, int maxObjectDepth) {

		Map<String,Value> valuesMap = new LinkedHashMap<String,Value>();

		try {
			Map<LocalVariable,Value> varsMap = sf.getValues(sf.visibleVariables());
			for (Map.Entry<LocalVariable,Value> var : varsMap.entrySet()) {
				valuesMap.put(var.getKey().name(), var.getValue());
			}
		}
		catch (Exception e) {
			StringBuilder sb = new StringBuilder();
			indent(sb, baseIndents);
			sb.append("Exception reading vars: " + e.toString());
			return(sb.toString());
		}
		
		return(renderValuesMap(valuesMap, "VAR", baseIndents, maxObjectDepth));
	}

	private String renderValuesMap(Map<String,Value> valuesMap, String label,
								   int baseIndents, int maxObjectDepth) {
		
		StringBuilder sb = new StringBuilder();
		
		try {
			for (Map.Entry<String,Value> val : valuesMap.entrySet()) {
				if (sb.length() > 0) sb.append("\n");
				renderValueString(label, val.getKey(), val.getValue(),
								  baseIndents, maxObjectDepth, sb);
			}
		}
		catch (Exception e) {
			if (sb.length() > 0) sb.append("\n");
			indent(sb, baseIndents);
			sb.append(String.format("Exception generating %s: %s", label, e.toString()));
		}

		return(sb.toString());
	}

	// renderValueString
	// Renders an object value including nested object fields, posssibly multi-line
	
	protected String renderValueString(String label, String name, Value val,
									   int baseIndents, int maxObjectDepth, StringBuilder sb) {

		renderValueStringHelper(label, name, val, baseIndents, baseIndents + maxObjectDepth, sb);
		return(sb.toString());
	}

	private static void renderValueStringHelper(String label, String name, Value val,
												int indents, int maxIndents,
												StringBuilder sb) {

		// main line, indented and possibly labelled
		indent(sb, indents);
		if (label != null) sb.append(label).append(" ");
		sb.append(name).append(": ").append(val == null ? "null" : val.toString());;

		if (indents >= maxIndents || val == null || !(val instanceof ObjectReference)) {
			return;
		}

		// this was an object reference; dig into it
		ObjectReference or = (ObjectReference) val;
		List<Field> flds = or.referenceType().allFields();
		
		for (Map.Entry<Field,Value> fld : or.getValues(flds).entrySet()) {
			sb.append("\n");
			renderValueStringHelper(null, fld.getKey().name(), fld.getValue(),
									indents + 1, maxIndents, sb);
		}
	}

	// +----------------+
	// | Implementation |
	// +----------------+

	public BaseAction(VirtualMachine vm, List<String> args) {
		this.vm = vm;
		this.args = args;
	}

	private static void indent(StringBuilder sb, int indent) {
		for (int i = 0; i < indent; ++i) sb.append("\t");
	}

	protected VirtualMachine vm;
	protected List<String> args;
	protected boolean suspend;
}
