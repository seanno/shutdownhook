
// Sorted list of integers
//
// should compile without warnings using "javac *.java" and run without
// exception using "java SLL classname x y z ..." where classname is the
// name of a class that implements the SLL.Impl interface and x/y/z/etc.
// are positive (add) or negative (delete) integers.

public class SortedList
{
	// Implement this
	
	public interface Impl {
		public void insert(Integer data);
		public void delete(Integer data);
		public Integer[] asArray();
	}

	// Use these for testing

	static public void check(Impl actualImpl, Impl expectedImpl) throws Exception {

		Integer[] actual = actualImpl.asArray();
		Integer[] expected = expectedImpl.asArray();

		if (actual.length != expected.length) {
			throw new Exception("length mismatch");
		}

		for (int i = 0; i < expected.length; ++i) {
			if (actual[i] != expected[i]) {
				throw new Exception("data mismatch at position " + Integer.toString(i));
			}
		}
	}
	
	static public void verify(Impl impl, Integer... vals) throws Exception {

		Baseline baseline = new Baseline();

		for (int x : vals) {
			if (x >= 0) { impl.insert(x); baseline.insert(x); }
			else { impl.delete(x * -1); baseline.delete(x * -1); }
		}
		
		check(impl, baseline);
	}

	static public void print(Impl impl) throws Exception {

		boolean first = true;
		for (int x : impl.asArray()) {
			if (first) { first = false; } else { System.out.print(","); }
			System.out.print(Integer.toString(x));
		}

		System.out.println("");
	}

	// Entrypoint for testing cases from the cmdline
	
	static public void main(String[] args) throws Exception {

		Impl impl = (Impl) Class.forName(args[0])
			.getDeclaredConstructor()
			.newInstance();

		Integer[] vals = new Integer[args.length - 1];
		for (int i = 1; i < args.length; ++i) {
			vals[i-1] = Integer.parseInt(args[i]);
		}
		
		verify(impl, vals);

		print(impl);
		System.out.println("SUCCESS");
	}
}
