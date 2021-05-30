/*
** Read about this code at http://shutdownhook.com
** MIT license details at https://github.com/seanno/shutdownhook/blob/main/LICENSE
*/

public enum LegacyGender
{
	M("Male"),
	F("Female"),
	O("Other"),
	U("Unknown");

	LegacyGender(String displayName) {
		this.displayName = displayName;
	}

	public String getDisplayName() {
		return(displayName);
	}

	public static LegacyGender find(String input) {

		for (LegacyGender lg : LegacyGender.values()) {
			if (lg.name().equalsIgnoreCase(input)) return(lg);
			if (lg.getDisplayName().equalsIgnoreCase(input)) return(lg);
		}

		return(null);
	}
	
	private String displayName;
}
