
import java.lang.*;

public class ShutdownHook
{
	public static void main(String[] args) {

		Runtime.getRuntime().addShutdownHook(
		    new Thread(() -> pontificate() ));
		
		for (int age = 4; age < 21; ++age) school();
		for (int age = 21; age < 52; ++age) work();
		retire();
	}

	public static void school() { System.out.println("studying"); }
	public static void work() { System.out.println("working"); }
	public static void retire() { System.out.println("retiring"); }
	public static void pontificate() { System.out.println("you are here"); }
}
