
// implementation using jdk built-ins to prove the test harness works. ;)

import java.util.ArrayList;
import java.util.Collections;

public class Baseline implements SortedList.Impl
{
	private ArrayList<Integer> list = new ArrayList<Integer>();
		
	// insert a new node at the correct place in the list. Multiple
	// nodes may have the same data value (and should obviously be
	// a contiguous group in the list)

	public void insert(Integer data) {
		list.add(data);
		Collections.sort(list);
	}

	// remove all nodes with the given value. If none exist, noop.

	public void delete(Integer data) {
		list.removeIf(d -> d == data);
	}

	// return a representation of the list as an array. 

	public Integer[] asArray() {
		return(list.toArray(new Integer[list.size()]));
	}
}
