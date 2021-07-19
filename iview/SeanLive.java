
// No imports or jdk/external package references!

public class SeanLive implements SortedList.Impl
{
	public static class Node
	{
		public Node Next;
		public Integer Data;
	}

	private Node head;
	
	// insert a new node at the correct place in the list. Multiple
	// nodes may have the same data value (and should obviously be
	// a contiguous group in the list)
	
	public void insert(Integer data) {

		Node newGuy = new Node();
		newGuy.Data = data;
		
		Node lastLessThan = findLastLessThan(data);

		if (lastLessThan == null) {
			// insert at head
			newGuy.Next = head;
			head = newGuy;
		}
		else {
			// not the head
			newGuy.Next = lastLessThan.Next;
			lastLessThan.Next = newGuy;
		}
	}

	// remove all nodes with the given value. If none exist, noop.
	
	public void delete(Integer data) {

		Node last = null;
		Node walk = head;

		while (walk != null && walk.Data < data) {
			last = walk;
			walk = walk.Next;
		}

		while (walk != null && walk.Data == data) {
			if (last == null) {
				head = walk.Next;
			}
			else {
				last.Next = walk.Next;
			}

			walk = walk.Next;
		}
	}

	// return a representation of the list as an array. 
	
	public Integer[] asArray() {

		Integer[] vals = new Integer[length()];

		int i = 0;
		Node walk = head;

		while (walk != null) {
			vals[i++] = walk.Data;
			walk = walk.Next;
		}

		return(vals);
	}

	private Node findLastLessThan(int data) {
		Node last = null;
		Node walk = head;
		
		while (walk != null && walk.Data < data)  {
			last = walk;
			walk = walk.Next;
		}

		return(last);
	}
		
	private int length() {
		int len = 0;
		Node walk = head;
		
		while (walk != null) {
			++len;
			walk = walk.Next;
		}

		return(len);
	}

	// Add tests here. Use SortedList.verify(impl, ...) passing in an instance
	// of your class and an array of positive (add to list) and negative
	// (remove from list) integers. verify() will except on failure.

    public static void main(String[] args) throws Exception {

		/*
		SeanLive live = new SeanLive();
		live.insert(1);
		live.delete(1);
		SortedList.print(live);
		*/
		
		// simple example; final list should be 0,0
		SortedList.verify(new SeanLive(), 1, 2, 3, -3, -2, -1, 0, 0);

		// empty list
		SortedList.verify(new SeanLive());

		// adds
		SortedList.verify(new SeanLive(), 0);
		SortedList.verify(new SeanLive(), 0, 0);
		SortedList.verify(new SeanLive(), 0, 1, 0);
		SortedList.verify(new SeanLive(), 5, 4, 3, 2, 1);
		SortedList.verify(new SeanLive(), 1, 2, 3, 4, 5);
		SortedList.verify(new SeanLive(), 1, 5, 2, 3, 4);

		// deletes from front
		SortedList.verify(new SeanLive(), 1, -1);
		SortedList.verify(new SeanLive(), 1, 1, -1);
		SortedList.verify(new SeanLive(), 1, 2, -1);

		// deletes from end
		// *** COMMENT ADDED AFTER RECORDING ... note the below tests
		// are wrong! I meant to write them but got distracted after
		// copying the "front" tests and when I got back I trusted
		// the comment to assume I was done ... comments lie! ***
		SortedList.verify(new SeanLive(), 1, -1);
		SortedList.verify(new SeanLive(), 1, 1, -1);
		SortedList.verify(new SeanLive(), 1, 2, -1);

		// deletes from middle
		SortedList.verify(new SeanLive(), 1, 2, 3, -2);
		SortedList.verify(new SeanLive(), 0, 0, 0, 5, 5, 5, 3, 3, -3);

		SortedList.verify(new SeanLive(), 1, 2, 2, 2, 3, 8, -2);
	}
}
