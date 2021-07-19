
// No imports or jdk/external package references!

public class Skeleton implements SortedList.Impl
{
	// insert a new node at the correct place in the list. Multiple
	// nodes may have the same data value (and should obviously be
	// a contiguous group in the list)
	
	public void insert(Integer data) {

		// +----------------+
		// | YOUR CODE HERE |
		// +----------------+
	}

	// remove all nodes with the given value. If none exist, noop.
	
	public void delete(Integer data) {

		// +----------------+
		// | YOUR CODE HERE |
		// +----------------+
	}

	// return a representation of the list as an array. 
	
	public Integer[] asArray() {

		// +----------------+
		// | YOUR CODE HERE |
		// +----------------+
		return(null);
	}

	// add tests here, use SortedList.verify(impl, ...) passing in an instance
	// of your class and an array of positive (add to list) and negative
	// (remove from list) integers. verify() will except on failure.

    public static void main(String[] args) throws Exception {

		// simple example; final list should be 0,0
		SortedList.verify(new Skeleton(), 1, 2, 3, -3, -2, -1, 0, 0);

		// +----------------+
		// | YOUR CODE HERE |
		// +----------------+
	}
}
